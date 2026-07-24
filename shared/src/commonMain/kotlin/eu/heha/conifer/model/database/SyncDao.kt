package eu.heha.conifer.model.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Queries the sync engine needs (Nextcloud sync spec §3.3). Only sync code uses this DAO; the
 * UI observes bits exclusively through [BitDao], which hides tombstones.
 */
@Dao
interface SyncDao {

    @Query("SELECT * FROM bits WHERE id = :id")
    suspend fun bit(id: String): Bit?

    /** Locally created bits that have never been pushed (uploaded via bulk PUT). */
    @Query("SELECT * FROM bits WHERE dirty = 1 AND remote_etag IS NULL")
    suspend fun dirtyNew(): List<Bit>

    /** Locally changed bits that already exist on the server (pushed with If-Match). */
    @Query("SELECT * FROM bits WHERE dirty = 1 AND remote_etag IS NOT NULL")
    suspend fun dirtyModified(): List<Bit>

    /** Fast-path check (sync spec §5 step 1): any unpushed local change at all. */
    @Query("SELECT EXISTS(SELECT 1 FROM bits WHERE dirty = 1)")
    suspend fun hasDirtyBits(): Boolean

    /**
     * The visible bits of a day in their deterministic rendering order (sync spec §7.3:
     * display order, then id as tiebreaker). The stored ISO date-time strings start with the
     * ISO day, so the comparison is a plain prefix check.
     */
    @Query(
        "SELECT * FROM bits WHERE deleted = 0 AND substr(date, 1, 10) = :day " +
                "ORDER BY date ASC, id ASC"
    )
    suspend fun bitsForDay(day: LocalDate): List<Bit>

    @Upsert
    suspend fun upsert(bit: Bit)

    @Delete
    suspend fun delete(bit: Bit)

    /**
     * Tombstones ready for physical deletion (sync spec §8): already pushed ([Bit.dirty] == 0, so
     * the deletion itself has actually reached the server and every other device has had a
     * chance to pull it) and older than the retention window.
     */
    @Query("SELECT * FROM bits WHERE deleted = 1 AND dirty = 0 AND modified_at < :threshold")
    suspend fun eligibleTombstones(threshold: Instant): List<Bit>

    /**
     * [payload] is stamped alongside the ETag so a later edit of the same bit can recover the
     * day it had at this push (see [eu.heha.conifer.sync.SyncEngine]'s re-dating handling) even
     * though [BitDao], which local edits go through, never touches sync bookkeeping.
     */
    @Query("UPDATE bits SET dirty = 0, remote_etag = :etag, payload = :payload WHERE id = :id")
    suspend fun setClean(id: String, etag: String, payload: String)

    /**
     * Atomically reads the current local row for [remote]'s id, resolves it against [remote]
     * via [merge] (in practice [eu.heha.conifer.sync.MergePolicy.merged]), and stores the
     * result - so a concurrent local edit can never interleave between the read and the write
     * (Nextcloud sync spec §6: "Run merge inside a Room transaction together with the
     * surrounding bookkeeping updates"). Takes the merge function as a parameter rather than
     * calling [eu.heha.conifer.sync.MergePolicy] directly so this DAO stays free of business
     * logic and [eu.heha.conifer.sync.MergePolicy] stays a plain, DB-free, unit-testable function.
     */
    @Transaction
    suspend fun mergeAndStore(
        remote: Bit,
        remoteEtag: String,
        merge: (local: Bit?, remote: Bit, remoteEtag: String) -> Bit,
    ): Bit {
        val merged = merge(bit(remote.id), remote, remoteEtag)
        upsert(merged)
        return merged
    }

    @Query("SELECT etag FROM bucket_state WHERE bucket = :bucket")
    suspend fun bucketEtag(bucket: String): String?

    @Upsert
    suspend fun upsertBucketState(state: BucketState)

    @Query("SELECT content_hash FROM readable_state WHERE day = :day")
    suspend fun readableContentHash(day: LocalDate): String?

    @Upsert
    suspend fun upsertReadableState(state: ReadableState)

    @Query("SELECT day FROM readable_pending ORDER BY day ASC")
    suspend fun pendingReadableDays(): List<LocalDate>

    @Query("SELECT EXISTS(SELECT 1 FROM readable_pending)")
    suspend fun hasPendingReadableDays(): Boolean

    @Upsert
    suspend fun markReadablePending(pending: ReadablePending)

    @Query("DELETE FROM readable_pending WHERE day = :day")
    suspend fun clearReadablePending(day: LocalDate)
}
