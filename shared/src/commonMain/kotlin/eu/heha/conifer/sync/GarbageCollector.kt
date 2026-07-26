package eu.heha.conifer.sync

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.sync.GarbageCollector.Companion.TOMBSTONE_RETENTION
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Physically removes tombstones once they've been retained long enough for every device to have
 * had a chance to pull them (Nextcloud sync spec §8). Deliberately conservative: only considers
 * tombstones that are already clean (their deletion has actually reached the server, not just
 * this device) and older than [TOMBSTONE_RETENTION]; a device that stays offline longer than that
 * risks re-uploading a post GC'd elsewhere in the meantime ("resurrection") - [SyncEngine] guards
 * against the sharpest edge of that (see its `isReturningFromLongOffline` stopgap), but the full
 * mitigation the spec describes isn't implemented (see `sync_review.md` at the repo root).
 */
class GarbageCollector(
    private val remoteStore: RemoteStore,
    private val databaseController: DatabaseController,
) {
    private fun dao() = databaseController.syncDao()

    suspend fun collect(bitsRoot: String) {
        val threshold = Clock.System.now() - TOMBSTONE_RETENTION
        for (tombstone in dao().eligibleTombstones(threshold)) {
            deleteTombstone(bitsRoot, tombstone)
        }
    }

    private suspend fun deleteTombstone(bitsRoot: String, tombstone: Bit) {
        try {
            remoteStore.delete("$bitsRoot/${tombstone.bucket}/${tombstone.id}.json")
            dao().delete(tombstone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Left in place - retried on the next GC pass instead of losing track of it.
            Napier.w(e) { "failed to garbage-collect tombstone ${tombstone.id}, retrying next time" }
        }
    }

    internal companion object {
        // Shared with SyncEngine's own "returning from long offline" check (spec §8's
        // resurrection-risk mitigation) - a device that's been dark longer than this may find
        // posts it still thinks are live already GC'd elsewhere.
        val TOMBSTONE_RETENTION = 90.days
    }
}
