package eu.heha.conifer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number

/**
 * Web dates and times, out of the browser's own `Intl.DateTimeFormat` and the locale it reports —
 * which follows the browser's language settings, and with them the reader's habits about hour cycles
 * and field order.
 *
 * Undefined is passed for the locale everywhere rather than a name of our own choosing, which is how
 * `Intl` is told to use the browser's.
 */
class WasmDateTimeFormats : DateTimeFormats {

    override fun timeOfDay(time: LocalTime): String =
        // Only the hour and minute are asked for, so the hour cycle is the locale's to decide.
        formatDateTime(REFERENCE_YEAR, 1, 1, time.hour, time.minute, """{"timeStyle":"short"}""")

    override fun weekdayShort(date: LocalDate): String =
        format(date, """{"weekday":"short"}""")

    override fun dayAndMonth(date: LocalDate): String =
        format(date, """{"day":"numeric","month":"numeric"}""")

    override fun date(date: LocalDate): String =
        format(date, """{"dateStyle":"medium"}""")

    override fun dateWithWeekday(date: LocalDate): String =
        format(date, """{"dateStyle":"full"}""")

    private fun format(date: LocalDate, options: String): String =
        formatDateTime(date.year, date.month.number, date.day, 0, 0, options)

    private companion object {
        /** The day a bare time of day is hung off; the format never asks for the date part. */
        const val REFERENCE_YEAR = 2000
    }
}

/**
 * One trip into JavaScript for all of it: build the `Date` from its parts — months are counted from
 * zero there — and let `Intl` do the writing. The options arrive as JSON because a `js(...)` body can
 * only be handed values JavaScript already understands, and a string is the simplest of those.
 *
 * Surpress unused because IDE cannot read js statements
 */
@Suppress("unused")
@OptIn(ExperimentalWasmJsInterop::class)
private fun formatDateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    options: String
): String = js(
    "new Date(year, month - 1, day, hour, minute)" +
            ".toLocaleString(undefined, JSON.parse(options))"
)
