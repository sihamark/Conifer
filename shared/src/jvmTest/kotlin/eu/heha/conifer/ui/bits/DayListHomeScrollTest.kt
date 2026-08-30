package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.test.Test

/**
 * The way back from the past: a day list scrolled far into it is a long drag from today, so Esc,
 * "All days" and the key for today ask it to come back — a bumped
 * [BitsPaneState.scrollDaysHomeRequest], answered by [ScrollBackToTodayWhenAsked].
 *
 * The days it counted back to stay counted back; it is only the position that returns.
 */
@OptIn(ExperimentalTestApi::class)
class DayListHomeScrollTest {

    @Test
    fun theSidebarComesBackToTodayWhenItIsAsked() = runComposeUiTest {
        var scrollHomeRequest by mutableStateOf(0)
        val days = DayCount()
        setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                DaySidebar(
                    bitsByDate = emptyList(),
                    selectedDate = null,
                    currentDate = TODAY,
                    isTopBarVisible = false,
                    onClickDate = {},
                    onClickAllDays = {},
                    dayCount = days.count,
                    onLoadOlderDays = days::addPage,
                    scrollHomeRequest = scrollHomeRequest
                )
            }
        }

        // Far enough into the past that the sidebar has counted back another page or two to get
        // there, and today is well off the top of it.
        repeat(2) {
            onNode(hasScrollAction()).performScrollToIndex(days.count)
            waitForIdle()
        }
        onNodeWithText(ALL_DAYS).assertDoesNotExist()
        val daysCountedBack = days.count

        // Nothing else about the state moves, which is the point of the request being a request:
        // this is Esc pressed on a screen that has nothing selected to clear.
        scrollHomeRequest++
        waitForIdle()

        // The row above the days, so its being on screen is the list being back where it started.
        onNodeWithText(ALL_DAYS).assertIsDisplayed()
        // The days it reached are still there to scroll back down to.
        onNode(hasScrollAction()).performScrollToIndex(daysCountedBack - 1)
        waitForIdle()
        val oldestDay = LocalDate.fromEpochDays(TODAY.toEpochDays() - (daysCountedBack - 2))
        onNodeWithText(dayAndMonth(oldestDay)).assertExists()
    }

    @Test
    fun theDayStripComesBackToTodayWhenItIsAsked() = runComposeUiTest {
        var state by mutableStateOf(BitsPaneState(today = TODAY))
        val days = DayCount()
        setContent {
            BitsPane(
                state = state.copy(listedDayCount = days.count),
                actions = BitsPaneActions(onLoadOlderDays = days::addPage),
                layout = BitsLayout.Stacked,
                doesImeHideTopBar = false
            )
        }
        onNodeWithContentDescription("Show date picker").performClick()
        waitForIdle()

        repeat(2) {
            dayStrip().performScrollToIndex(days.count)
            waitForIdle()
        }
        onNodeWithText(dayAndMonth(TODAY)).assertDoesNotExist()

        state = state.copy(scrollDaysHomeRequest = state.scrollDaysHomeRequest + 1)
        waitForIdle()

        onNodeWithText(dayAndMonth(TODAY)).assertIsDisplayed()
    }

    @Test
    fun aDayLetGoOfWithoutAskingLeavesTheDaysWhereTheyAre() = runComposeUiTest {
        // Tapping the selected chip a second time drops the filter, and does it from a gesture
        // aimed at a day in view: the days around it are what the user is working among, so they
        // stay on screen. Only the ways *out* of the past ask for today, and this is not one.
        var selectedDate by mutableStateOf<LocalDate?>(TODAY)
        val days = DayCount()
        setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                DaySidebar(
                    bitsByDate = emptyList(),
                    selectedDate = selectedDate,
                    currentDate = TODAY,
                    isTopBarVisible = false,
                    onClickDate = {},
                    onClickAllDays = {},
                    dayCount = days.count,
                    onLoadOlderDays = days::addPage,
                    scrollHomeRequest = 0
                )
            }
        }

        // Item 0 is "All days", so this scrolls the last day of the first page into view — and the
        // day it lands on is the one that has to still be there afterwards.
        onNode(hasScrollAction()).performScrollToIndex(DAY_LIST_PAGE)
        waitForIdle()
        val dayScrolledTo = LocalDate.fromEpochDays(TODAY.toEpochDays() - (DAY_LIST_PAGE - 1))
        onNodeWithText(dayAndMonth(dayScrolledTo)).assertIsDisplayed()

        selectedDate = null
        waitForIdle()

        onNodeWithText(dayAndMonth(dayScrolledTo)).assertIsDisplayed()
        // Had it gone home, this row above the days would be the thing on screen instead.
        onNodeWithText(ALL_DAYS).assertDoesNotExist()
    }

    /** The strip is the only thing that scrolls here: the pane is given no bits to list. */
    private fun ComposeUiTest.dayStrip() = onNode(hasScrollAction())

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
        const val ALL_DAYS = "All days"
    }
}
