package eu.heha.conifer.ui.bits

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import eu.heha.conifer.JvmDateTimeFormats
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.LocalDateTimeFormats
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.util.Locale
import kotlin.test.Test

/**
 * That the platform's spellings actually reach the screen, and that the screen has none of its own
 * left: with an American formatter in scope, a bit written at 14:30 has to read half past two in the
 * afternoon wherever its time is shown.
 */
@OptIn(ExperimentalTestApi::class)
class LocalizedDateTimeTest {

    @Test
    fun theBitsOnScreenAreDatedAndTimedTheWayTheFormatterSpellsThem() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDateTimeFormats provides JvmDateTimeFormats(Locale.US)
            ) {
                BitsPane(
                    state = BitsPaneState(
                        today = LocalDate(2026, 8, 7),
                        // Pinned: the composer's chip shows the current time, so a test that let
                        // the wall clock in would assert different things before and after noon.
                        currentTime = MORNING,
                        bitsByDate = listOf(
                            DatedBits(
                                date = AUGUST_SIXTH,
                                bits = listOf(
                                    Bit(
                                        text = "an afternoon bit",
                                        date = LocalDateTime(2026, 8, 6, 14, 30)
                                    )
                                )
                            )
                        )
                    ),
                    layout = BitsLayout.DaySidebar,
                    doesImeHideTopBar = false
                )
            }
        }

        // The bit's own time, on its card. Matched loosely on purpose: the JDK's CLDR data puts a
        // narrow no-break space (U+202F) before PM, which no test should be spelling out.
        onNodeWithText("2:30", substring = true).assertExists()
        onNodeWithText("PM", substring = true).assertExists()
        // The sticky day header, which used to be the ISO date.
        onNodeWithText("Aug 6, 2026").assertExists()
        // The sidebar's row for that day: American order, and an English weekday. The weekday is
        // matched against the first of them, since 30 days of sidebar hold several Thursdays.
        onNodeWithText("8/6").assertExists()
        onAllNodesWithText("Thu").onFirst().assertExists()
    }

    @Test
    fun nothingOnScreenStillSpellsADateTheIsoWay() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDateTimeFormats provides JvmDateTimeFormats(Locale.US)
            ) {
                BitsPane(
                    state = BitsPaneState(
                        today = LocalDate(2026, 8, 7),
                        // Pinned: the composer's chip shows the current time, so a test that let
                        // the wall clock in would assert different things before and after noon.
                        currentTime = MORNING,
                        bitsByDate = listOf(
                            DatedBits(
                                date = AUGUST_SIXTH,
                                bits = listOf(
                                    Bit(text = "a bit", date = LocalDateTime(2026, 8, 6, 14, 30))
                                )
                            )
                        )
                    ),
                    layout = BitsLayout.DaySidebar,
                    doesImeHideTopBar = false
                )
            }
        }

        // The spelling the database uses must not be showing anywhere the reader can see it.
        onNodeWithText("2026-08-06").assertDoesNotExist()
        onNodeWithText("14:30").assertDoesNotExist()
    }

    private companion object {
        val AUGUST_SIXTH = LocalDate(2026, 8, 6)
        val MORNING = LocalTime(9, 15)
    }
}
