package eu.heha.conifer.sync

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.BucketState
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.model.database.ReadablePending
import eu.heha.conifer.prefs.SyncPrefs
import eu.heha.conifer.sync.SyncEngine.Companion.PULL_PARALLELISM
import io.github.aakira.napier.Napier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import eu.heha.conifer.AppJson as json

/**
 * Orchestrates one full sync run against [remoteStore]: fast-path check, pull, push, the
 * readable module, tombstone GC, and finalize (Nextcloud sync spec §5). A run is exclusive
 * against concurrent calls on the same instance.
 */
class SyncEngine(
    private val remoteStore: RemoteStore,
    private val databaseController: DatabaseController,
    private val syncPrefs: SyncPrefs,
) {
    private val mutex = Mutex()
    private val readableModule = ReadableModule(remoteStore, databaseController)
    private val garbageCollector = GarbageCollector(remoteStore, databaseController)

    // Buckets confirmed/created on the server during this instance's lifetime, so a normal sync
    // doesn't re-issue the same redundant MKCOLs every single run. RemoteStore.mkdirs() tolerates
    // an existing folder regardless, so forgetting this (e.g. after an app restart) is harmless,
    // just slightly wasteful.
    private val knownBuckets = mutableSetOf<String>()

    // Whether this instance has already confirmed/created dataRoot/meta/manifest.json this
    // session (spec §9 "First device / empty server"). Purely a request-saving cache: once
    // postsRoot exists remotely (from anyone's first push), pull() never hits the branch that
    // checks this again anyway, since manifest.json is written once and never touched afterwards.
    private var manifestConfirmed = false

    private fun dao() = databaseController.syncDao()

    suspend fun sync() = mutex.withLock {
        val appRoot = syncPrefs.appRoot()
        val postsRoot = "$appRoot/.sync/posts"

        if (fastPathApplies(postsRoot)) return@withLock

        pull(appRoot, postsRoot)
        push(postsRoot)
        readableModule.render(appRoot)
        collectGarbageIfDue(postsRoot)
        finalize(postsRoot)
    }

    private suspend fun fastPathApplies(postsRoot: String): Boolean {
        val remoteRootEtag = remoteStore.etag(postsRoot) ?: return false
        return remoteRootEtag == syncPrefs.rootEtag() &&
                !dao().hasDirtyBits() &&
                !dao().hasPendingReadableDays() &&
                !garbageCollectionIsDue()
    }

    // --- tombstone GC (spec §8) -------------------------------------------------------------

    private suspend fun collectGarbageIfDue(postsRoot: String) {
        if (!garbageCollectionIsDue()) return
        garbageCollector.collect(postsRoot)
        syncPrefs.setLastGcAt(Clock.System.now())
    }

    private suspend fun garbageCollectionIsDue(): Boolean {
        val lastGcAt = syncPrefs.lastGcAt() ?: return true
        return Clock.System.now() - lastGcAt >= GC_INTERVAL
    }

    // --- pull (spec §5 step 2) ---------------------------------------------------------------

    private suspend fun pull(appRoot: String, postsRoot: String) {
        val buckets = try {
            remoteStore.list(postsRoot)
        } catch (_: WebDavNotFoundException) {
            ensureManifestExists(appRoot, postsRoot) // spec §9 "First device / empty server"
            emptyList()
        }
        for (bucketEntry in buckets.filter { it.isDirectory }) {
            val bucket = bucketEntry.name
            if (bucketEntry.etag == dao().bucketEtag(bucket)) continue // unchanged since last sync

            val entries = remoteStore.list("$postsRoot/$bucket")
                .filter { !it.isDirectory && it.name.endsWith(".json") }
            pullEntries(postsRoot, bucket, entries)
            dao().upsertBucketState(BucketState(bucket, bucketEntry.etag))
        }
    }

    /** Downloads and merges [entries] with parallelism ≤ [PULL_PARALLELISM] (spec §4/§9). */
    private suspend fun pullEntries(
        postsRoot: String,
        bucket: String,
        entries: List<RemoteStore.Entry>,
    ) = coroutineScope {
        val permits = Semaphore(PULL_PARALLELISM)
        for (entry in entries) {
            launch { permits.withPermit { pullEntry(postsRoot, bucket, entry) } }
        }
    }

    /**
     * Marks a brand-new collection as Conifer's: `dataRoot/posts/`, `dataRoot/meta/`, and
     * `dataRoot/meta/manifest.json` with `{"schema":1}` (spec §9 "First device / empty server").
     * Tolerates another device winning the race to create the manifest first.
     */
    private suspend fun ensureManifestExists(appRoot: String, postsRoot: String) {
        if (manifestConfirmed) return
        mkdirsRecursive(postsRoot)
        val metaDir = "$appRoot/.sync/meta"
        mkdirsRecursive(metaDir)
        try {
            remoteStore.put(
                "$metaDir/manifest.json",
                MANIFEST_JSON.encodeToByteArray(),
                ifNoneMatchAll = true,
            )
        } catch (_: WebDavConflictException) {
            // Another device created it first - it exists now either way.
        }
        manifestConfirmed = true
    }

    private suspend fun pullEntry(postsRoot: String, bucket: String, entry: RemoteStore.Entry) {
        val id = entry.name.removeSuffix(".json")
        val local = dao().bit(id)
        if (local != null && local.remoteEtag == entry.etag) return // already up to date

        val rawJson = remoteStore.get("$postsRoot/$bucket/${entry.name}").decodeToString()
        val remote = parse(rawJson) ?: return
        mergeRemote(remote, entry.etag)
    }

    /** Merges [remote] into the local row and queues both its old and new day for re-rendering. */
    private suspend fun mergeRemote(remote: Bit, remoteEtag: String): Bit {
        val previousDay = dao().bit(remote.id)?.date?.date
        val merged = dao().mergeAndStore(remote, remoteEtag, MergePolicy::merged)
        markReadablePendingForDayChange(merged.date.date, previousDay)
        return merged
    }

    /**
     * Queues [bit]'s day for re-rendering and, if a local edit re-dated it since the last time
     * it was pushed, its previous day too - so that day's file stops listing it (spec §7.2).
     * [Bit.payload] is what makes the previous day recoverable here even though the edit itself
     * went through [eu.heha.conifer.model.database.BitDao], not this DAO: [pushNewSingle],
     * [pushModified] and [pushNew] all stamp it with the JSON they just pushed, so the *next*
     * push can diff against the day it recorded.
     */
    private suspend fun markReadablePendingForPush(bit: Bit) {
        val previousDay = bit.payload?.let(::parse)?.date?.date
        markReadablePendingForDayChange(bit.date.date, previousDay)
    }

    private suspend fun markReadablePendingForDayChange(
        currentDay: LocalDate,
        previousDay: LocalDate?,
    ) {
        dao().markReadablePending(ReadablePending(currentDay))
        if (previousDay != null && previousDay != currentDay) {
            dao().markReadablePending(ReadablePending(previousDay))
        }
    }

    private fun parse(rawJson: String): Bit? = try {
        val bitJson = json.decodeFromString<BitJson>(rawJson)
        bitJson.toBitOrNull(rawJson).also {
            if (it == null) {
                Napier.w { "post ${bitJson.id} has schema ${bitJson.schema}, newer than this app understands - leaving it alone" }
            }
        }
    } catch (e: SerializationException) {
        Napier.e(e) { "failed to parse a post's JSON, skipping it: $rawJson" }
        null
    }

    // --- push (spec §5 step 3) --------------------------------------------------------------

    private suspend fun push(postsRoot: String) {
        val new = dao().dirtyNew()
        val modified = dao().dirtyModified()
        if (new.isEmpty() && modified.isEmpty()) return

        ensureBucketsExist(
            postsRoot,
            (new.asSequence() + modified.asSequence()).mapTo(mutableSetOf()) { it.bucket })

        if (new.isNotEmpty()) pushNew(postsRoot, new)
        for (bit in modified) pushModified(postsRoot, bit)
    }

    private suspend fun ensureBucketsExist(postsRoot: String, buckets: Set<String>) {
        for (bucket in buckets) {
            if (bucket in knownBuckets) continue
            mkdirsRecursive("$postsRoot/$bucket")
            // Only recorded once mkdirs has actually succeeded - if it throws (e.g. a transient
            // network failure), the bucket must not be silently treated as ready on a later
            // retry within this same process.
            knownBuckets.add(bucket)
        }
    }

    private suspend fun mkdirsRecursive(path: String) {
        var current = ""
        for (segment in path.trim('/').split('/')) {
            current = if (current.isEmpty()) segment else "$current/$segment"
            remoteStore.mkdirs(current)
        }
    }

    private suspend fun pushNew(postsRoot: String, new: List<Bit>) {
        val bulkResult = runCatching {
            val files = new.map { bit ->
                RemoteStore.BulkFile(
                    path(postsRoot, bit),
                    encode(bit).encodeToByteArray(),
                    bit.modifiedAt.epochSeconds
                )
            }
            new.zip(remoteStore.bulkPut(files))
        }
        bulkResult.onSuccess { pushed ->
            for ((bit, etag) in pushed) {
                dao().setClean(bit.id, etag, encode(bit))
                markReadablePendingForPush(bit)
            }
        }.onFailure {
            // The batch (or KtorWebDavStore's own per-file fallback inside bulkPut) failed
            // partway; some files may already be on the server from this or an earlier attempt.
            // Retry item by item instead of losing that progress or leaving them dirty forever.
            for (bit in new) pushNewSingle(postsRoot, bit)
        }
    }

    private suspend fun pushNewSingle(postsRoot: String, bit: Bit) {
        val path = path(postsRoot, bit)
        try {
            val payload = encode(bit)
            val etag = remoteStore.put(path, payload.encodeToByteArray(), ifNoneMatchAll = true)
            dao().setClean(bit.id, etag, payload)
            markReadablePendingForPush(bit)
        } catch (e: WebDavConflictException) {
            // Already on the server (a previous attempt's PUT went through but was never
            // recorded locally) - reconcile through the normal merge path instead of assuming
            // our copy is identical.
            val etag = remoteStore.etag(path) ?: throw e
            val remote = parse(remoteStore.get(path).decodeToString()) ?: return
            mergeRemote(remote, etag)
        }
    }

    private suspend fun pushModified(postsRoot: String, bit: Bit, attempt: Int = 1) {
        val path = path(postsRoot, bit)
        try {
            val payload = encode(bit)
            val etag = remoteStore.put(path, payload.encodeToByteArray(), ifMatch = bit.remoteEtag)
            dao().setClean(bit.id, etag, payload)
            markReadablePendingForPush(bit)
        } catch (_: WebDavConflictException) {
            if (attempt > MAX_PUSH_ATTEMPTS) {
                Napier.w { "giving up pushing bit ${bit.id} after $attempt conflicting attempts; it stays dirty for the next sync" }
                return
            }
            val currentEtag = remoteStore.etag(path)
            if (currentEtag == null) {
                // The file vanished between our failed PUT and this check - push it as new.
                pushNewSingle(postsRoot, bit.copy(remoteEtag = null))
                return
            }
            val remote = parse(remoteStore.get(path).decodeToString()) ?: return
            val merged = mergeRemote(remote, currentEtag)
            if (merged.dirty) pushModified(postsRoot, merged, attempt + 1)
        } catch (e: WebDavNotFoundException) {
            // The bucket folder went missing (shouldn't normally happen - buckets are never
            // deleted); recreate it once and retry.
            if (attempt > MAX_PUSH_ATTEMPTS) throw e
            mkdirsRecursive("$postsRoot/${bit.bucket}")
            pushModified(postsRoot, bit, attempt + 1)
        }
    }

    // --- finalize (spec §5 step 5) ----------------------------------------------------------

    private suspend fun finalize(postsRoot: String) {
        // Only null if nothing has ever been created anywhere (no remote data, nothing local to
        // push either) - there is genuinely nothing to record yet; the next sync that pushes
        // something will create postsRoot and finalize normally.
        val rootEtag = remoteStore.etag(postsRoot) ?: return
        syncPrefs.setRootEtag(rootEtag)
        syncPrefs.setLastSyncAt(Clock.System.now())
    }

    private fun path(postsRoot: String, bit: Bit) = "$postsRoot/${bit.bucket}/${bit.id}.json"

    /**
     * Serializes [bit] for the wire, merging our own fields into whatever [Bit.payload] already
     * holds (the last known server-side JSON) rather than rebuilding the object from scratch -
     * so a field a newer app version added, which this version doesn't understand, survives a
     * push made from here instead of being silently dropped (spec §3.1: "unknown fields must be
     * preserved on merge - take the JSON as a whole, don't rebuild it field by field"). A bit
     * that's never been synced has no payload yet, so there's nothing to preserve.
     */
    private fun encode(bit: Bit): String {
        val known = json.encodeToJsonElement(bit.toJson()).jsonObject
        val base = bit.payload?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        }
        val merged = if (base == null) known else JsonObject(base + known)
        return json.encodeToString(JsonObject.serializer(), merged)
    }

    private companion object {
        const val MAX_PUSH_ATTEMPTS = 3
        const val PULL_PARALLELISM = 6
        const val MANIFEST_JSON = """{"schema":1}"""
        val GC_INTERVAL = 7.days
    }
}
