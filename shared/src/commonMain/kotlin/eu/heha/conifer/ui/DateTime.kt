package eu.heha.conifer.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun now() = Clock.System.now().dateTimeInDefaultTz()

fun Instant.dateTimeInDefaultTz() = this.toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDate.print() = LocalDate.Formats.ISO.format(this)
fun LocalTime.print() = LocalTime.Format {
    hour(); chars(":"); minute()
}.format(this)