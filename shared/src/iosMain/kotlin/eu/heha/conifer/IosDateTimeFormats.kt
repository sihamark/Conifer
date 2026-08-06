package eu.heha.conifer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * iOS dates and times, out of `NSDateFormatter` and the current locale — which on iOS also means the
 * user's own region and their 24-hour setting, both of which the formatter reads for itself.
 *
 * The templated formats ("day and month", "the hour") are `setLocalizedDateFormatFromTemplate`, which
 * is the same ICU skeleton mechanism Android uses; the whole dates are the ready-made styles.
 *
 * The formatters are built once and kept: `NSDateFormatter` is famously expensive to create, and
 * these are called once per bit on screen.
 */
class IosDateTimeFormats(private val locale: NSLocale = NSLocale.currentLocale) : DateTimeFormats {

    private val timeFormat = templated("jm")
    private val dayAndMonthFormat = templated("Md")
    private val weekdayShortFormat = templated("EEE")
    private val dateFormat = styled(NSDateFormatterMediumStyle)
    private val dateWithWeekdayFormat = styled(NSDateFormatterFullStyle)

    override fun timeOfDay(time: LocalTime): String = timeFormat.stringFromDate(time.toNSDate())

    override fun weekdayShort(date: LocalDate): String =
        weekdayShortFormat.stringFromDate(date.toNSDate())

    override fun dayAndMonth(date: LocalDate): String =
        dayAndMonthFormat.stringFromDate(date.toNSDate())

    override fun date(date: LocalDate): String = dateFormat.stringFromDate(date.toNSDate())

    override fun dateWithWeekday(date: LocalDate): String =
        dateWithWeekdayFormat.stringFromDate(date.toNSDate())

    /**
     * Deliberately not written with `apply`: inside it `this` would be the formatter, which has a
     * `locale` of its own, so `setLocale(locale)` would hand it back the one it already had and this
     * class's own locale would be silently ignored — leaving every format on the system's locale and
     * the parameter above a lie. Spelled out, `locale` can only mean one thing.
     *
     * The order matters too, and only in this direction: the template is resolved against whatever
     * locale the formatter holds *at the time*, so setting it afterwards leaves the pattern belonging
     * to the wrong one.
     */
    private fun templated(template: String): NSDateFormatter {
        val formatter = NSDateFormatter()
        formatter.setLocale(locale)
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter
    }

    private fun styled(style: ULong): NSDateFormatter {
        val formatter = NSDateFormatter()
        formatter.setLocale(locale)
        formatter.setDateStyle(style)
        formatter.setTimeStyle(NSDateFormatterNoStyle)
        return formatter
    }

    /**
     * A day, as the calendar's own idea of a moment. The time of day is left at midnight and never
     * shown: only the date formatters see these, and a formatter with no time style ignores it.
     */
    private fun LocalDate.toNSDate(): NSDate = NSDateComponents().let { components ->
        components.setYear(year.toLong())
        components.setMonth(month.number.toLong())
        components.setDay(day.toLong())
        NSCalendar.currentCalendar.dateFromComponents(components) ?: NSDate()
    }

    /**
     * A time of day, hung off an arbitrary day for the same reason: the time formatters have no date
     * style, so which day it was is neither asked for nor shown.
     */
    private fun LocalTime.toNSDate(): NSDate = NSDateComponents().let { components ->
        components.setYear(REFERENCE_YEAR)
        components.setMonth(1)
        components.setDay(1)
        components.setHour(hour.toLong())
        components.setMinute(minute.toLong())
        NSCalendar.currentCalendar.dateFromComponents(components) ?: NSDate()
    }

    private companion object {
        const val REFERENCE_YEAR = 2000L
    }
}
