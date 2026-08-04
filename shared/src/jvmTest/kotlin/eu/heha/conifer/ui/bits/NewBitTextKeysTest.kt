package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Alt+↑/↓ nudge as the user actually meets it: real key events into the focused text field.
 * [TimeSlotShiftTest] covers the arithmetic; this covers the wiring around it.
 */
@OptIn(ExperimentalTestApi::class)
class NewBitTextKeysTest {

    @Test
    fun altUpAndAltDownNudgeTheTimeWhileTheFieldIsFocused() = runComposeUiTest {
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
        onNodeWithText("lunch").performKeyInput {
            withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) }
        }
        onNodeWithText("lunch").performKeyInput {
            withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) }
        }

        // One event each, not one per key-down *and* key-up.
        assertEquals(listOf(LocalTime(12, 15), LocalTime(11, 45)), picked)
    }

    @Test
    fun bareUpAndDownStayOutOfTheTime() = runComposeUiTest {
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

        // Even where the field has no use for them — one line, nowhere for the caret to go — the
        // arrows mean the same as in a field that has: nothing to do with the time.
        assertEquals(emptyList(), picked, "only Alt+↑/↓ move the time")
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

    /**
     * The point of the modifier: in a bit that has grown past one line, the nudge still works
     * where the typing left off and the cursor stays there, ready for the rest of the sentence.
     */
    @Test
    fun altUpNudgesAWrappedBitWithoutDisturbingTheCursor() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        var text = WRAPPING_TEXT
        val focusRequester = FocusRequester()
        setContent {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            WrappingNewBitText(
                text = text,
                onNewBitTextChange = { text = it },
                onSelectTime = { picked += it },
                focusRequester = focusRequester
            )
        }

        // The cursor starts at the end of the text, i.e. on the last of several wrapped lines.
        onNodeWithText(WRAPPING_TEXT).performKeyInput {
            withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) }
        }
        onNodeWithText(WRAPPING_TEXT).performTextInput("!")

        assertEquals(listOf(LocalTime(12, 15)), picked)
        assertEquals("$WRAPPING_TEXT!", text, "typing must carry on where it left off")
    }

    @Test
    fun bareUpMovesTheCaretInAWrappedBit() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        var text = WRAPPING_TEXT
        val focusRequester = FocusRequester()
        setContent {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            WrappingNewBitText(
                text = text,
                onNewBitTextChange = { text = it },
                onSelectTime = { picked += it },
                focusRequester = focusRequester
            )
        }

        onNodeWithText(WRAPPING_TEXT).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithText(WRAPPING_TEXT).performTextInput("!")

        assertEquals(emptyList(), picked, "↑ is the caret's, wrapped or not")
        assertTrue(
            text.endsWith(WRAPPING_TEXT.takeLast(5)),
            "the caret should have left the last line, got: $text"
        )
    }

    /** A multi-line field narrow enough that [WRAPPING_TEXT] takes more than one line in it. */
    @Composable
    private fun WrappingNewBitText(
        text: String,
        onNewBitTextChange: (String) -> Unit = {},
        onSelectTime: (LocalTime) -> Unit = {},
        onClickAdd: () -> Unit = {},
        focusRequester: FocusRequester = remember { FocusRequester() }
    ) {
        Box(Modifier.width(240.dp)) {
            NewBitText(
                newBitText = text,
                isEditing = false,
                time = LocalTime(12, 0),
                onNewBitTextChange = onNewBitTextChange,
                onSelectTime = onSelectTime,
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
