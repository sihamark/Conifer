package eu.heha.conifer.ui.bits

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.now
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The calendar the composer's chip row opens — the way in, the day it opens on and what it hands
 * back. The month grid itself is Material's; which days it will offer and how its year grid is
 * bounded are [DayPickerRangeTest]'s.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerDayPickerTest {

    @Test
    fun theCalendarOpensOnTheDayTheBitWouldGetAndHandsItBack() = runComposeUiTest {
        val picked = mutableListOf<LocalDate>()
        setContent {
            ComposerPane(
                // A day months back, which is the case the calendar exists for: the day strip would
                // take a long drag to reach it.
                state = BitsPaneState(composerDate = aDayMonthsBack()),
                actions = BitsPaneActions(onPickDate = { picked += it })
            )
        }

        openCalendar()
        // Straight to "Set day", so what comes back is the day the calendar opened on and nothing
        // the test picked itself.
        onNodeWithText(SET_DAY).performClick()

        assertEquals(listOf(aDayMonthsBack()), picked)
        onNodeWithText(SET_DAY).assertDoesNotExist()
    }

    @Test
    fun cancellingLeavesTheDayAlone() = runComposeUiTest {
        val picked = mutableListOf<LocalDate>()
        setContent {
            ComposerPane(
                state = BitsPaneState(composerDate = aDayMonthsBack()),
                actions = BitsPaneActions(onPickDate = { picked += it })
            )
        }

        openCalendar()
        onNodeWithText("Cancel").performClick()

        assertTrue(picked.isEmpty(), "nothing is committed until Set day")
        onNodeWithText(SET_DAY).assertDoesNotExist()
    }

    @Test
    fun theSidebarOpensTheSameCalendar() = runComposeUiTest {
        // In the two-pane layout the days are the sidebar's, so it carries the way in as well —
        // both buttons open the one dialog [BitsPane] owns.
        setContent { ComposerPane(layout = BitsLayout.DaySidebar) }

        val buttons = onAllNodesWithContentDescription(CALENDAR)
        buttons.assertCountEquals(2)

        buttons[0].performClick()
        onNodeWithText(SET_DAY).assertExists()
        onNodeWithText("Cancel").performClick()

        buttons[1].performClick()
        onNodeWithText(SET_DAY).assertExists()
    }

    private fun ComposeUiTest.openCalendar() {
        onNodeWithContentDescription(CALENDAR).performClick()
        onNodeWithText(SET_DAY).assertExists()
    }

    private companion object {
        const val SET_DAY = "Set day"
        const val CALENDAR = "Pick a day from the calendar"

        /**
         * Any day far enough back that it is a calendar's business and not the strip's — counted
         * from the clock, because [BitsPaneState.today] is, and a day written down here would only
         * be in the past until it wasn't.
         */
        fun aDayMonthsBack(): LocalDate = LocalDate.fromEpochDays(now().date.toEpochDays() - 170)
    }
}

/** The pane in a layout of the test's choosing, so it doesn't depend on the host's window size. */
@Composable
private fun ComposerPane(
    state: BitsPaneState = BitsPaneState(),
    actions: BitsPaneActions = BitsPaneActions(),
    layout: BitsLayout = BitsLayout.Stacked
) {
    BitsPane(
        state = state.copy(
            bitsByDate = state.bitsByDate.ifEmpty {
                listOf(
                    DatedBits(
                        date = LocalDate.fromEpochDays(now().date.toEpochDays() - 26),
                        bits = emptyList()
                    )
                )
            }
        ),
        actions = actions,
        layout = layout,
        doesImeHideTopBar = false
    )
}
