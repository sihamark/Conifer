package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.heha.conifer.DateTimeFormats
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.IsoDateTimeFormats
import eu.heha.conifer.ui.LocalDateTimeFormats
import eu.heha.conifer.ui.bits.DaySidebarTodayMarkerTest.Companion.UNCONSTRAINED
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The "Today" marker beside the current day in the [DaySidebar] has to leave its row a single line.
 *
 * It only just fits: the sidebar is a fixed width, and whatever the weekday column, the dots and
 * the count badge do not claim is all the date and this marker have between them. Text handed too
 * little room wraps rather than complain, and wrapped, this one put "Today" on a line of its own
 * and left the row twice as tall as every other, the date floating against the middle of it.
 *
 * Two things about the setup are load-bearing, and the test is worth little without either.
 * [WidestDateFormats], because the formats a test renders with by default spell today as `8.8`,
 * narrow enough to hide the problem entirely, where the real platforms spell out both fields —
 * `08. 08.` at the widest, as Hungarian spells it. And [ConiferTheme], because the app's text is
 * Lato and the default is not: measuring the wrong typeface would measure the wrong row.
 */
@OptIn(ExperimentalTestApi::class)
class DaySidebarTodayMarkerTest {

    @Test
    fun theTodayMarkerLeavesItsRowAsTallAsTheOthers() = runComposeUiTest {
        setContent { Sidebar() }
        waitForIdle()

        val todayRow = todayMarker().rowHeight()
        val plainRow = onNodeWithText("08. 07.", useUnmergedTree = true).rowHeight()
        assertEquals(plainRow, todayRow, "the row holding the Today marker is not a single line")
    }

    @Test
    fun theTodayMarkerIsNotCutShortToAchieveIt() = runComposeUiTest {
        setContent { Sidebar() }
        waitForIdle()

        // Staying on one line is a promise the marker could also keep by ellipsizing, which would
        // read as "Toda…" and be a defect of its own. So it is measured against the same word with
        // all the room it could want: equal widths mean the row had room for the whole of it.
        //
        // Measured, rather than asked of TextLayoutResult.hasVisualOverflow — that reports true
        // here even for the date, which sits well inside the room it was given.
        assertEquals(
            onNodeWithTag(UNCONSTRAINED).width(),
            todayMarker().width(),
            "the Today marker no longer fits beside the date and is being cut short"
        )
    }

    /** The marker in the sidebar row, as opposed to the [UNCONSTRAINED] yardstick beside it. */
    private fun ComposeUiTest.todayMarker() =
        onNode(hasText(TODAY) and !hasTestTag(UNCONSTRAINED), useUnmergedTree = true)

    @Composable
    private fun Sidebar() {
        val today = LocalDate(2026, 8, 8)
        ConiferTheme(isDarkTheme = true) {
            CompositionLocalProvider(LocalDateTimeFormats provides WidestDateFormats) {
                Row {
                    // Roomy, so that only the sidebar's own fixed width constrains it.
                    Box(Modifier.size(400.dp, 900.dp)) {
                        DaySidebar(
                            bitsByDate = (0..4).map { offset ->
                                val date = LocalDate.fromEpochDays(today.toEpochDays() - offset)
                                DatedBits(date, bitsOn(date))
                            },
                            selectedDate = null,
                            currentDate = today,
                            isTopBarVisible = true,
                            onClickDate = {},
                            onClickAllDays = {}
                        )
                    }
                    // The yardstick for the test above: the marker's word, same style, no
                    // constraint worth speaking of.
                    Text(
                        text = TODAY,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag(UNCONSTRAINED)
                    )
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.width(): Dp =
        getUnclippedBoundsInRoot().let { it.right - it.left }

    /** The height of the row a text sits in — the clickable surface wrapping it. */
    private fun SemanticsNodeInteraction.rowHeight(): Float {
        val bounds = fetchSemanticsNode().parent!!.boundsInRoot
        return bounds.bottom - bounds.top
    }

    /**
     * A day with bits spread over it, so the row carries the dots and a count badge — both take
     * width from the date and the marker, so a row without them would not be the row at issue.
     */
    private fun bitsOn(date: LocalDate) = (0 until 17).map { index ->
        Bit(
            text = "bit $index",
            date = LocalDateTime(date, LocalTime(8 + index % 12, 0)),
            createdAt = Instant.fromEpochMilliseconds(0)
        )
    }

    private companion object {
        const val TODAY = "Today"
        const val UNCONSTRAINED = "today-with-all-the-room-it-wants"
    }
}

/**
 * The widest day-and-month any locale asks for, rather than the `8.8` of [IsoDateTimeFormats] —
 * which is too narrow to press on the sidebar's row at all, and would let this test pass over a
 * broken layout.
 *
 * `08. 08.` is Hungarian, the longest of the fourteen spellings `JvmDateTimeFormats` produces
 * across the JDK's thousand-odd locales (the next are Croatian's `08. 08` and the `08/08` of some
 * three hundred). Pinning the worst case is what makes this test an answer about locales and not
 * just about the machine it ran on.
 */
private object WidestDateFormats : DateTimeFormats by IsoDateTimeFormats {
    override fun weekdayShort(date: LocalDate) =
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[date.dayOfWeek.ordinal]

    override fun dayAndMonth(date: LocalDate) =
        "${date.month.number.toString().padStart(2, '0')}. " +
                "${date.day.toString().padStart(2, '0')}."
}
