package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.heha.conifer.DateTimeFormats
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.IsoDateTimeFormats
import eu.heha.conifer.ui.LocalDateTimeFormats
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A weekday longer than the column it is given must not take the row with it.
 *
 * The sidebar sets the weekday column to a fixed width so the dates line up down the list, and asks
 * the platform for the locale's short weekday to put in it. For a good many locales that is not
 * short at all: `java.time` answers `sábado` for Portuguese as spoken in Portugal and `Jumamosi`
 * for Swahili, where English gets `Sat` and German `Sa.`. Both run past the column, and text with
 * nowhere left to go wraps — which would double the height of not one row but all thirty.
 *
 * So the rows are measured against a locale of the second kind. They must come out the height of
 * the rows in [DaySidebarTodayMarkerTest], which uses a short one.
 */
@OptIn(ExperimentalTestApi::class)
class DaySidebarLongWeekdayTest {

    @Test
    fun aWeekdayTooLongForItsColumnLeavesEveryRowASingleLine() = runComposeUiTest {
        setContent { Sidebar() }
        waitForIdle()

        // Every day of the week, so the test does not rest on which one happens to be the longest.
        val heights = (0..6)
            .map { onNodeWithText("0${8 - it}.", substring = true, useUnmergedTree = true) }
            .map { it.rowHeight() }
            .distinct()
        assertEquals(
            listOf(SINGLE_LINE_ROW),
            heights,
            "a long weekday is wrapping and making its row taller than one line"
        )
    }

    @Composable
    private fun Sidebar() {
        val today = LocalDate(2026, 8, 8)
        ConiferTheme(isDarkTheme = true) {
            CompositionLocalProvider(LocalDateTimeFormats provides LongWeekdayFormats) {
                Box(Modifier.size(400.dp, 900.dp)) {
                    DaySidebar(
                        bitsByDate = (0..6).map { offset ->
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
            }
        }
    }

    /** The height of the row a text sits in — the clickable surface wrapping it. */
    private fun SemanticsNodeInteraction.rowHeight(): Float {
        val bounds = fetchSemanticsNode().parent!!.boundsInRoot
        return bounds.bottom - bounds.top
    }

    private fun bitsOn(date: LocalDate) = (0 until 17).map { index ->
        Bit(
            text = "bit $index",
            date = LocalDateTime(date, LocalTime(8 + index % 12, 0)),
            createdAt = Instant.fromEpochMilliseconds(0)
        )
    }

    private companion object {
        /**
         * What a row of one line comes to: 16.dp of text between 8.dp of padding either side, and
         * spelled out rather than read off a neighbouring row so that a change making every row two
         * lines at once cannot pass unnoticed.
         */
        const val SINGLE_LINE_ROW = 36f
    }
}

/**
 * Weekday names as the wordier locales give them — Portuguese as spoken in Portugal, whose "short"
 * weekday is the whole word. Swahili and a hundred-odd others are longer still; this is the widest
 * that a language in wide use asks for.
 */
private object LongWeekdayFormats : DateTimeFormats by IsoDateTimeFormats {
    override fun weekdayShort(date: LocalDate) = listOf(
        "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira",
        "sábado", "domingo"
    )[date.dayOfWeek.ordinal]

    override fun dayAndMonth(date: LocalDate) =
        "${date.day.toString().padStart(2, '0')}." +
                date.month.number.toString().padStart(2, '0')
}
