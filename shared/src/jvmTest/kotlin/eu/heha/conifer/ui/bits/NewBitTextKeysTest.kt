package eu.heha.conifer.ui.bits

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ↑/↓ nudge as the user actually meets it: real key events into the focused text field.
 * [TimeSlotShiftTest] covers the arithmetic; this covers the wiring around it.
 */
@OptIn(ExperimentalTestApi::class)
class NewBitTextKeysTest {

    @Test
    fun upAndDownNudgeTheTimeWhileTheFieldIsFocused() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        setContent {
            NewBitText(
                newBitText = "lunch",
                isEditing = false,
                time = LocalTime(12, 0),
                onNewBitTextChange = {},
                onSelectTime = { picked += it },
                onClickAdd = {},
                focusRequester = FocusRequester()
            )
        }

        onNodeWithText("lunch").performClick()
        onNodeWithText("lunch").performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithText("lunch").performKeyInput { pressKey(Key.DirectionDown) }

        // One event each, not one per key-down *and* key-up.
        assertEquals(listOf(LocalTime(12, 15), LocalTime(11, 45)), picked)
    }

    @Test
    fun leftAndRightAreLeftToTheTextField() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        var text = "lunch"
        setContent {
            NewBitText(
                newBitText = text,
                isEditing = false,
                time = LocalTime(12, 0),
                onNewBitTextChange = { text = it },
                onSelectTime = { picked += it },
                onClickAdd = {},
                focusRequester = FocusRequester()
            )
        }

        onNodeWithText("lunch").performClick()
        onNodeWithText("lunch").performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithText("lunch").performKeyInput { pressKey(Key.DirectionRight) }

        assertEquals(emptyList(), picked, "←/→ must stay caret movement, not touch the time")
    }

    @Test
    fun theCaretStillMovesWithLeftSoTypingLandsWhereItShould() = runComposeUiTest {
        var text = "ab"
        setContent {
            NewBitText(
                newBitText = text,
                isEditing = false,
                time = LocalTime(12, 0),
                onNewBitTextChange = { text = it },
                onSelectTime = {},
                onClickAdd = {},
                focusRequester = FocusRequester()
            )
        }

        onNodeWithText("ab").performClick()
        // Cursor starts at the end; one ← puts it between a and b.
        onNodeWithText("ab").performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithText("ab").performTextInput("X")

        assertEquals("aXb", text)
    }
}
