package eu.heha.conifer

import android.icu.text.DateFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/**
 * Android dates and times, out of the ICU formatters the platform ships (`android.icu`) and the
 * default locale — which is the one the user picked in their system settings.
 *
 * ICU rather than `java.time`, which is what `JvmDateTimeFormats` uses, and not only for the
 * skeletons: mixing the two is a trap. Android's `getBestDateTimePattern` answers a skeleton with an
 * *ICU* pattern, and ICU has letters `java.time` has never heard of — `B`, the flexible day period,
 * turns up in the Chinese locales' idea of an hour — so handing one to `DateTimeFormatter.ofPattern`
 * throws, and throws while these fields are being initialised, which is to say at startup, on
 * somebody else's phone. Asking ICU to do the formatting as well as choose the pattern keeps the two
 * halves in the same dialect.
 *
 * [locale] is a constructor parameter for the same reason as on the other platforms: so that a locale
 * other than the device's can be asked for.
 */
class AndroidDateTimeFormats(private val locale: Locale = Locale.getDefault()) : DateTimeFormats {

    /** `j` is "the hour, however this locale writes hours", so this needs no 12/24 branch. */
    private val timeFormat = DateFormat.getInstanceForSkeleton(HOUR_AND_MINUTE, locale)
    private val dayAndMonthFormat = DateFormat.getInstanceForSkeleton(DAY_AND_MONTH, locale)
    private val weekdayShortFormat = DateFormat.getInstanceForSkeleton(SHORT_WEEKDAY, locale)
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    private val dateWithWeekdayFormat = DateFormat.getDateInstance(DateFormat.FULL, locale)

    override fun timeOfDay(time: LocalTime): String = timeFormat.format(time.toDate())

    override fun weekdayShort(date: LocalDate): String = weekdayShortFormat.format(date.toDate())

    override fun dayAndMonth(date: LocalDate): String = dayAndMonthFormat.format(date.toDate())

    override fun date(date: LocalDate): String = dateFormat.format(date.toDate())

    override fun dateWithWeekday(date: LocalDate): String =
        dateWithWeekdayFormat.format(date.toDate())

    /** Midnight on the day in question; the date formats have no time in them to show. */
    private fun LocalDate.toDate(): Date =
        GregorianCalendar(year, month.number - 1, day).time

    /** The time of day on a day nobody sees, for the same reason in reverse. */
    private fun LocalTime.toDate(): Date =
        GregorianCalendar(REFERENCE_YEAR, 0, 1, hour, minute).time

    private companion object {
        /** ICU skeletons: which fields are wanted, leaving their order and spelling to the locale. */
        const val HOUR_AND_MINUTE = "jm"
        const val DAY_AND_MONTH = "Md"
        const val SHORT_WEEKDAY = "EEE"

        /** Calendar months count from zero, which is what the arithmetic above is for. */
        const val REFERENCE_YEAR = 2000
    }
}
