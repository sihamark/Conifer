package eu.heha.conifer.model.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "bits",
    indices = [Index("date"), Index("dirty")]
)
data class Bit(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = Uuid.random().toString(),
    @ColumnInfo(name = "text")
    val text: String,
    /**
     * Technical audit timestamp of the row; not shown in the UI. Immutable — it also determines
     * [bucket], the bit's fixed location on the sync server.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Clock.System.now(),
    /**
     * The wall-clock date and time the bit is about, as the user entered it. Deliberately
     * zone-less so the displayed day and time never shift when the device's time zone changes.
     * Its date part is also the sync spec's `day` (the fixed display-day string) and its time
     * part the fine-grained order within that day (`displayDate`).
     */
    @ColumnInfo(name = "date")
    val date: LocalDateTime = createdAt.toLocalDateTime(TimeZone.currentSystemDefault()),

    // Sync bookkeeping (Nextcloud sync spec §3.3). The UI never shows these fields.

    /** Time of the last modification; basis for last-write-wins merging between devices. */
    @ColumnInfo(name = "modified_at", defaultValue = "0")
    val modifiedAt: Instant = createdAt,
    /**
     * Id of the device that made the last modification; deterministic tiebreaker when two
     * devices modified a bit at the exact same [modifiedAt]. Empty until stamped on first edit.
     */
    @ColumnInfo(name = "modified_by", defaultValue = "''")
    val modifiedBy: String = "",
    /**
     * Tombstone flag: deleted bits that already exist on the server are kept as rows (and
     * files) so the deletion propagates to other devices; they are hidden from all UI queries.
     */
    @ColumnInfo(name = "deleted", defaultValue = "0")
    val deleted: Boolean = false,
    /**
     * The bit's full server-side JSON as last pulled; null until the bit has been on the
     * server. Kept as a whole so unknown fields written by newer app versions survive a merge.
     */
    @ColumnInfo(name = "payload")
    val payload: String? = null,
    /** Locally changed and not yet pushed to the server. */
    @ColumnInfo(name = "dirty", defaultValue = "1")
    val dirty: Boolean = true,
    /** ETag of the server file at the last pull/push; null = not on the server yet. */
    @ColumnInfo(name = "remote_etag")
    val remoteEtag: String? = null,
    /**
     * `yyyy-MM` month folder on the server, derived once from [createdAt] in UTC and never
     * changed afterwards, even when the bit is re-dated (sync spec invariant I1).
     */
    @ColumnInfo(name = "bucket", defaultValue = "''")
    val bucket: String = bucketOf(createdAt)
)

/** The fixed `yyyy-MM` server bucket for a bit created at [createdAt] (always UTC). */
fun bucketOf(createdAt: Instant): String =
    createdAt.toLocalDateTime(TimeZone.UTC).date.format(BUCKET_FORMAT)

private val BUCKET_FORMAT = LocalDate.Format {
    year(Padding.ZERO)
    char('-')
    monthNumber(Padding.ZERO)
}
