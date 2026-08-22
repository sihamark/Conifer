package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The bits are capped at a readable width, so a wide window leaves an empty margin either side.
 * A drag started there has to scroll the list as well — which it only does because the list spans
 * the whole pane and carries the width limit as content padding (see `Bits` in [BitsPane]).
 *
 * The gesture goes through [onRoot] on purpose: injected on the list node instead, the
 * coordinates would be relative to that node and land on the bits whether or not the list spans
 * the margin, so the test would pass either way.
 */
@OptIn(ExperimentalTestApi::class)
class BitsListMarginScrollTest {

    @Test
    fun aDragInTheMarginBesideTheBitsStillScrollsTheList() = runComposeUiTest {
        setContent {
            // Far wider than the 720.dp cap on the bits, so there is a real margin to grab.
            Box(Modifier.size(1400.dp, 500.dp)) {
                BitsPane(
                    state = BitsPaneState(bitsByDate = bitsByDate()),
                    layout = BitsLayout.Stacked,
                    doesImeHideTopBar = false
                )
            }
        }

        // The list anchors to its newest bit, so that one is on screen to begin with.
        onNodeWithText("bit 39").assertIsDisplayed()

        onRoot().performTouchInput {
            // 3% in from the left edge — deep inside the margin, nowhere near the bits.
            val x = width * 0.03f
            repeat(6) {
                swipe(
                    start = Offset(x, centerY - height * 0.3f),
                    end = Offset(x, centerY + height * 0.3f)
                )
            }
        }

        // Swiping down walks towards the older bits, so the newest is gone from the viewport.
        onNodeWithText("bit 39").assertDoesNotExist()
    }

    private fun bitsByDate(): List<DatedBits> = listOf(
        DatedBits(
            date = DAY,
            // Newest first, the way the DAO delivers them.
            bits = (39 downTo 0).map { index ->
                Bit(
                    id = "bit-$index",
                    text = "bit $index",
                    createdAt = Instant.fromEpochSeconds(1_000L + index),
                    date = LocalDateTime(DAY, LocalTime(8, 0))
                )
            }
        )
    )

    private companion object {
        val DAY = LocalDate(2026, 8, 3)
    }
}
