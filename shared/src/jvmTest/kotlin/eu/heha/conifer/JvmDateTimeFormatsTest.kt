package eu.heha.conifer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The desktop spellings, asked for in two locales that disagree about everything that matters: the
 * hour cycle, the order of day and month, and the names of both.
 *
 * The exact strings `java.time` produces are the JDK's CLDR data and not this app's to promise, so
 * these check the parts that would be *wrong* rather than the whole rendering — an American time with
 * no AM in it, or a German date with the month before the day, is a bug either way.
 */
class JvmDateTimeFormatsTest {

    private val german = JvmDateTimeFormats(Locale.GERMANY)
    private val american = JvmDateTimeFormats(Locale.US)

    @Test
    fun theHourCycleFollowsTheLocale() {
        val afternoon = LocalTime(14, 30)

        assertEquals("14:30", german.timeOfDay(afternoon))
        val american = american.timeOfDay(afternoon)
        assertTrue("2:30" in american, "expected a 12-hour clock, got: $american")
        assertTrue(
            american.contains("PM", ignoreCase = true) || american.contains("p.m."),
            "expected an afternoon marker, got: $american"
        )
    }

    @Test
    fun middayAndMidnightSurviveTheTwelveHourClock() {
        // The two the 12-hour clock is famous for getting wrong.
        assertTrue("12:00" in american.timeOfDay(LocalTime(12, 0)))
        assertTrue("12:00" in american.timeOfDay(LocalTime(0, 0)))
        assertEquals("00:00", german.timeOfDay(LocalTime(0, 0)))
    }

    @Test
    fun dayAndMonthKeepTheLocalesOwnOrderAndSeparator() {
        val augustSixth = LocalDate(2026, 8, 6)

        // German writes the day first and separates with dots; American writes the month first.
        assertEquals("06.08", german.dayAndMonth(augustSixth))
        assertEquals("8/6", american.dayAndMonth(augustSixth))
    }

    @Test
    fun theYearIsGoneFromDayAndMonthWithoutLeavingItsSeparatorBehind() {
        val augustSixth = LocalDate(2026, 8, 6)

        listOf(
            Locale.GERMANY,
            Locale.US,
            Locale.JAPAN,
            Locale.FRANCE,
            Locale.KOREA
        ).forEach { locale ->
            val dayAndMonth = JvmDateTimeFormats(locale).dayAndMonth(augustSixth)

            assertFalse("2026" in dayAndMonth, "$locale kept the year: $dayAndMonth")
            assertFalse("26" in dayAndMonth, "$locale kept a two-digit year: $dayAndMonth")
            // Japanese and Korean write the year first, so a separator left behind shows up in
            // front. A trailing one is not the same bug: German and Korean both end a date with a
            // dot of their own accord ("06.08", "8. 6."), and taking that off would be the error.
            assertTrue(
                dayAndMonth.first().isDigit(),
                "$locale left a separator where the year had been: $dayAndMonth"
            )
        }
    }

    @Test
    fun weekdaysAndMonthsAreNamedInTheLocalesLanguage() {
        val thursday = LocalDate(2026, 8, 6)

        // The trailing dot is German's own abbreviation mark, and the JDK's CLDR data supplies it.
        assertEquals("Do.", german.weekdayShort(thursday))
        assertEquals("Thu", american.weekdayShort(thursday))
        assertTrue("Aug" in german.dateWithWeekday(thursday), german.dateWithWeekday(thursday))
        assertTrue(
            "Donnerstag" in german.dateWithWeekday(thursday),
            "the heading should name the day in full: ${german.dateWithWeekday(thursday)}"
        )
        assertTrue(
            "Thursday" in american.dateWithWeekday(thursday),
            american.dateWithWeekday(thursday)
        )
    }

    @Test
    fun aWholeDateIsShortButStillSaysWhichMonthItIs() {
        val thursday = LocalDate(2026, 8, 6)

        // Every locale's medium date carries the year, and none of them is the ISO spelling the
        // database uses — that is the whole reason this exists.
        listOf(german, american).forEach { formats ->
            val date = formats.date(thursday)
            assertTrue("2026" in date, date)
            assertFalse(date == "2026-08-06", "medium dates should not come out as ISO: $date")
        }
    }
}
