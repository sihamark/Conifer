package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlin.time.Instant

/** Who is allowed to move the bit list, and who is not. */
@OptIn(ExperimentalTestApi::class)
class BitsListScrollTest {

    @Test
    fun aSyncRewritingBitsLeavesTheScrollPositionAlone() = runComposeUiTest {
        var bits by mutableStateOf(bitsByDate())
        setContent {
            Box(Modifier.size(400.dp, 400.dp)) {
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
            Box(Modifier.size(400.dp, 400.dp)) {
                BitsList(
                    state = BitsPaneState(bitsByDate = all, selectedDate = selected),
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

    private fun bitsByDate(synced: Boolean = false): List<DatedBits> = listOf(
        DatedBits(
            date = DAY,
            // Newest first, the way the DAO delivers them.
            bits = (19 downTo 0).map { index ->
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
    }
}
