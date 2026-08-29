package eu.heha.conifer.ui.bits

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import eu.heha.conifer.ui.DatedBits
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The time picker the composer's time chip opens — the way in, what it hands back, and which of its
 * two halves it opens on. The dial and the typed fields themselves are Material's.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerTimePickerTest {

    @Test
    fun theTimeChipOpensThePickerSeededWithTheTimeItShows() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        setContent {
            ComposerPane(
                // Off the slider's grid, which is the whole reason the picker is there: the time it
                // opens on has to be this one and not the quarter hour below it.
                state = BitsPaneState(composerTime = LocalTime(12, 7)),
                actions = BitsPaneActions(onSelectTime = { picked += it })
            )
        }

        openTimePicker()
        onNodeWithText(SET_TIME).performClick()

        assertEquals(listOf(LocalTime(12, 7)), picked)
        onNodeWithText(SET_TIME).assertDoesNotExist()
    }

    @Test
    fun cancellingLeavesTheTimeAlone() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        setContent {
            ComposerPane(
                state = BitsPaneState(composerTime = LocalTime(12, 7)),
                actions = BitsPaneActions(onSelectTime = { picked += it })
            )
        }

        openTimePicker()
        onNodeWithText("Cancel").performClick()

        assertTrue(picked.isEmpty(), "nothing is committed until Set time")
        onNodeWithText(SET_TIME).assertDoesNotExist()
    }

    @Test
    fun aKeyboardOpensStraightOntoTheTypedFields() = runComposeUiTest {
        setContent { ComposerPane(hasHardwareKeyboard = true) }

        openTimePicker()

        // The composer's own field, plus the picker's hour and minute — the dial has none.
        onAllNodes(hasSetTextAction()).assertCountEquals(3)
    }

    @Test
    fun withoutAKeyboardItOpensOnTheDialInstead() = runComposeUiTest {
        setContent { ComposerPane(hasHardwareKeyboard = false) }

        openTimePicker()

        onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    /** Expand the date/time chip, then press the time chip beside the slider. */
    private fun ComposeUiTest.openTimePicker() {
        onNodeWithContentDescription("Show date picker").performClick()
        onNodeWithContentDescription("Pick an exact time").performClick()
        onNodeWithText(SET_TIME).assertExists()
    }

    private companion object {
        const val SET_TIME = "Set time"
    }
}

/** The pane in its stacked (phone) layout, so the test doesn't depend on the host's window size. */
@Composable
private fun ComposerPane(
    state: BitsPaneState = BitsPaneState(),
    actions: BitsPaneActions = BitsPaneActions(),
    hasHardwareKeyboard: Boolean = false
) {
    BitsPane(
        state = state.copy(
            bitsByDate = state.bitsByDate.ifEmpty {
                listOf(DatedBits(date = LocalDate(2026, 8, 5), bits = emptyList()))
            }
        ),
        actions = actions,
        layout = BitsLayout.Stacked,
        doesImeHideTopBar = false,
        hasHardwareKeyboard = hasHardwareKeyboard
    )
}
