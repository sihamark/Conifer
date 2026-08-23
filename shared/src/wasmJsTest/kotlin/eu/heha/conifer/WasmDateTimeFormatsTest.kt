package eu.heha.conifer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That the trip through `Intl.DateTimeFormat` comes back with something, and with the right fields in
 * it. The browser's locale is whatever the machine running the test is set to, so this checks shape
 * rather than spelling: a time with no date in it, a date with no time, a weekday that is a word.
 */
class WasmDateTimeFormatsTest {

    private val formats = WasmDateTimeFormats()
    private val date = LocalDate(2026, 8, 6)

    @Test
    fun theTimeOfDayIsATimeAndNothingElse() {
        val time = formats.timeOfDay(LocalTime(14, 30))

        assertTrue(time.any { it.isDigit() }, "expected a time, got: $time")
        assertTrue("30" in time, "expected the minutes, got: $time")
        // The reference year the time is hung off must not leak into the rendering.
        assertFalse("2000" in time, "the placeholder date leaked out: $time")
    }

    @Test
    fun aDateCarriesItsYearAndItsMonthButNoClock() {
        val whole = formats.date(date)

        assertTrue("2026" in whole, whole)
        assertFalse(":" in whole, "a date should have no time of day in it: $whole")
    }

    @Test
    fun dayAndMonthDropTheYear() {
        val dayAndMonth = formats.dayAndMonth(date)

        assertTrue("8" in dayAndMonth && "6" in dayAndMonth, dayAndMonth)
        assertFalse("2026" in dayAndMonth, "the year should be gone: $dayAndMonth")
    }

    @Test
    fun theWeekdayIsAWord() {
        val weekday = formats.weekdayShort(date)

        assertTrue(weekday.any { it.isLetter() }, "expected a name, got: $weekday")
        assertFalse(weekday.any { it.isDigit() }, "expected no numbers in it: $weekday")
    }

    @Test
    fun theHeadingNamesTheWeekdayAndTheMonth() {
        val heading = formats.dateWithWeekday(date)

        assertTrue("2026" in heading, heading)
        assertTrue(heading.count { it.isLetter() } > 4, "expected names spelled out, got: $heading")
    }
}
