package eu.heha.conifer.model.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    /** Technical audit timestamp of the row; not shown in the UI. */
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Clock.System.now(),
    /**
     * The wall-clock date and time the bit is about, as the user entered it. Deliberately
     * zone-less so the displayed day and time never shift when the device's time zone changes.
     */
    @ColumnInfo(name = "date")
    val date: LocalDateTime = createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
)