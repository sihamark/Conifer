package eu.heha.conifer.ui.bits

import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The arithmetic behind the calendar dialog: the day it hands Material and the day it reads back,
 * and how far its year grid reaches. Pulled out of the composable for the same reason
 * [shiftedByTimeSlots] and [BitsPaneState.dateShiftedBy] are — this is the part that can be wrong
 * without looking wrong.
 */
@OptIn(ExperimentalMaterial3Api::class)
class DayPickerRangeTest {

    @Test
    fun aDayGoesThroughMillisAndComesBackAsItself() {
        val days = listOf(
            LocalDate(2026, 8, 31),
            LocalDate(1970, 1, 1),
            LocalDate(2000, 2, 29),
            // Before the epoch, where the millis are negative — the case a truncating division
            // would land a day late on.
            LocalDate(1969, 12, 31),
            LocalDate(1900, 1, 1)
        )

        for (day in days) {
            assertEquals(day, dateOfUtcMillis(day.toUtcMillis()), "$day")
        }
    }

    @Test
    fun theEpochIsTheDayZeroMillisNames() {
        assertEquals(0L, LocalDate(1970, 1, 1).toUtcMillis())
        assertEquals(LocalDate(1970, 1, 2), dateOfUtcMillis(24 * 60 * 60 * 1000L))
    }

    @Test
    fun anyMomentInADayReadsAsThatDay() {
        // Material hands back UTC midnight, but the typed half has been known to carry the hours it
        // parsed; either way, the day is what is being picked.
        val midnight = LocalDate(2026, 3, 14).toUtcMillis()

        assertEquals(LocalDate(2026, 3, 14), dateOfUtcMillis(midnight))
        assertEquals(LocalDate(2026, 3, 14), dateOfUtcMillis(midnight + 23 * 60 * 60 * 1000L))
        assertEquals(LocalDate(2026, 3, 15), dateOfUtcMillis(midnight + 24 * 60 * 60 * 1000L))
    }

    @Test
    fun theYearGridEndsAtTheCurrentYearAndReachesPastTheOldestWriting() {
        val today = LocalDate(2026, 8, 31)

        val range = yearRangeBackTo(earliestDate = LocalDate(2019, 4, 2), today = today)

        assertEquals(2019..2026, range)
    }

    @Test
    fun withOnlyRecentWritingItStillReachesYearsBack() {
        // The case of a fresh install: there is nothing to look at further back, but backdating a
        // bit to a holiday two years ago is exactly what one opens a calendar for.
        val today = LocalDate(2026, 8, 31)

        val range = yearRangeBackTo(earliestDate = LocalDate(2026, 8, 1), today = today)

        assertEquals(2021..2026, range)
    }

    @Test
    fun withNoWritingAtAllItReachesJustAsFar() {
        val today = LocalDate(2026, 8, 31)

        assertEquals(2021..2026, yearRangeBackTo(earliestDate = null, today = today))
    }

    @Test
    fun daysUpToTodayAreTheSelectableOnes() {
        val today = LocalDate(2026, 8, 31)
        val selectable = DaysUpTo(today)

        assertTrue(selectable.isSelectableDate(today.toUtcMillis()), "today itself")
        assertTrue(selectable.isSelectableDate(LocalDate(2019, 4, 2).toUtcMillis()))
        assertFalse(selectable.isSelectableDate(LocalDate(2026, 9, 1).toUtcMillis()), "tomorrow")
        assertTrue(selectable.isSelectableYear(2026))
        assertFalse(selectable.isSelectableYear(2027))
    }
}
