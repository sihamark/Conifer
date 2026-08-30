package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scrolling either day list into the past goes on: both ask for another page of days as they come
 * within sight of their oldest one, so there is no day the list stops at.
 *
 * What is under test is the asking — the growing itself is the view model's
 * ([eu.heha.conifer.ui.BitsViewModel.loadOlderDays]), and the [DayCount] here stands in for it,
 * counting back another [DAY_LIST_PAGE] days exactly as it does.
 */
@OptIn(ExperimentalTestApi::class)
class DayListPagingTest {

    @Test
    fun theSidebarAsksForOlderDaysAsItIsScrolledToItsOldest() = runComposeUiTest {
        val days = DayCount()
        setContent { Sidebar(days, height = 600.dp) }
        waitForIdle()

        assertEquals(DAY_LIST_PAGE, days.count, "a sidebar nobody scrolled counts back one page")

        // Item 0 is "All days", so this is the last day of the first page.
        onNode(hasScrollAction()).performScrollToIndex(DAY_LIST_PAGE)
        waitForIdle()

        assertEquals(2 * DAY_LIST_PAGE, days.count, "the sidebar did not ask for older days")
    }

    @Test
    fun theDaysItAsksForAreThere() = runComposeUiTest {
        val days = DayCount()
        setContent { Sidebar(days, height = 600.dp) }

        // Three times over, so that the days scrolled to below are ones no page the sidebar
        // started with holds: a list that grew once and then stopped reaches none of them.
        repeat(3) {
            onNode(hasScrollAction()).performScrollToIndex(days.count)
            waitForIdle()
        }

        // Item 0 is "All days", so this is a day of the third page — and its date has to be the
        // day that many days before today, not merely some day the sidebar felt like drawing.
        val itemIndex = 2 * DAY_LIST_PAGE + 5
        assertTrue(days.count > itemIndex, "only ${days.count} days were asked for")
        onNode(hasScrollAction()).performScrollToIndex(itemIndex)
        waitForIdle()

        val day = LocalDate.fromEpochDays(TODAY.toEpochDays() - (itemIndex - 1))
        onNodeWithText(dayAndMonth(day)).assertExists()
    }

    @Test
    fun aSidebarTallerThanAPageGoesOnAskingUntilItIsFilled() =
        // Two pages of days do not reach the bottom of a window this tall.
        runSkikoComposeUiTest(size = Size(400f, 2400f)) {
            // Nothing is scrolled here: the days simply do not reach the bottom of the window, and
            // a page of them still does not, so the answer to "are we near the oldest day" is yes
            // again as soon as each page arrives. Asking once would leave the sidebar half empty
            // on a tall screen until the user scrolled a list that has nowhere to scroll to.
            val days = DayCount()
            setContent { Sidebar(days, height = 2400.dp) }
            waitForIdle()

            assertTrue(
                days.count > 2 * DAY_LIST_PAGE,
                "the sidebar stopped asking at ${days.count} days, short of its own bottom"
            )
            // And it stops once they run past the bottom, rather than counting back for ever.
            assertTrue(
                days.count < 10 * DAY_LIST_PAGE,
                "the sidebar kept asking: ${days.count} days"
            )
        }

    @Test
    fun theDayStripAsksForOlderDaysAsItIsScrolledIntoThePast() = runComposeUiTest {
        val days = DayCount()
        setContent {
            BitsPane(
                state = BitsPaneState(listedDayCount = days.count, today = TODAY),
                actions = BitsPaneActions(onLoadOlderDays = days::addPage),
                layout = BitsLayout.Stacked,
                doesImeHideTopBar = false
            )
        }
        onNodeWithContentDescription("Show date picker").performClick()
        waitForIdle()

        assertEquals(DAY_LIST_PAGE, days.count, "a strip nobody scrolled counts back one page")

        // The strip's item 0 is the spacer at the today end, so this is again its oldest day.
        dayStrip().performScrollToIndex(DAY_LIST_PAGE)
        waitForIdle()

        assertEquals(2 * DAY_LIST_PAGE, days.count, "the day strip did not ask for older days")
    }

    /**
     * The strip rather than the bits list beside it: it is the scrollable holding today's chip,
     * and the pane is given no bits, so nothing else spells a day out at all.
     */
    private fun ComposeUiTest.dayStrip() =
        onNode(hasScrollAction() and hasAnyDescendant(hasText(dayAndMonth(TODAY))))

    @Composable
    private fun Sidebar(days: DayCount, height: Dp) {
        Box(Modifier.size(width = 400.dp, height = height)) {
            DaySidebar(
                bitsByDate = emptyList(),
                selectedDate = null,
                currentDate = TODAY,
                isTopBarVisible = false,
                onClickDate = {},
                onClickAllDays = {},
                dayCount = days.count,
                onLoadOlderDays = days::addPage
            )
        }
    }

    /** How the day lists spell a day, as [eu.heha.conifer.ui.IsoDateTimeFormats] does for a test. */
    private fun dayAndMonth(date: LocalDate) = "${date.day}.${date.month.number}"

    /** The view model's day count, as far as a day list can tell: a number that grows by pages. */
    private class DayCount {
        var count by mutableStateOf(DAY_LIST_PAGE)
            private set

        fun addPage() {
            count += DAY_LIST_PAGE
        }
    }

    private companion object {
        val TODAY = LocalDate(2026, 8, 8)
    }
}
