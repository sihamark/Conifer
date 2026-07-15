package eu.heha.conifer.model.database

import androidx.room3.ColumnTypeConverter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlin.time.Instant

object DatabaseConverters {
    @ColumnTypeConverter
    fun instantFromLong(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }

    @ColumnTypeConverter
    fun longFromInstant(value: Instant?): Long? = value?.toEpochMilliseconds()

    // Local date-times are stored as ISO-8601 strings ("2026-07-13T14:30:00"), which sort
    // lexicographically in chronological order, so they work in ORDER BY clauses.
    @ColumnTypeConverter
    fun localDateTimeFromString(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it, LocalDateTime.Formats.ISO) }

    @ColumnTypeConverter
    fun stringFromLocalDateTime(value: LocalDateTime?): String? =
        value?.format(LocalDateTime.Formats.ISO)
}