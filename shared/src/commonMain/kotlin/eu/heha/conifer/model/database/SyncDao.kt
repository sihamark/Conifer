package eu.heha.conifer.model.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.datetime.LocalDate

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

    @Query("UPDATE bits SET dirty = 0, remote_etag = :etag WHERE id = :id")
    suspend fun setClean(id: String, etag: String)

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
