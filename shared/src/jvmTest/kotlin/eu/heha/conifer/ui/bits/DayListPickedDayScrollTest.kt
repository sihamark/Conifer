package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.heha.conifer.ui.bits.DayListPickedDayScrollTest.Companion.DAYS_BACK
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.test.Test

/**
 * The way *into* the past, the pair of [DayListHomeScrollTest]: a day picked from the calendar can
 * be hundreds of days past the edge of a list parked at today, so the pick asks both day lists to
 * go there — a [BitsPaneState.scrollDaysToDateRequest], answered by [ScrollToDayWhenAsked].
 *
 * Which actions make that request is [eu.heha.conifer.ui.BitsViewModelDayListsHomeTest]'s; this is
 * the list's end of it, so the request is made here by hand, as that test's counterpart does with
 * the way home.
 */
@OptIn(ExperimentalTestApi::class)
class DayListPickedDayScrollTest {

    @Test
    fun theSidebarGoesToTheDayItIsAskedFor() = runComposeUiTest {
        var request by mutableStateOf<DayScrollRequest?>(null)
        setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                DaySidebar(
                    bitsByDate = emptyList(),
                    selectedDate = null,
                    currentDate = TODAY,
                    isTopBarVisible = false,
                    onClickDate = {},
                    onClickAllDays = {},
                    // As the view model leaves the lists after a pick: counting back far enough to
                    // hold the day, and still standing at today.
                    dayCount = GROWN_DAY_COUNT,
                    scrollToDateRequest = request
                )
            }
        }
        val day = TODAY.daysBack(DAYS_BACK)
        // Held in a list of 210 days, and nowhere near the screen — which is the whole difference
        // the request exists to close.
        onNodeWithText(dayAndMonth(day)).assertDoesNotExist()

        request = DayScrollRequest(day)
        waitForIdle()

        onNodeWithText(dayAndMonth(day)).assertIsDisplayed()
    }

    @Test
    fun theDayStripGoesToTheDayItIsAskedFor() = runComposeUiTest {
        var state by mutableStateOf(
            BitsPaneState(today = TODAY, listedDayCount = GROWN_DAY_COUNT)
        )
        setContent {
            BitsPane(
                state = state,
                actions = BitsPaneActions(),
                layout = BitsLayout.Stacked,
                doesImeHideTopBar = false
            )
        }
        onNodeWithContentDescription("Show date picker").performClick()
        waitForIdle()
        val day = TODAY.daysBack(DAYS_BACK)
        onNodeWithText(dayAndMonth(day)).assertDoesNotExist()

        state = state.copy(scrollDaysToDateRequest = DayScrollRequest(day))
        waitForIdle()

        onNodeWithText(dayAndMonth(day)).assertIsDisplayed()
    }

    @Test
    fun theSameDayAskedForAgainIsGoneToAgain() = runComposeUiTest {
        // The list is dragged away between the two asks in life; here it is sent home, which puts
        // it in the same place — a request equal to the last one would leave it there.
        var request by mutableStateOf<DayScrollRequest?>(null)
        var scrollHomeRequest by mutableStateOf(0)
        setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                DaySidebar(
                    bitsByDate = emptyList(),
                    selectedDate = null,
                    currentDate = TODAY,
                    isTopBarVisible = false,
                    onClickDate = {},
                    onClickAllDays = {},
                    dayCount = GROWN_DAY_COUNT,
                    scrollHomeRequest = scrollHomeRequest,
                    scrollToDateRequest = request
                )
            }
        }
        val day = TODAY.daysBack(DAYS_BACK)
        request = DayScrollRequest(day, 1)
        waitForIdle()
        onNodeWithText(dayAndMonth(day)).assertIsDisplayed()

        scrollHomeRequest++
        waitForIdle()
        onNodeWithText(ALL_DAYS).assertIsDisplayed()

        request = DayScrollRequest(day, 2)
        waitForIdle()

        onNodeWithText(dayAndMonth(day)).assertIsDisplayed()
    }

    @Test
    fun aDayTheListDoesNotHoldLeavesItWhereItIs() = runComposeUiTest {
        // The view model grows the lists with the same write that asks for the day, so this cannot
        // happen from the screen — and if it ever did, scrolling as far as the list goes would
        // point at the wrong day with every appearance of pointing at the right one.
        var request by mutableStateOf<DayScrollRequest?>(null)
        setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                DaySidebar(
                    bitsByDate = emptyList(),
                    selectedDate = null,
                    currentDate = TODAY,
                    isTopBarVisible = false,
                    onClickDate = {},
                    onClickAllDays = {},
                    dayCount = DAY_LIST_PAGE,
                    scrollToDateRequest = request
                )
            }
        }

        request = DayScrollRequest(TODAY.daysBack(DAYS_BACK))
        waitForIdle()

        // Still at the top, where the row above the days is.
        onNodeWithText(ALL_DAYS).assertIsDisplayed()
    }

    private fun LocalDate.daysBack(days: Int) = LocalDate.fromEpochDays(toEpochDays() - days)

    /** How the day lists spell a day, as [eu.heha.conifer.ui.IsoDateTimeFormats] does for a test. */
    private fun dayAndMonth(date: LocalDate) = "${date.day}.${date.month.number}"

    private companion object {
        val TODAY = LocalDate(2026, 8, 8)
        const val ALL_DAYS = "All days"

        /** Far enough back that no amount of scrolling is the way there. */
        const val DAYS_BACK = 200

        /** What the view model grows the lists to for a day [DAYS_BACK] days old. */
        const val GROWN_DAY_COUNT = 7 * DAY_LIST_PAGE
    }
}
