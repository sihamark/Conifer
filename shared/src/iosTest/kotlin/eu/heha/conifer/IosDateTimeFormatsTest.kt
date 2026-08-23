package eu.heha.conifer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import platform.Foundation.NSLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iOS spellings, asked for in two locales that disagree about the hour cycle and the order of the
 * fields — and, just as importantly, that the dates handed to `NSDateFormatter` come back as the day
 * that went in, which is the part building an `NSDate` out of components could get wrong.
 */
class IosDateTimeFormatsTest {

    private val german = IosDateTimeFormats(NSLocale("de_DE"))
    private val american = IosDateTimeFormats(NSLocale("en_US"))
    private val thursday = LocalDate(2026, 8, 6)

    @Test
    fun theHourCycleFollowsTheLocale() {
        val afternoon = LocalTime(14, 30)

        assertEquals("14:30", german.timeOfDay(afternoon))
        val american = american.timeOfDay(afternoon)
        assertTrue("2:30" in american, "expected a 12-hour clock, got: $american")
        assertTrue("PM" in american.uppercase(), "expected an afternoon marker, got: $american")
    }

    @Test
    fun theDayThatGoesInIsTheDayThatComesOut() {
        // The date is assembled out of NSDateComponents, so an off-by-one here would be a calendar
        // or a time-zone mistake rather than a formatting one.
        assertTrue("6" in german.dayAndMonth(thursday), german.dayAndMonth(thursday))
        assertTrue("8" in german.dayAndMonth(thursday), german.dayAndMonth(thursday))
        assertTrue("2026" in german.date(thursday), german.date(thursday))
        // Foundation abbreviates without the dot the JDK's CLDR data uses; both are that data's call.
        assertEquals("Do", german.weekdayShort(thursday))
        assertEquals("Thu", american.weekdayShort(thursday))
    }

    @Test
    fun theFieldOrderIsTheLocalesOwn() {
        // German writes the day first, American the month.
        assertTrue(german.dayAndMonth(thursday).startsWith("6"), german.dayAndMonth(thursday))
        assertTrue(american.dayAndMonth(thursday).startsWith("8"), american.dayAndMonth(thursday))
    }

    @Test
    fun aTimeCarriesNoDateAndADateNoTime() {
        val time = german.timeOfDay(LocalTime(9, 5))
        assertFalse("2000" in time, "the placeholder date leaked out: $time")

        val date = german.date(thursday)
        assertFalse(":" in date, "a date should have no time of day in it: $date")
    }

    @Test
    fun theHeadingNamesTheWeekdayInFull() {
        assertTrue(
            "Donnerstag" in german.dateWithWeekday(thursday),
            german.dateWithWeekday(thursday)
        )
        assertTrue(
            "Thursday" in american.dateWithWeekday(thursday),
            american.dateWithWeekday(thursday)
        )
    }
}
