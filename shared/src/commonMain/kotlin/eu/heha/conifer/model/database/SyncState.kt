package eu.heha.conifer.model.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.datetime.LocalDate

/**
 * ETag of a `yyyy-MM` server bucket folder at the last successful sync. A bucket whose remote
 * ETag still matches is skipped entirely during pull.
 */
@Entity(tableName = "bucket_state")
data class BucketState(
    @PrimaryKey
    @ColumnInfo(name = "bucket")
    val bucket: String,
    @ColumnInfo(name = "etag")
    val etag: String
)

/**
 * Hash of the last uploaded human-readable Markdown rendering of a day. Uploads are skipped
 * when the freshly rendered content hashes to the same value.
 */
@Entity(tableName = "readable_state")
data class ReadableState(
    @PrimaryKey
    @ColumnInfo(name = "day")
    val day: LocalDate,
    @ColumnInfo(name = "content_hash")
    val contentHash: String
)

/** Days whose human-readable rendering is outstanding (retry queue across sync runs). */
@Entity(tableName = "readable_pending")
data class ReadablePending(
    @PrimaryKey
    @ColumnInfo(name = "day")
    val day: LocalDate
)
