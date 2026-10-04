package eu.heha.conifer.ui.bits

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.pressKey
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

    @Test
    fun enterSetsTheDayAsTheButtonWould() = runComposeUiTest {
        // The calendar is the one control here a keyboard cannot really work: Material's grid is
        // walked with the Tab key and nothing else. Enter is what is left, and it means what it
        // means in the composer below — that one, then, and be done.
        val picked = mutableListOf<LocalDate>()
        setContent {
            ComposerPane(
                state = BitsPaneState(composerDate = aDayMonthsBack()),
                actions = BitsPaneActions(onPickDate = { picked += it })
            )
        }

        openCalendar()
        calendar().performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf(aDayMonthsBack()), picked)
        onNodeWithText(SET_DAY).assertDoesNotExist()
    }

    @Test
    fun enterFinishesTheTypedFieldThatHasTheCursor() = runComposeUiTest {
        // The half a keyboard opens the calendar on (DisplayMode.Input), where the day is written
        // out rather than pointed at — and where having to find a button afterwards was the whole
        // of what was annoying. The date is not retyped here: how it is spelled is the reader's
        // locale's business, and what is being held down is that a field with the cursor in it
        // does not swallow the key.
        val picked = mutableListOf<LocalDate>()
        setContent {
            ComposerPane(
                state = BitsPaneState(composerDate = aDayMonthsBack()),
                actions = BitsPaneActions(onPickDate = { picked += it }),
                isKeyboardPresent = true
            )
        }
        openCalendar()

        // The calendar opens with the cursor already in that field; the composer's own field is
        // the other one on the screen, which is why this asks for the focused one.
        dateField().assertExists()
        calendar().performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf(aDayMonthsBack()), picked)
        onNodeWithText(SET_DAY).assertDoesNotExist()
    }

    @Test
    fun enterOnAFieldWithNoDayInItYetLeavesTheCalendarOpen() = runComposeUiTest {
        // The same state the "Set day" button is disabled in: there is nothing to hand back, and a
        // key that closed the dialog on nothing would throw away the day it opened on.
        val picked = mutableListOf<LocalDate>()
        setContent {
            ComposerPane(
                state = BitsPaneState(composerDate = aDayMonthsBack()),
                actions = BitsPaneActions(onPickDate = { picked += it }),
                isKeyboardPresent = true
            )
        }
        openCalendar()

        dateField().performTextClearance()
        calendar().performKeyInput { pressKey(Key.Enter) }

        assertTrue(picked.isEmpty())
        onNodeWithText(SET_DAY).assertExists()
    }

    @Test
    fun escapeClosesTheCalendarAndNothingBehindIt() = runComposeUiTest {
        // The calendar takes the focus while its grid is up (see EnterSets), which is exactly the
        // arrangement that could have quietly handed Esc to the screen underneath instead — where
        // it means "every day and now", and would undo the day the calendar was opened to change.
        var reset = 0
        val picked = mutableListOf<LocalDate>()
        setContent {
            ComposerPane(
                state = BitsPaneState(composerDate = aDayMonthsBack()),
                actions = BitsPaneActions(
                    onPickDate = { picked += it },
                    onResetSelection = { reset++ }
                )
            )
        }

        openCalendar()
        calendar().performKeyInput { pressKey(Key.Escape) }

        onNodeWithText(SET_DAY).assertDoesNotExist()
        assertTrue(picked.isEmpty(), "Esc is a way out, not a way to pick")
        assertEquals(0, reset, "the calendar's Esc also reached the screen behind it")
    }

    /** The calendar's typed field, told from the composer's by having the cursor. */
    private fun ComposeUiTest.dateField() = onNode(hasSetTextAction() and isFocused())

    /** The dialog's own root, which is where a press inside it goes. */
    private fun ComposeUiTest.calendar() =
        onAllNodes(isRoot()).filterToOne(hasAnyDescendant(hasText(SET_DAY)))

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
    layout: BitsLayout = BitsLayout.Stacked,
    /** Which half the calendar opens on: the typed field where there is a keyboard, else the grid. */
    isKeyboardPresent: Boolean = false
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
        doesImeHideTopBar = false,
        hasHardwareKeyboard = isKeyboardPresent
    )
}
