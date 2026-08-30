package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import eu.heha.conifer.ui.DatedBits
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The screen's shortcuts as the user meets them: real key events into a whole [BitsPane].
 *
 * The point of most of these is *where* the events are sent — at the root, with nothing on the
 * screen focused and the text field never so much as clicked. That is the case the shortcuts exist
 * for and the one that quietly breaks, since a key event is only offered to the focused node and its
 * ancestors.
 */
@OptIn(ExperimentalTestApi::class)
class BitsPaneShortcutsTest {

    @Test
    fun theShortcutsWorkWithNothingOnTheScreenFocused() = runComposeUiTest {
        val stepped = mutableListOf<Int>()
        setContent {
            // Text in the field, so the pane does not hand it focus on the way in, and nothing here
            // clicks anything: this is the screen as it sits after a bit has been written and the
            // user has clicked the background, which is where the shortcuts have to keep working.
            ShortcutPane(
                state = BitsPaneState(newBitText = "half a bit"),
                actions = BitsPaneActions(onShiftDate = { stepped += it })
            )
        }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionLeft) } }

        assertEquals(
            listOf(-1),
            stepped,
            "with no focus target of its own the pane never sees the event: Compose falls back to " +
                    "key input above the root focus node, and the pane's handler is below it"
        )
    }

    @Test
    fun altUpAndAltDownNudgeTheTimeBySliderSlots() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        setContent {
            ShortcutPane(
                state = BitsPaneState(composerTime = LocalTime(12, 0)),
                actions = BitsPaneActions(onSelectTime = { picked += it })
            )
        }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) } }
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) } }

        // One event each, not one per key-down *and* key-up.
        assertEquals(listOf(LocalTime(12, 15), LocalTime(11, 45)), picked)
    }

    @Test
    fun theTimeNudgeWorksWithNothingOnTheScreenFocusedEither() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        setContent {
            ShortcutPane(
                state = BitsPaneState(newBitText = "half a bit", composerTime = LocalTime(12, 0)),
                actions = BitsPaneActions(onSelectTime = { picked += it })
            )
        }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) } }

        assertEquals(listOf(LocalTime(12, 15)), picked)
    }

    @Test
    fun bareUpAndDownStayOutOfTheTime() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        setContent {
            ShortcutPane(
                state = BitsPaneState(composerTime = LocalTime(12, 0)),
                actions = BitsPaneActions(onSelectTime = { picked += it })
            )
        }

        onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        assertEquals(emptyList(), picked, "the bare arrows are the caret's; only Alt+↑/↓ move time")
    }

    /**
     * The point of the modifier: in a bit that has grown past one line, the nudge still works where
     * the typing left off and the cursor stays there, ready for the rest of the sentence — now that
     * the nudge is the pane's, the event has to reach it past a focused field that wants those keys.
     */
    @Test
    fun altUpNudgesAWrappedBitWithoutDisturbingTheCursor() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        // Real snapshot state, not a plain var: the field puts the cursor back at the end whenever
        // its text is replaced from outside, so a pane that failed to recompose with what was typed
        // would look to the field like an outside replacement and undo it.
        val text = mutableStateOf(WRAPPING_TEXT)
        setContent {
            // Narrow enough that the text takes more than one line, and tall enough to be allowed to.
            Box(Modifier.width(260.dp)) {
                ShortcutPane(
                    state = BitsPaneState(newBitText = text.value, composerTime = LocalTime(12, 0)),
                    actions = BitsPaneActions(
                        onNewBitTextChange = { text.value = it },
                        onSelectTime = { picked += it }
                    ),
                    composerMaxLines = 3
                )
            }
        }

        // Matched on being a text field rather than on its contents, which the typing below changes.
        val field = onNode(hasSetTextAction())
        field.performClick()
        // Wherever in the wrapped text the click left the cursor, "A" marks the spot — and if the
        // nudge in between leaves the cursor alone, "B" lands directly after it.
        field.performTextInput("A")
        field.performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) } }
        field.performTextInput("B")

        assertEquals(listOf(LocalTime(12, 15)), picked)
        assertTrue(
            text.value.contains("AB"),
            "typing must carry on where it left off, got: ${text.value}"
        )
    }

    @Test
    fun altLeftAndAltRightStepTheDay() = runComposeUiTest {
        val stepped = mutableListOf<Int>()
        setContent { ShortcutPane(actions = BitsPaneActions(onShiftDate = { stepped += it })) }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionLeft) } }
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionRight) } }

        // One step each, not one per key-down *and* key-up. Left is back in time, as on the strip.
        assertEquals(listOf(-1, 1), stepped)
    }

    @Test
    fun altPageUpAndPageDownStepTheDayAsTheArrowsDo() = runComposeUiTest {
        val stepped = mutableListOf<Int>()
        setContent { ShortcutPane(actions = BitsPaneActions(onShiftDate = { stepped += it })) }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.PageUp) } }
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.PageDown) } }

        assertEquals(listOf(-1, 1), stepped)
    }

    @Test
    fun shiftAltArrowsSkipToDaysWithBits() = runComposeUiTest {
        val skipped = mutableListOf<Int>()
        val stepped = mutableListOf<Int>()
        setContent {
            ShortcutPane(
                actions = BitsPaneActions(
                    onShiftDate = { stepped += it },
                    onSkipToDateWithBits = { skipped += it }
                )
            )
        }

        onRoot().performKeyInput {
            withKeyDown(Key.AltLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        }

        assertEquals(listOf(-1), skipped)
        assertTrue(stepped.isEmpty(), "Shift should skip instead of stepping, not as well as")
    }

    @Test
    fun theDayKeysNeedAltSoTheTextFieldKeepsTheBareArrowsAndPageKeys() = runComposeUiTest {
        val stepped = mutableListOf<Int>()
        setContent { ShortcutPane(actions = BitsPaneActions(onShiftDate = { stepped += it })) }

        onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        onRoot().performKeyInput { pressKey(Key.PageUp) }

        assertTrue(stepped.isEmpty(), "bare arrows and page keys belong to the field and the list")
    }

    @Test
    fun altHomeGoesToTodayAndAltZeroToAllDays() = runComposeUiTest {
        var todays = 0
        var allDays = 0
        setContent {
            ShortcutPane(
                actions = BitsPaneActions(
                    onClickAllDays = { allDays++ },
                    onSelectToday = { todays++ }
                )
            )
        }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.MoveHome) } }
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.Zero) } }

        assertEquals(1, todays)
        assertEquals(1, allDays)
    }

    @Test
    fun todayAndNowAreOnLettersToo() = runComposeUiTest {
        var todays = 0
        var times = 0
        setContent {
            ShortcutPane(
                actions = BitsPaneActions(
                    onSelectToday = { todays++ },
                    onResetTime = { times++ }
                )
            )
        }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.T) } }
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.N) } }

        assertEquals(1, todays, "Home is missing from every Apple laptop; T has to do the same")
        assertEquals(1, times, "and N the same as End")
    }

    @Test
    fun altEndHandsTheTimeBackToTheClockWithoutTouchingTheDay() = runComposeUiTest {
        var times = 0
        var todays = 0
        setContent {
            ShortcutPane(
                state = BitsPaneState(
                    composerDate = LocalDate(2026, 8, 4),
                    composerTime = LocalTime(9, 30)
                ),
                actions = BitsPaneActions(
                    onResetTime = { times++ },
                    onSelectToday = { todays++ }
                )
            )
        }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.MoveEnd) } }

        assertEquals(1, times)
        assertEquals(0, todays, "the time's key is not the day's: yesterday keeps being yesterday")
    }

    @Test
    fun escapeEndsAnEditBeforeItTouchesTheSelection() = runComposeUiTest {
        var cancelled = 0
        var reset = 0
        setContent {
            ShortcutPane(
                // Both are on: the edit is the one that has to give way first.
                state = BitsPaneState(editingBitId = "a-bit", filterDate = LocalDate(2026, 8, 4)),
                actions = BitsPaneActions(
                    onResetSelection = { reset++ },
                    onCancelEdit = { cancelled++ }
                )
            )
        }

        onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(1, cancelled)
        assertEquals(0, reset)
    }

    @Test
    fun escapeClearsTheDaySelectionWhenNothingIsBeingEdited() = runComposeUiTest {
        var reset = 0
        setContent {
            ShortcutPane(
                state = BitsPaneState(filterDate = LocalDate(2026, 8, 4)),
                actions = BitsPaneActions(onResetSelection = { reset++ })
            )
        }

        onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(1, reset)
    }

    @Test
    fun escapeClearsANudgedTimeEvenWithNoDayPicked() = runComposeUiTest {
        var reset = 0
        setContent {
            // A time is as much a thing to be stuck with as a day, so it is enough on its own.
            ShortcutPane(
                state = BitsPaneState(composerTime = LocalTime(9, 30)),
                actions = BitsPaneActions(onResetSelection = { reset++ })
            )
        }

        onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(1, reset)
    }

    @Test
    fun escapeAlsoClearsADayTheKeysSteppedToWithoutFilteringTheList() = runComposeUiTest {
        var reset = 0
        setContent {
            // What Alt+← leaves behind on an unfiltered list: a composer date and no filter.
            ShortcutPane(
                state = BitsPaneState(composerDate = LocalDate(2026, 8, 4)),
                actions = BitsPaneActions(onResetSelection = { reset++ })
            )
        }

        onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(1, reset)
    }

    @Test
    fun theShortcutsStillWorkAfterSomethingElseOnTheScreenHasTakenFocus() = runComposeUiTest {
        val stepped = mutableListOf<Int>()
        setContent {
            ShortcutPane(
                state = BitsPaneState(newBitText = "half a bit"),
                actions = BitsPaneActions(onShiftDate = { stepped += it })
            )
        }

        // Focus into the field, the way typing would, and then send the keys from the root: the
        // handler sits above the field, so it has to see them first either way.
        onNodeWithText("half a bit").performClick()
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionLeft) } }

        assertEquals(listOf(-1), stepped)
    }

    @Test
    fun altHOpensAndClosesTheListOfShortcuts() = runComposeUiTest {
        setContent { ShortcutPane() }
        val title = "Keyboard shortcuts"

        onNodeWithText(title).assertDoesNotExist()
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.H) } }
        onNodeWithText(title).assertExists()
        // The same key again puts it away: it is a thing being shown, not a place being gone to.
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.H) } }
        onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun f1OpensTheListToo() = runComposeUiTest {
        setContent { ShortcutPane() }

        onRoot().performKeyInput { pressKey(Key.F1) }

        onNodeWithText("Keyboard shortcuts").assertExists()
    }

    @Test
    fun theListNamesEveryShortcutTheScreenHandles() = runComposeUiTest {
        setContent { ShortcutPane() }
        onRoot().performKeyInput { pressKey(Key.F1) }

        // Not a spelling test: this is the one place that would notice a key being wired up in
        // handleShortcut and never told to the user.
        listOf(
            "Enter", "Shift+Enter", "Alt+↑/↓", "Alt+←/→", "Alt+PgUp/PgDn", "Shift+Alt+←/→",
            "Alt+Home", "Alt+0", "Esc", "Alt+H", "F1"
        ).forEach { keys ->
            onNodeWithText(keys).assertExists()
        }
    }

    /**
     * The macOS bargain, from the side that matters most: ⌥←/→ is how that platform has always moved
     * by word, and where the screen asks for ⌃⌥ instead, ⌥ on its own must reach the text field
     * untouched — a swallowed word-jump is exactly the complaint this chord exists to answer.
     */
    @Test
    fun onTheCtrlAltChordPlainAltArrowsAreLeftToTheWordsInTheText() = runComposeUiTest {
        val stepped = mutableListOf<Int>()
        val skipped = mutableListOf<Int>()
        setContent {
            ShortcutPane(
                actions = BitsPaneActions(
                    onShiftDate = { stepped += it },
                    onSkipToDateWithBits = { skipped += it }
                ),
                shortcutChord = ShortcutChord.CtrlAlt
            )
        }

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionLeft) } }
        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionRight) } }
        onRoot().performKeyInput {
            withKeyDown(Key.AltLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        }

        assertTrue(stepped.isEmpty(), "Alt+←/→ is word-jump where this chord is in use")
        assertTrue(skipped.isEmpty(), "and Shift+Alt+←/→ is selecting by word")
    }

    @Test
    fun theCtrlAltChordStepsAndSkipsTheDayWithCtrlHeldAsWell() = runComposeUiTest {
        val stepped = mutableListOf<Int>()
        val skipped = mutableListOf<Int>()
        setContent {
            ShortcutPane(
                actions = BitsPaneActions(
                    onShiftDate = { stepped += it },
                    onSkipToDateWithBits = { skipped += it }
                ),
                shortcutChord = ShortcutChord.CtrlAlt
            )
        }

        onRoot().performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionLeft) } }
        }
        onRoot().performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionRight) } }
        }
        onRoot().performKeyInput {
            withKeyDown(Key.CtrlLeft) {
                withKeyDown(Key.AltLeft) {
                    withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) }
                }
            }
        }

        assertEquals(listOf(-1, 1), stepped)
        assertEquals(listOf(-1), skipped)
    }

    @Test
    fun theCtrlAltChordNudgesTheTimeAndOpensTheListToo() = runComposeUiTest {
        val picked = mutableListOf<LocalTime>()
        setContent {
            ShortcutPane(
                state = BitsPaneState(composerTime = LocalTime(12, 0)),
                actions = BitsPaneActions(onSelectTime = { picked += it }),
                shortcutChord = ShortcutChord.CtrlAlt
            )
        }

        onRoot().performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) } }
        }
        onRoot().performKeyInput {
            withKeyDown(Key.CtrlLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.H) } }
        }

        assertEquals(listOf(LocalTime(12, 15)), picked)
        onNodeWithText("Keyboard shortcuts").assertExists()
    }

    @Test
    fun theListNamesTheChordThisPlatformActuallyAnswersTo() = runComposeUiTest {
        setContent { ShortcutPane(shortcutChord = ShortcutChord.CtrlAlt) }
        onRoot().performKeyInput { pressKey(Key.F1) }

        listOf(
            "Ctrl+Alt+↑/↓", "Ctrl+Alt+←/→", "Ctrl+Alt+PgUp/PgDn", "Shift+Ctrl+Alt+←/→",
            "Ctrl+Alt+Home", "Ctrl+Alt+0", "Ctrl+Alt+H"
        ).forEach { keys ->
            onNodeWithText(keys).assertExists()
        }
        // The keys that never had the chord on them are the same on every platform.
        onNodeWithText("Enter").assertExists()
        onNodeWithText("F1").assertExists()
    }

    @Test
    fun escapeClosesTheListBeforeItTouchesAnythingElse() = runComposeUiTest {
        var cancelled = 0
        var reset = 0
        setContent {
            ShortcutPane(
                // Both of the other things Esc could do are on offer; the overlay still goes first.
                state = BitsPaneState(editingBitId = "a-bit", filterDate = LocalDate(2026, 8, 4)),
                actions = BitsPaneActions(
                    onResetSelection = { reset++ },
                    onCancelEdit = { cancelled++ }
                )
            )
        }
        onRoot().performKeyInput { pressKey(Key.F1) }

        onRoot().performKeyInput { pressKey(Key.Escape) }

        onNodeWithText("Keyboard shortcuts").assertDoesNotExist()
        assertEquals(0, cancelled, "the edit is behind the overlay and should have been left alone")
        assertEquals(0, reset)
    }

    @Test
    fun theShortcutsIconIsOfferedOnlyWhereThereIsAKeyboardForIt() = runComposeUiTest {
        setContent { ShortcutPane(hasHardwareKeyboard = false) }

        onNodeWithContentDescription("Show keyboard shortcuts").assertDoesNotExist()
    }

    @Test
    fun theShortcutsIconOpensTheListWhereThereIsAKeyboard() = runComposeUiTest {
        setContent { ShortcutPane(hasHardwareKeyboard = true) }

        onNodeWithContentDescription("Show keyboard shortcuts").performClick()

        onNodeWithText("Keyboard shortcuts").assertExists()
    }

    @Test
    fun aKeyboardThatArrivesLaterEarnsTheIconByBeingUsed() = runComposeUiTest {
        // A tablet with a keyboard plugged in: the platform said no, and Alt says otherwise.
        setContent { ShortcutPane(hasHardwareKeyboard = false) }
        onNodeWithContentDescription("Show keyboard shortcuts").assertDoesNotExist()

        onRoot().performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionLeft) } }

        onNodeWithContentDescription("Show keyboard shortcuts").assertExists()
    }

    private companion object {
        const val WRAPPING_TEXT = "a bit long enough to take more than one line in a narrow field"
    }
}

/**
 * A pane with a bit in it, laid out as the stacked (phone) layout so the test doesn't depend on the
 * window size the test host happens to report.
 */
@androidx.compose.runtime.Composable
private fun ShortcutPane(
    state: BitsPaneState = BitsPaneState(),
    actions: BitsPaneActions = BitsPaneActions(),
    composerMaxLines: Int = 1,
    hasHardwareKeyboard: Boolean = false,
    shortcutChord: ShortcutChord = ShortcutChord.Alt
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
        composerMaxLines = composerMaxLines,
        hasHardwareKeyboard = hasHardwareKeyboard,
        shortcutChord = shortcutChord
    )
}
