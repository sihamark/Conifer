package eu.heha.conifer.ui

import androidx.compose.runtime.Composable
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_label_today
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

fun now() = Clock.System.now().dateTimeInDefaultTz()

fun Instant.dateTimeInDefaultTz() = this.toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDate.print() = LocalDate.Formats.ISO.format(this)
fun LocalTime.print() = LocalTime.Format {
    hour(); chars(":"); minute()
}.format(this)

/** "Today" for the current date, the plain ISO date for any other. */
@Composable
internal fun LocalDate.label(today: LocalDate): String =
    if (this == today) stringResource(Res.string.bits_label_today) else print()
