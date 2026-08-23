package eu.heha.conifer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_label_today
import eu.heha.conifer.DateTimeFormats
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

fun now() = Clock.System.now().dateTimeInDefaultTz()

fun Instant.dateTimeInDefaultTz() = this.toLocalDateTime(TimeZone.currentSystemDefault())

/**
 * The machine-readable spellings, for the few things that are written down or diagnosed rather than
 * read: [IsoDateTimeFormats], which is what previews and tests render with, and the sync debug rows.
 * Named for what they are, because everything a person actually reads goes through [DateTimeFormats]
 * instead, and a bare `print` in a UI package reads like the opposite of the truth.
 *
 * Deliberately not localized, and not to be: a bit written on a German phone and read back on an
 * American one has to come back the same date. What is stored is spelled this way everywhere — see
 * the database converters, `ReadableRenderer` for the sync files, and the log timestamps.
 */
internal fun LocalDate.printIso() = LocalDate.Formats.ISO.format(this)
internal fun LocalTime.printIso() = LocalTime.Format {
    hour(); chars(":"); minute()
}.format(this)

/**
 * How the screen spells dates and times, which is the platform's business: see [DateTimeFormats] and
 * the implementation each entry point hands to `ConiferApp.initialize`.
 *
 * Static, because it is decided once for the life of the app and nothing below it reacts to a change;
 * and defaulted to [IsoDateTimeFormats] so that a preview or a test is an ordinary composable, and
 * shows the same dates on whatever machine it runs on.
 */
val LocalDateTimeFormats = staticCompositionLocalOf<DateTimeFormats> { IsoDateTimeFormats }

/**
 * Dates and times with no locale in them at all: ISO dates, 24-hour times, English weekday names —
 * which is what this app showed everybody before the platforms were asked.
 *
 * Not a fallback the app itself ever uses, since every platform brings its own. It is here for
 * previews, tests and screenshots, where a date that reads differently on the next machine would be
 * a nuisance rather than a courtesy.
 */
object IsoDateTimeFormats : DateTimeFormats {
    override fun timeOfDay(time: LocalTime): String = time.printIso()

    override fun weekdayShort(date: LocalDate): String = date.dayOfWeek.name.take(3)

    override fun dayAndMonth(date: LocalDate): String = "${date.day}.${date.month.number}"

    override fun date(date: LocalDate): String = date.printIso()

    override fun dateWithWeekday(date: LocalDate): String =
        "${date.dayOfWeek.name}, ${date.day}.${date.month.number}.${date.year}"
}

/** A moment as the reader writes it: their date, then their time of day. */
fun DateTimeFormats.dateAndTimeOf(instant: Instant): String =
    instant.dateTimeInDefaultTz().let { "${date(it.date)} ${timeOfDay(it.time)}" }

/** "Today" for the current date, and the date as the reader writes it for any other. */
@Composable
internal fun LocalDate.label(today: LocalDate): String =
    if (this == today) {
        stringResource(Res.string.bits_label_today)
    } else {
        LocalDateTimeFormats.current.date(this)
    }
