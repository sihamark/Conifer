package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * The field is composed with the line budget the room left over affords it
 * (`currentComposerMaxLines`), and that budget can be a different one when the field comes back
 * from a saved state: rotating with the keyboard up is saved out of a window with one line's worth
 * of room and restored into one with two.
 */
@OptIn(ExperimentalTestApi::class)
class NewBitTextRestoreTest {

    @Test
    fun theFieldComesBackWhenItsLineBudgetGrewWhileItWasAway() =
        assertItSurvivesRestoringWith(before = 1, after = 2)

    @Test
    fun theFieldComesBackWhenItsLineBudgetShrankWhileItWasAway() =
        assertItSurvivesRestoringWith(before = 5, after = 1)

    /**
     * Composes the field with [before] lines, saves it, throws the composition away and brings it
     * back with [after] — the same thing the platform does to the screen across a rotation.
     */
    private fun assertItSurvivesRestoringWith(before: Int, after: Int) = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var isComposed by mutableStateOf(true)
        var maxLines by mutableStateOf(before)
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (isComposed) {
                    Box(Modifier.width(240.dp)) {
                        NewBitText(
                            newBitText = TEXT,
                            isEditing = false,
                            onNewBitTextChange = {},
                            onClickAdd = {},
                            focusRequester = FocusRequester(),
                            maxLines = maxLines
                        )
                    }
                }
            }
        }
        onNodeWithText(TEXT).assertIsDisplayed()

        val saved = runOnIdle { registry.performSave() }
        runOnIdle { isComposed = false }
        runOnIdle {
            registry = SaveableStateRegistry(saved) { true }
            maxLines = after
            isComposed = true
        }
        waitForIdle()

        onNodeWithText(TEXT).assertIsDisplayed()
    }

    private companion object {
        const val TEXT = "a bit that was already written when the screen turned"
    }
}
