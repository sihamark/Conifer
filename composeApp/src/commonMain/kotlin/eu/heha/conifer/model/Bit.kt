package eu.heha.conifer.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
data class Bit(
    val id: String = Uuid.random().toString(),
    val text: String,
    val createdAt: Instant = Clock.System.now(),
    val explicitDate: LocalDate? = null,
    val explicitTime: LocalTime? = null
) {
    val date = explicitDate ?: createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val time = explicitTime ?: createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).time
}