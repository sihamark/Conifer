package eu.heha.conifer.ui.bits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.heha.conifer.log.CrashBreadcrumb
import eu.heha.conifer.log.LastRunEnd
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The banner is the whole point of writing a crash down: a log file nobody is told about answers
 * nothing. So it has to say what happened, hand it over on one tap, and be dismissable - and it has
 * to stay away entirely when the last run ended the way runs are supposed to.
 */
@OptIn(ExperimentalTestApi::class)
class RunEndPromptTest {

    @Test
    fun saysWhatTheLastRunDiedOfAndOffersToCopyIt() = runComposeUiTest {
        var copied = false
        setContent {
            ConiferTheme {
                RunEndPromptItem(
                    state = BitsPaneState(lastRunEnd = LastRunEnd.Crashed(LAST_CRASH)),
                    actions = BitsPaneActions(onClickCopyRunEndReport = { copied = true })
                )
            }
        }
        waitForIdle()

        onNodeWithText("The last run ended in an error.", substring = true).assertExists()
        onNodeWithText("IllegalStateException: boom", substring = true).assertExists()

        onNodeWithText("Copy details").performClick()
        assertTrue(copied, "the copy button did not hand the report over")
    }

    /** Where the platform has a share sheet, that is the offer that actually sends the report. */
    @Test
    fun offersToShareTheReportWhereThePlatformCan() = runComposeUiTest {
        var shared = false
        setContent {
            ConiferTheme {
                RunEndPromptItem(
                    state = BitsPaneState(lastRunEnd = LastRunEnd.Crashed(LAST_CRASH), isSharePossible = true),
                    actions = BitsPaneActions(onClickShareRunEndReport = { shared = true })
                )
            }
        }
        waitForIdle()

        onNodeWithText("Share report…").performClick()
        assertTrue(shared, "the share button did not hand the report over")
    }

    /** Web has neither a log file nor a share sheet, so it gets neither offer. */
    @Test
    fun leavesOutTheShareButtonWhereThereIsNowhereToShareTo() = runComposeUiTest {
        setContent {
            ConiferTheme {
                RunEndPromptItem(
                    state = BitsPaneState(lastRunEnd = LastRunEnd.Crashed(LAST_CRASH)),
                    actions = BitsPaneActions()
                )
            }
        }
        waitForIdle()

        onNodeWithText("Share report…").assertDoesNotExist()
    }

    /** Dismissing is its own action, and copying deliberately is not it - see `dismissCrashReport`. */
    @Test
    fun canBeDismissed() = runComposeUiTest {
        var dismissed = false
        setContent {
            ConiferTheme {
                RunEndPromptItem(
                    state = BitsPaneState(lastRunEnd = LastRunEnd.Crashed(LAST_CRASH)),
                    actions = BitsPaneActions(onDismissRunEndReport = { dismissed = true })
                )
            }
        }
        waitForIdle()

        onNodeWithText("Dismiss").performClick()
        assertTrue(dismissed, "the banner cannot be dismissed")
    }

    /**
     * Where the platform brought no clipboard the banner is a notice rather than an offer: it still
     * says the run crashed, and it no longer offers a button that would do nothing.
     */
    @Test
    fun leavesOutTheCopyButtonWithoutAClipboard() = runComposeUiTest {
        setContent {
            ConiferTheme {
                RunEndPromptItem(
                    state = BitsPaneState(lastRunEnd = LastRunEnd.Crashed(LAST_CRASH), isCopyPossible = false),
                    actions = BitsPaneActions()
                )
            }
        }
        waitForIdle()

        onNodeWithText("Copy details").assertDoesNotExist()
        onNodeWithText("Dismiss").assertExists()
    }

    /**
     * The banner floats over the bits with nothing under it to stop at, so an error long enough to
     * make it taller than the window would push its own buttons off the bottom - leaving a notice
     * covering the app with no way to dismiss it. That is what `summaryMaxLines` is for, and this
     * hands it the worst case: the most lines any window grants, and a message with no end to it.
     */
    @Test
    fun keepsItsButtonsReachableWhenTheErrorIsEnormous() = runComposeUiTest {
        setContent {
            ConiferTheme {
                Box(Modifier.size(400.dp, 600.dp)) {
                    RunEndPromptItem(
                        state = BitsPaneState(
                            lastRunEnd = LastRunEnd.Crashed(LAST_CRASH.copy(message = STACKMAP_DUMP))
                        ),
                        actions = BitsPaneActions(),
                        // What the tallest windows give it; see `currentRunEndMaxLines`.
                        summaryMaxLines = 8
                    )
                }
            }
        }
        waitForIdle()

        // Displayed, not merely present: off the bottom of the window it would still exist.
        onNodeWithText("Dismiss").assertIsDisplayed()
        onNodeWithText("Copy details").assertIsDisplayed()
        // And the sentence saying what it is survives the error rather than being crowded out by it.
        onNodeWithText("The last run ended in an error.", substring = true).assertIsDisplayed()
    }

    @Test
    fun staysAwayWhenTheLastRunEndedNormally() = runComposeUiTest {
        setContent {
            ConiferTheme {
                RunEndPromptItem(state = BitsPaneState(), actions = BitsPaneActions())
            }
        }
        waitForIdle()

        onNodeWithText("The last run ended in an error.", substring = true).assertDoesNotExist()
    }

    private companion object {
        val LAST_CRASH = CrashBreadcrumb(
            buildLabel = "1.2.4 (9), commit 29ba2459, built 2026-08-10T19:58:08Z",
            // 2026-07-28 12:34:56.789 UTC. Not asserted on: the banner spells the moment in the
            // reader's own time zone, which is the machine's here.
            atEpochMillis = 1_785_242_096_789,
            origin = "main",
            type = "IllegalStateException",
            message = "boom",
            frames = listOf("at eu.heha.conifer.sync.SyncEngine.push(SyncEngine.kt:214)"),
            logFile = "/logs/conifer-2026-07-28_143201.log",
        )

        /**
         * The shape of the message that started this: a `VerifyError` listing every local in the
         * frame. Built here rather than read off a breadcrumb, because `crashBreadcrumb` cuts a
         * message to [eu.heha.conifer.log.MAX_CRASH_MESSAGE_CHARS] on the way in - and the banner
         * has to hold its shape whatever it is handed, including a record an older version wrote.
         */
        val STACKMAP_DUMP = "Inconsistent stackmap frames at branch target 2596. locals: { " +
                "'androidx/compose/ui/Modifier', 'androidx/compose/runtime/Composer', ".repeat(60) +
                "}"
    }
}
