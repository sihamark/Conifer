package eu.heha.conifer.model.database

import androidx.room3.ColumnTypeConverter
import kotlinx.datetime.LocalDate
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

    // Dates are stored as ISO-8601 strings ("2026-07-13"), matching the first ten characters of
    // the stored local date-times, so day lookups can compare against substr(date, 1, 10).
    @ColumnTypeConverter
    fun localDateFromString(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it, LocalDate.Formats.ISO) }

    @ColumnTypeConverter
    fun stringFromLocalDate(value: LocalDate?): String? =
        value?.format(LocalDate.Formats.ISO)
}