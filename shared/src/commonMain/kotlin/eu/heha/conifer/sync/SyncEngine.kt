package eu.heha.conifer.sync

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.BucketState
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.model.database.ReadablePending
import eu.heha.conifer.prefs.SyncPrefs
import eu.heha.conifer.sync.SyncEngine.Companion.PULL_PARALLELISM
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    // bitsRoot exists remotely (from anyone's first push), pull() never hits the branch that
    // checks this again anyway, since manifest.json is written once and never touched afterwards.
    private var manifestConfirmed = false

    private fun dao() = databaseController.syncDao()

    /** Runs one full sync round, returning tallies of what actually moved (for the debug UI). */
    suspend fun sync(): SyncStats = mutex.withLock {
        val counters = SyncCounters()
        val appRoot = syncPrefs.appRoot()
        val bitsRoot = "$appRoot/.sync/bits"

        if (fastPathApplies(bitsRoot)) {
            Napier.i { "fast path: remote unchanged, nothing dirty or pending - no requests made" }
            return@withLock SyncStats()
        }

        pull(appRoot, bitsRoot, counters)
        push(bitsRoot, counters)
        readableModule.render(appRoot)
        collectGarbageIfDue(bitsRoot)
        finalize(bitsRoot)
        counters.snapshot()
    }

    private suspend fun fastPathApplies(bitsRoot: String): Boolean {
        val remoteRootEtag = remoteStore.etag(bitsRoot) ?: return false
        return remoteRootEtag == syncPrefs.rootEtag() &&
                !dao().hasDirtyBits() &&
                !dao().hasPendingReadableDays() &&
                !garbageCollectionIsDue()
    }

    // --- tombstone GC (spec §8) -------------------------------------------------------------

    private suspend fun collectGarbageIfDue(bitsRoot: String) {
        if (!garbageCollectionIsDue()) return
        Napier.i { "tombstone GC is due, collecting" }
        garbageCollector.collect(bitsRoot)
        syncPrefs.setLastGcAt(Clock.System.now())
    }

    private suspend fun garbageCollectionIsDue(): Boolean {
        val lastGcAt = syncPrefs.lastGcAt() ?: return true
        return Clock.System.now() - lastGcAt >= GC_INTERVAL
    }

    /**
     * Whether this device is due for the spec §8 resurrection check: it's been long enough since
     * [SyncPrefs.lastSyncAt] that another device could have deleted-and-GC'd a post this device
     * still thinks is live (same window as [GarbageCollector.TOMBSTONE_RETENTION], since that's
     * the mechanism creating the risk). `null` (never synced before) can't have this problem -
     * nothing to resurrect yet.
     */
    private suspend fun isReturningFromLongOffline(): Boolean {
        val lastSyncAt = syncPrefs.lastSyncAt() ?: return false
        return Clock.System.now() - lastSyncAt >= GarbageCollector.TOMBSTONE_RETENTION
    }

    // --- pull (spec §5 step 2) ---------------------------------------------------------------

    private suspend fun pull(appRoot: String, bitsRoot: String, counters: SyncCounters) {
        val buckets = try {
            remoteStore.list(bitsRoot)
        } catch (_: WebDavNotFoundException) {
            ensureManifestExists(appRoot, bitsRoot) // spec §9 "First device / empty server"
            emptyList()
        }
        val bucketFolders = buckets.filter { it.isDirectory }
        var changedBuckets = 0
        for (bucketEntry in bucketFolders) {
            val bucket = bucketEntry.name
            if (bucketEntry.etag == dao().bucketEtag(bucket)) continue // unchanged since last sync

            changedBuckets++
            val entries = remoteStore.list("$bitsRoot/$bucket")
                .filter { !it.isDirectory && it.name.endsWith(".json") }
            Napier.d { "pull: bucket $bucket changed, ${entries.size} remote files" }
            pullEntries(bitsRoot, bucket, entries, counters)
            dao().upsertBucketState(BucketState(bucket, bucketEntry.etag))
        }
        Napier.i { "pull: $changedBuckets of ${bucketFolders.size} buckets changed" }
    }

    /** Downloads and merges [entries] with parallelism ≤ [PULL_PARALLELISM] (spec §4/§9). */
    private suspend fun pullEntries(
        bitsRoot: String,
        bucket: String,
        entries: List<RemoteStore.Entry>,
        counters: SyncCounters,
    ) = coroutineScope {
        val permits = Semaphore(PULL_PARALLELISM)
        entries.map { entry ->
            async { permits.withPermit { pullEntry(bitsRoot, bucket, entry, counters) } }
        }.awaitAll()
    }

    /**
     * Marks a brand-new collection as Conifer's: `dataRoot/bits/`, `dataRoot/meta/`, and
     * `dataRoot/meta/manifest.json` with `{"schema":1}` (spec §9 "First device / empty server").
     * Tolerates another device winning the race to create the manifest first.
     */
    private suspend fun ensureManifestExists(appRoot: String, bitsRoot: String) {
        if (manifestConfirmed) return
        mkdirsRecursive(bitsRoot)
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

    private suspend fun pullEntry(
        bitsRoot: String,
        bucket: String,
        entry: RemoteStore.Entry,
        counters: SyncCounters,
    ) {
        val id = entry.name.removeSuffix(".json")
        val local = dao().bit(id)
        if (local != null && local.remoteEtag == entry.etag) return // already up to date

        val rawJson = remoteStore.get("$bitsRoot/$bucket/${entry.name}").decodeToString()
        val remote = parse(rawJson) ?: return
        mergeRemote(remote, entry.etag, counters)
    }

    /**
     * Merges [remote] into the local row and queues both its old and new day for re-rendering.
     * Counts as "merged" (on top of "pulled") only when the local row had its own unpushed edit
     * to reconcile against - a clean/nonexistent local row is just an ordinary incoming update,
     * not a real conflict resolution (see [MergePolicy.winner]'s early-out branches).
     */
    private suspend fun mergeRemote(remote: Bit, remoteEtag: String, counters: SyncCounters): Bit {
        val local = dao().bit(remote.id)
        val merged = dao().mergeAndStore(remote, remoteEtag, MergePolicy::merged)
        markReadablePendingForDayChange(merged.date.date, local?.date?.date)
        counters.incrementPulled(wasMerge = local?.dirty == true)
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
        // Deliberately not logging rawJson: it is a bit's content, and the log file must stay
        // shareable (see eu.heha.conifer.log.redactSecrets and the debug popover's note).
        Napier.e(e) { "failed to parse a remote bit's JSON (${rawJson.length} bytes), skipping it" }
        null
    }

    // --- push (spec §5 step 3) --------------------------------------------------------------

    private suspend fun push(bitsRoot: String, counters: SyncCounters) {
        val new = dao().dirtyNew()
        val modified = dao().dirtyModified()
        if (new.isEmpty() && modified.isEmpty()) {
            Napier.d { "push: nothing dirty" }
            return
        }
        Napier.i { "push: ${new.size} new, ${modified.size} modified" }

        ensureBucketsExist(
            bitsRoot,
            (new.asSequence() + modified.asSequence()).mapTo(mutableSetOf()) { it.bucket })

        if (new.isNotEmpty()) pushNew(bitsRoot, new, counters)
        for (bit in modified) pushModified(bitsRoot, bit, counters)
    }

    private suspend fun ensureBucketsExist(bitsRoot: String, buckets: Set<String>) {
        for (bucket in buckets) {
            if (bucket in knownBuckets) continue
            mkdirsRecursive("$bitsRoot/$bucket")
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

    private suspend fun pushNew(bitsRoot: String, new: List<Bit>, counters: SyncCounters) {
        val bulkResult = runCatching {
            val files = new.map { bit ->
                RemoteStore.BulkFile(
                    path(bitsRoot, bit),
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
                counters.incrementPushed()
            }
        }.onFailure { e ->
            // The batch (or KtorWebDavStore's own per-file fallback inside bulkPut) failed
            // partway; some files may already be on the server from this or an earlier attempt.
            // Retry item by item instead of losing that progress or leaving them dirty forever.
            Napier.w(e) { "push: bulk upload of ${new.size} bits failed, retrying file by file" }
            for (bit in new) pushNewSingle(bitsRoot, bit, counters)
        }
    }

    private suspend fun pushNewSingle(bitsRoot: String, bit: Bit, counters: SyncCounters) {
        val path = path(bitsRoot, bit)
        try {
            val payload = encode(bit)
            val etag = remoteStore.put(path, payload.encodeToByteArray(), ifNoneMatchAll = true)
            dao().setClean(bit.id, etag, payload)
            markReadablePendingForPush(bit)
            counters.incrementPushed()
        } catch (e: WebDavConflictException) {
            // Already on the server (a previous attempt's PUT went through but was never
            // recorded locally) - reconcile through the normal merge path instead of assuming
            // our copy is identical.
            val etag = remoteStore.etag(path) ?: throw e
            val remote = parse(remoteStore.get(path).decodeToString()) ?: return
            mergeRemote(remote, etag, counters)
        }
    }

    private suspend fun pushModified(
        bitsRoot: String,
        bit: Bit,
        counters: SyncCounters,
        attempt: Int = 1,
    ) {
        val path = path(bitsRoot, bit)
        try {
            val payload = encode(bit)
            val etag = remoteStore.put(path, payload.encodeToByteArray(), ifMatch = bit.remoteEtag)
            dao().setClean(bit.id, etag, payload)
            markReadablePendingForPush(bit)
            counters.incrementPushed()
        } catch (_: WebDavConflictException) {
            if (attempt > MAX_PUSH_ATTEMPTS) {
                Napier.w { "giving up pushing bit ${bit.id} after $attempt conflicting attempts; it stays dirty for the next sync" }
                return
            }
            val currentEtag = remoteStore.etag(path)
            if (currentEtag == null) {
                if (isReturningFromLongOffline()) {
                    // TODO(spec §8 resurrection mitigation): this is only the stopgap half of
                    //  the spec's fix (see sync_review.md #1 at the repo root) - it avoids the
                    //  resurrection by refusing to act, not by resolving it. The full fix needs a
                    //  "flagged for manual confirmation" state on Bit (schema bump) and a UI
                    //  surface to actually resolve it; revisit once a settings/inbox screen
                    //  exists. Right now this edit just stays dirty forever with no way to clear
                    //  it, which is the honest trade-off of doing the safe half without the rest.
                    Napier.w {
                        "bit ${bit.id}'s remote file is gone after this device was offline for " +
                                "a long time - not resurrecting it; it stays dirty until this is " +
                                "resolved (see the TODO on pushModified)"
                    }
                    return
                }
                // The file vanished between our failed PUT and this check - push it as new.
                pushNewSingle(bitsRoot, bit.copy(remoteEtag = null), counters)
                return
            }
            val remote = parse(remoteStore.get(path).decodeToString()) ?: return
            val merged = mergeRemote(remote, currentEtag, counters)
            if (merged.dirty) pushModified(bitsRoot, merged, counters, attempt + 1)
        } catch (e: WebDavNotFoundException) {
            // The bucket folder went missing (shouldn't normally happen - buckets are never
            // deleted); recreate it once and retry.
            if (attempt > MAX_PUSH_ATTEMPTS) throw e
            mkdirsRecursive("$bitsRoot/${bit.bucket}")
            pushModified(bitsRoot, bit, counters, attempt + 1)
        }
    }

    // --- finalize (spec §5 step 5) ----------------------------------------------------------

    private suspend fun finalize(bitsRoot: String) {
        // Only null if nothing has ever been created anywhere (no remote data, nothing local to
        // push either) - there is genuinely nothing to record yet; the next sync that pushes
        // something will create bitsRoot and finalize normally.
        val rootEtag = remoteStore.etag(bitsRoot) ?: run {
            Napier.d { "finalize: $bitsRoot doesn't exist yet - nothing synced anywhere so far" }
            return
        }
        syncPrefs.setRootEtag(rootEtag)
        syncPrefs.setLastSyncAt(Clock.System.now())
    }

    private fun path(bitsRoot: String, bit: Bit) = "$bitsRoot/${bit.bucket}/${bit.id}.json"

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

/** Tallies from one [SyncEngine.sync] run, for the debug UI. */
data class SyncStats(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val merged: Int = 0,
)

/**
 * Mutable, concurrency-safe accumulator for [SyncStats] - guards plain [Int] fields with a
 * [Mutex] rather than reaching for a platform-specific atomic, since [SyncEngine.pullEntries]
 * increments it from several coroutines racing under [PULL_PARALLELISM].
 */
private class SyncCounters {
    private val mutex = Mutex()
    private var pushed = 0
    private var pulled = 0
    private var merged = 0

    suspend fun incrementPushed() = mutex.withLock { pushed++ }

    suspend fun incrementPulled(wasMerge: Boolean) = mutex.withLock {
        pulled++
        if (wasMerge) merged++
    }

    suspend fun snapshot() = mutex.withLock { SyncStats(pushed, pulled, merged) }
}
