package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Who is allowed to move the bit list, and who is not. */
@OptIn(ExperimentalTestApi::class)
class BitsListScrollTest {

    @Test
    fun aSyncRewritingBitsLeavesTheScrollPositionAlone() = runComposeUiTest {
        var bits by mutableStateOf(bitsByDate())
        setContent {
            Box(Modifier.size(VIEWPORT, VIEWPORT)) {
                BitsList(
                    state = BitsPaneState(bitsByDate = bits),
                    visibleBitsByDate = bits,
                    actions = BitsPaneActions(),
                    isTopBarVisible = true
                )
            }
        }

        // Scroll away from where the list anchors itself, to somewhere a reader might be.
        onNode(hasScrollAction(), useUnmergedTree = false).performScrollToNode(hasText("bit 3"))
        onNodeWithText("bit 3").assertIsDisplayed()

        // What a sync does: the visible text is untouched, but every row comes back with new sync
        // bookkeeping, so the list is a different value than before.
        bits = bitsByDate(synced = true)

        onNodeWithText("bit 3").assertIsDisplayed()
    }

    @Test
    fun changingTheDayFilterAnchorsToTheNewestBitAgain() = runComposeUiTest {
        var selected by mutableStateOf<LocalDate?>(null)
        val all = bitsByDate()
        setContent {
            val visible = selected?.let { day -> all.filter { it.date == day } } ?: all
            Box(Modifier.size(VIEWPORT, VIEWPORT)) {
                BitsList(
                    state = BitsPaneState(bitsByDate = all, filterDate = selected),
                    visibleBitsByDate = visible,
                    actions = BitsPaneActions(),
                    isTopBarVisible = true
                )
            }
        }

        onNode(hasScrollAction(), useUnmergedTree = false).performScrollToNode(hasText("bit 3"))
        selected = DAY

        // Picking a day re-anchors to that day's newest bit, which is the last one.
        onNodeWithText("bit 19").assertIsDisplayed()
    }

    /**
     * The bit that was just written lands directly below the last one in view, where the list counts
     * it as visible because a few of its pixels are — so it has to be scrolled to all the same.
     */
    @Test
    fun aNewBitPeekingOverTheBottomEdgeIsStillScrolledTo() = runComposeUiTest {
        var bits by mutableStateOf(bitsByDate())
        var scrollToBitId by mutableStateOf<String?>(null)
        setContent {
            Box(Modifier.size(VIEWPORT, VIEWPORT)) {
                BitsList(
                    state = BitsPaneState(bitsByDate = bits, scrollToBitId = scrollToBitId),
                    visibleBitsByDate = bits,
                    actions = BitsPaneActions(onScrolledToBit = { scrollToBitId = null }),
                    isTopBarVisible = true
                )
            }
        }

        // The list anchors to its newest bit, so the one written next starts off the bottom edge.
        onNodeWithText("bit 19").assertIsDisplayed()

        scrollToBitId = "bit-20"
        bits = bitsByDate(count = 21)

        onNodeWithText("bit 20").assertIsDisplayed()
        // Displayed is not enough: a bit peeking over the edge is displayed too, and the whole
        // point is that all of it is in view.
        val bottom = onNodeWithText("bit 20").getUnclippedBoundsInRoot().bottom
        assertTrue(bottom <= VIEWPORT, "bit 20 ends at $bottom, past the edge at $VIEWPORT")
    }

    private fun bitsByDate(synced: Boolean = false, count: Int = 20): List<DatedBits> = listOf(
        DatedBits(
            date = DAY,
            // Newest first, the way the DAO delivers them.
            bits = (count - 1 downTo 0).map { index ->
                Bit(
                    id = "bit-$index",
                    text = "bit $index",
                    createdAt = Instant.fromEpochSeconds(1_000L + index),
                    date = LocalDateTime(DAY, kotlinx.datetime.LocalTime(8, 0)),
                    // The fields a sync round rewrites; the reader sees none of them.
                    remoteEtag = if (synced) "\"etag-$index\"" else null,
                    dirty = !synced
                )
            }
        )
    )

    private companion object {
        val DAY = LocalDate(2026, 8, 3)

        /** Both edges of the square window the list is given. */
        val VIEWPORT = 400.dp
    }
}
