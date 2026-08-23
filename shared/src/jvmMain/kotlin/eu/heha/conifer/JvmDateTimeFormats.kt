package eu.heha.conifer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Desktop dates and times, out of `java.time` and the JVM's default locale — which on every desktop
 * OS is the one the user chose in their system settings.
 *
 * [locale] is a constructor parameter only so that a test can ask for a locale other than the one the
 * test machine happens to be set to.
 */
class JvmDateTimeFormats(private val locale: Locale = Locale.getDefault()) : DateTimeFormats {

    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private val dateFormat =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val dateWithWeekdayFormat =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    /**
     * `java.time` has no way to ask for "day and month" the way ICU's skeletons do (which is what
     * `AndroidDateTimeFormats` uses), so it is taken off the locale's own short date instead: the
     * year is struck out of the pattern, and with it whatever separator was holding it on. That keeps
     * the locale's field order and its separators, which is the whole point of asking — `dd.MM` for
     * German, `M/d` for American English, `MM/dd` for Japanese, where the year comes first.
     */
    private val dayAndMonthFormat = DateTimeFormatter.ofPattern(
        DateTimeFormatterBuilder
            .getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
            .replace(YEAR_AND_ITS_SEPARATOR, ""),
        locale
    )

    override fun timeOfDay(time: LocalTime): String = timeFormat.format(time.toJavaLocalTime())

    override fun weekdayShort(date: LocalDate): String =
        date.toJavaLocalDate().dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    override fun dayAndMonth(date: LocalDate): String =
        dayAndMonthFormat.format(date.toJavaLocalDate())

    override fun date(date: LocalDate): String = dateFormat.format(date.toJavaLocalDate())

    override fun dateWithWeekday(date: LocalDate): String =
        dateWithWeekdayFormat.format(date.toJavaLocalDate())

    private companion object {
        /**
         * The year field of a date pattern (`y`, `yy`, `u`…) together with the punctuation and
         * spacing on either side of it. The separator goes with it, or `M/d/yy` would keep a trailing
         * slash and `yy/MM/dd` a leading one; the cost is that German comes out `06.08` rather than
         * `06.08.`, having lost the dot that was holding the year on.
         */
        val YEAR_AND_ITS_SEPARATOR = Regex("""[^\p{Alnum}]*[yu]+[^\p{Alnum}]*""")
    }
}
