package eu.heha.conifer.ui.bits

import eu.heha.conifer.ui.DatedBits
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The day arithmetic behind the Alt+←/→ hotkeys, as [TimeSlotShiftTest] covers the time nudge's.
 * BitsPaneShortcutsTest covers the keys themselves.
 */
class DayShiftTest {

    @Test
    fun aStepCountsFromTheDayBeingWrittenTo() {
        // Not from the filter: the first press on an unfiltered list should reach yesterday, not
        // spend itself arriving at today.
        assertEquals(LocalDate(2026, 8, 4), stateOn(TODAY).dateShiftedBy(-1))
        assertEquals(
            LocalDate(2026, 8, 2),
            stateOn(TODAY, composerDate = LocalDate(2026, 8, 3)).dateShiftedBy(-1)
        )
        assertEquals(
            LocalDate(2026, 8, 4),
            stateOn(TODAY, composerDate = LocalDate(2026, 8, 3)).dateShiftedBy(1)
        )
    }

    @Test
    fun stepsAcrossAMonthBoundaryLandOnTheRealDay() {
        assertEquals(LocalDate(2026, 7, 31), stateOn(LocalDate(2026, 8, 1)).dateShiftedBy(-1))
    }

    @Test
    fun theFutureIsOutOfReach() {
        // The strip stops at today, so a step forward from today has nowhere to go.
        assertEquals(TODAY, stateOn(TODAY).dateShiftedBy(1))
    }

    @Test
    fun withNothingWrittenAStepWalksTheFirstPageAndStaysOnIt() {
        val oldest = LocalDate.fromEpochDays(TODAY.toEpochDays() - (DAY_LIST_PAGE - 1))

        assertEquals(oldest, stateOn(TODAY, composerDate = oldest).dateShiftedBy(-1))
        // Holding the key down walks to that day and then stays on it.
        assertEquals(oldest, stateOn(TODAY, composerDate = oldest).dateShiftedBy(-40))
    }

    @Test
    fun aStepReachesAsFarBackAsThereIsWriting() {
        // The lists grow for as long as they are scrolled, so what bounds a held key is the
        // writing, not the page the lists happen to have counted back to.
        val firstBit = LocalDate.fromEpochDays(TODAY.toEpochDays() - 400)
        val state = stateOn(TODAY, datesWithBits = listOf(firstBit, LocalDate(2026, 8, 4)))

        assertEquals(firstBit, state.dateShiftedBy(-400))
        assertEquals(firstBit, state.dateShiftedBy(-500))
        // And no further: the days before the first bit are days with nothing to say about them.
        assertEquals(firstBit, state.copy(composerDate = firstBit).dateShiftedBy(-1))
    }

    @Test
    fun aSkipPassesOverTheEmptyDaysToTheWriting() {
        val state = stateOn(
            TODAY,
            datesWithBits = listOf(
                LocalDate(2026, 8, 5),
                LocalDate(2026, 7, 28),
                LocalDate(2026, 7, 20)
            )
        )

        assertEquals(LocalDate(2026, 7, 28), state.nearestDateWithBits(-1))
        assertEquals(
            LocalDate(2026, 7, 20),
            state.copy(composerDate = LocalDate(2026, 7, 28)).nearestDateWithBits(-1)
        )
        assertEquals(
            LocalDate(2026, 7, 28),
            state.copy(composerDate = LocalDate(2026, 7, 20)).nearestDateWithBits(1)
        )
    }

    @Test
    fun aSkipTakesTheNearestSuchDayAndNotTheFirstOneItIsHanded() {
        // Whatever order the days arrive in, the one next to the current day is the one to land on.
        val state = stateOn(
            TODAY,
            datesWithBits = listOf(
                LocalDate(2026, 7, 20),
                LocalDate(2026, 8, 4),
                LocalDate(2026, 7, 28)
            )
        )

        assertEquals(LocalDate(2026, 8, 4), state.nearestDateWithBits(-1))
    }

    @Test
    fun aSkipWithNowhereToGoStaysWhereItIs() {
        val state = stateOn(TODAY, datesWithBits = listOf(LocalDate(2026, 8, 5)))

        // Nothing older to reach, and nothing newer than today ever.
        assertNull(state.copy(composerDate = LocalDate(2026, 8, 5)).nearestDateWithBits(-1))
        assertNull(state.nearestDateWithBits(1))
    }

    @Test
    fun aSkipReachesADayOlderThanTheListsHaveCountedBackTo() {
        // The day it lands on has bits by definition, and the lists are grown to reach it, so
        // there is nothing for the skip itself to stop at.
        val longAgo = LocalDate.fromEpochDays(TODAY.toEpochDays() - (DAY_LIST_PAGE * 3L))

        assertEquals(
            longAgo,
            stateOn(TODAY, datesWithBits = listOf(longAgo)).nearestDateWithBits(-1)
        )
    }

    private fun stateOn(
        today: LocalDate,
        composerDate: LocalDate? = null,
        datesWithBits: List<LocalDate> = emptyList()
    ) = BitsPaneState(
        today = today,
        composerDate = composerDate,
        bitsByDate = datesWithBits.map { DatedBits(date = it, bits = emptyList()) }
    )

    private companion object {
        val TODAY = LocalDate(2026, 8, 5)
    }
}
