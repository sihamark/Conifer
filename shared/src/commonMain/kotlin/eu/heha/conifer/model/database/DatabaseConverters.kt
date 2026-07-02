package eu.heha.conifer.model.database

import androidx.room3.ColumnTypeConverter
import kotlin.time.Instant

object DatabaseConverters {
    @ColumnTypeConverter
    fun instantFromLong(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }

    @ColumnTypeConverter
    fun longFromInstant(value: Instant?): Long? = value?.toEpochMilliseconds()
}