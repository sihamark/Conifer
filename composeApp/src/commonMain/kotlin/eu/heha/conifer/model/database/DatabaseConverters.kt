package eu.heha.conifer.model.database

import androidx.room.TypeConverter
import kotlin.time.Instant

object DatabaseConverters {
    @TypeConverter
    fun instantFromLong(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun longFromInstant(value: Instant?): Long? = value?.toEpochMilliseconds()
}