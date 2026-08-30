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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the keyboard does to the list. The composer grows by the height of the IME and takes that
 * much off the list from below — and a LazyColumn holds its first visible item still, so without
 * help the bits at the bottom, which is where the newest ones are, go behind the keyboard.
 *
 * The list's height standing in for all of that: the list is given a box that shrinks, which is
 * exactly what the layout does to it when the composer grows (see `BitsAndComposer`).
 */
@OptIn(ExperimentalTestApi::class)
class BitsListKeyboardScrollTest {

    @Test
    fun theNewestBitStaysInViewWhenTheKeyboardTakesHalfTheList() = runComposeUiTest {
        var height by mutableStateOf(TALL)
        setContent { BitsListIn(height) }
        // The list anchors itself to the newest bit, which is where somebody about to type is.
        onNodeWithText("bit 19").assertIsDisplayed()

        height = SHORT

        onNodeWithText("bit 19").assertIsDisplayed()
        val bottom = onNodeWithText("bit 19").getUnclippedBoundsInRoot().bottom
        assertTrue(bottom <= SHORT, "bit 19 ends at $bottom, behind the keyboard below $SHORT")
    }

    /**
     * Not only the last bit: everything keeps its distance from the bottom edge, so a reader who was
     * looking at the second- or third-newest keeps looking at it rather than at what came before.
     */
    @Test
    fun everythingKeepsItsDistanceFromTheBottomEdge() = runComposeUiTest {
        var height by mutableStateOf(TALL)
        setContent { BitsListIn(height) }
        // A bit in the band the keyboard is about to cover, which is where the interesting ones are.
        val bit = bitEndingBetween(SHORT, TALL)
        val before = TALL - onNodeWithText(bit).getUnclippedBoundsInRoot().bottom

        height = SHORT

        val after = SHORT - onNodeWithText(bit).getUnclippedBoundsInRoot().bottom
        val moved = abs((before - after).value)
        assertTrue(moved < 1f, "$bit ended up ${moved}dp further from the bottom edge")
    }

    /** Putting the keyboard away hands the room back rather than leaving the list a screen on. */
    @Test
    fun theListGoesBackWhereItWasWhenTheKeyboardCloses() = runComposeUiTest {
        var height by mutableStateOf(TALL)
        setContent { BitsListIn(height) }
        onNode(hasScrollAction()).performScrollToNode(hasText("bit 4"))
        val bit = bitEndingBetween(SHORT, TALL)
        val before = onNodeWithText(bit).getUnclippedBoundsInRoot().top

        height = SHORT
        height = TALL

        val after = onNodeWithText(bit).getUnclippedBoundsInRoot().top
        val moved = abs((before - after).value)
        assertTrue(moved < 1f, "$bit ended up ${moved}dp from where it started")
    }

    /** The text of the bit whose bottom edge is currently between [from] and [to]. */
    private fun ComposeUiTest.bitEndingBetween(from: Dp, to: Dp): String =
        (0..19).map { "bit $it" }
            .first { text ->
                onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() &&
                        onNodeWithText(text).getUnclippedBoundsInRoot().bottom in from..to
            }

    @Composable
    private fun BitsListIn(height: Dp) {
        val bits = bitsByDate()
        Box(Modifier.size(WIDTH, height)) {
            BitsList(
                state = BitsPaneState(bitsByDate = bits),
                visibleBitsByDate = bits,
                actions = BitsPaneActions(),
                isTopBarVisible = true
            )
        }
    }

    private fun bitsByDate(count: Int = 20): List<DatedBits> = listOf(
        DatedBits(
            date = DAY,
            // Newest first, the way the DAO delivers them.
            bits = (count - 1 downTo 0).map { index ->
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
        val WIDTH = 400.dp

        /** The list with no keyboard over it, and with one taking half the window. */
        val TALL = 400.dp
        val SHORT = 200.dp
    }
}
