package eu.heha.conifer.model.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(tableName = "bits")
data class Bit(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = Uuid.random().toString(),
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Clock.System.now(),
    @ColumnInfo(name = "date", defaultValue = "0")
    val date: Instant = createdAt
)