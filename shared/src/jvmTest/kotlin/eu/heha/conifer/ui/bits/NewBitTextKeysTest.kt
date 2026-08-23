package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import eu.heha.conifer.ui.bits.NewBitTextKeysTest.Companion.WRAPPING_TEXT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the text field itself does with keys: submitting on Enter, and leaving the caret keys alone.
 * The shortcuts that adjust the date and time are the screen's and are covered by
 * [BitsPaneShortcutsTest]; what matters here is that the field keeps what it needs of the keyboard.
 */
@OptIn(ExperimentalTestApi::class)
class NewBitTextKeysTest {

    @Test
    fun theCaretStillMovesWithLeftSoTypingLandsWhereItShould() = runComposeUiTest {
        var text = "ab"
        setContent {
            NewBitText(
                newBitText = text,
                isEditing = false,
                onNewBitTextChange = { text = it },
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

    @Test
    fun enterSubmitsTheBitInsteadOfBreakingTheLine() = runComposeUiTest {
        var submits = 0
        var text = WRAPPING_TEXT
        setContent {
            WrappingNewBitText(
                text = text,
                onNewBitTextChange = { text = it },
                onClickAdd = { submits++ }
            )
        }

        onNodeWithText(WRAPPING_TEXT).performClick()
        onNodeWithText(WRAPPING_TEXT).performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, submits)
        assertEquals(WRAPPING_TEXT, text, "Enter must not leave a line break behind either")
    }

    @Test
    fun shiftEnterBreaksTheLineInsteadOfSubmitting() = runComposeUiTest {
        var submits = 0
        var text = WRAPPING_TEXT
        setContent {
            WrappingNewBitText(
                text = text,
                onNewBitTextChange = { text = it },
                onClickAdd = { submits++ }
            )
        }

        onNodeWithText(WRAPPING_TEXT).performClick()
        onNodeWithText(WRAPPING_TEXT).performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) }
        }

        assertEquals(0, submits)
        assertTrue(text.contains('\n'), "Shift+Enter should add a line break, got: $text")
    }

    @Test
    fun bareUpMovesTheCaretInAWrappedBit() = runComposeUiTest {
        var text = WRAPPING_TEXT
        setContent {
            WrappingNewBitText(text = text, onNewBitTextChange = { text = it })
        }

        // The cursor starts at the end of the text, i.e. on the last of several wrapped lines.
        onNodeWithText(WRAPPING_TEXT).performClick()
        onNodeWithText(WRAPPING_TEXT).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithText(WRAPPING_TEXT).performTextInput("!")

        assertTrue(
            text.endsWith(WRAPPING_TEXT.takeLast(5)),
            "↑ is the caret's, wrapped or not, so the text should not have grown at its end: $text"
        )
    }

    /** A multi-line field narrow enough that [WRAPPING_TEXT] takes more than one line in it. */
    @Composable
    private fun WrappingNewBitText(
        text: String,
        onNewBitTextChange: (String) -> Unit = {},
        onClickAdd: () -> Unit = {},
        focusRequester: FocusRequester = remember { FocusRequester() }
    ) {
        Box(Modifier.width(240.dp)) {
            NewBitText(
                newBitText = text,
                isEditing = false,
                onNewBitTextChange = onNewBitTextChange,
                onClickAdd = onClickAdd,
                focusRequester = focusRequester,
                maxLines = 3
            )
        }
    }

    private companion object {
        const val WRAPPING_TEXT = "a bit long enough to take more than one line in a narrow field"
    }
}
