package eu.heha.conifer.sync

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.BucketState
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.model.database.ReadablePending
import eu.heha.conifer.prefs.SyncPrefs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Orchestrates one full sync run against [remoteStore]: fast-path check, pull, push, finalize
 * (Nextcloud sync spec §5). A run is exclusive against concurrent calls on the same instance.
 *
 * The readable-module step (spec §7, stage ④) isn't implemented yet. This engine still marks
 * every touched day in `readable_pending` (old *and* new day on a re-date, per spec §7.2) so that
 * stage can start directly from an accurate backlog instead of having to re-derive it.
 */
class SyncEngine(
    private val remoteStore: RemoteStore,
    private val databaseController: DatabaseController,
    private val syncPrefs: SyncPrefs,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    // Buckets confirmed/created on the server during this instance's lifetime, so a normal sync
    // doesn't re-issue the same redundant MKCOLs every single run. RemoteStore.mkdirs() tolerates
    // an existing folder regardless, so forgetting this (e.g. after an app restart) is harmless,
    // just slightly wasteful.
    private val knownBuckets = mutableSetOf<String>()

    private fun dao() = databaseController.syncDao()

    suspend fun sync() = mutex.withLock {
        val postsRoot = "${syncPrefs.appRoot()}/.sync/posts"

        if (fastPathApplies(postsRoot)) return@withLock

        pull(postsRoot)
        push(postsRoot)
        finalize(postsRoot)
    }

    private suspend fun fastPathApplies(postsRoot: String): Boolean {
        val remoteRootEtag = remoteStore.etag(postsRoot) ?: return false
        return remoteRootEtag == syncPrefs.rootEtag() &&
                !dao().hasDirtyBits() &&
                !dao().hasPendingReadableDays()
    }

    // --- pull (spec §5 step 2) ---------------------------------------------------------------

    private suspend fun pull(postsRoot: String) {
        val buckets = try {
            remoteStore.list(postsRoot)
        } catch (_: WebDavNotFoundException) {
            emptyList() // no device has ever pushed anything yet
        }
        for (bucketEntry in buckets.filter { it.isDirectory }) {
            val bucket = bucketEntry.name
            if (bucketEntry.etag == dao().bucketEtag(bucket)) continue // unchanged since last sync

            val entries = remoteStore.list("$postsRoot/$bucket")
                .filter { !it.isDirectory && it.name.endsWith(".json") }
            for (entry in entries) {
                pullEntry(postsRoot, bucket, entry)
            }
            dao().upsertBucketState(BucketState(bucket, bucketEntry.etag))
        }
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
        dao().markReadablePending(ReadablePending(merged.date.date))
        if (previousDay != null && previousDay != merged.date.date) {
            dao().markReadablePending(ReadablePending(previousDay))
        }
        return merged
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
                    encode(bit),
                    bit.modifiedAt.epochSeconds
                )
            }
            new.zip(remoteStore.bulkPut(files))
        }
        bulkResult.onSuccess { pushed ->
            for ((bit, etag) in pushed) {
                dao().setClean(bit.id, etag)
                dao().markReadablePending(ReadablePending(bit.date.date))
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
            val etag = remoteStore.put(path, encode(bit), ifNoneMatchAll = true)
            dao().setClean(bit.id, etag)
            dao().markReadablePending(ReadablePending(bit.date.date))
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
            val etag = remoteStore.put(path, encode(bit), ifMatch = bit.remoteEtag)
            dao().setClean(bit.id, etag)
            dao().markReadablePending(ReadablePending(bit.date.date))
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

    private fun encode(bit: Bit): ByteArray = json.encodeToString(bit.toJson()).encodeToByteArray()

    private companion object {
        const val MAX_PUSH_ATTEMPTS = 3
    }
}
