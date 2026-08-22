package eu.heha.conifer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import eu.heha.conifer.sync.SyncConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the sync surface offers when [eu.heha.conifer.BrowserOpener] couldn't hand the login URL
 * over. Login Flow v2 keeps polling regardless, so the only thing missing is a way for the user to
 * reach the URL — without which the pane would just spin forever.
 */
@OptIn(ExperimentalTestApi::class)
class SyncPaneBrowserFallbackTest {

    @Test
    fun theLoginUrlIsOfferedWhenTheBrowserDidNotOpen() = runComposeUiTest {
        val copied = mutableListOf<String>()
        setContent {
            SyncPane(
                state = SyncUiState(
                    connection = SyncConnectionState.Connecting(
                        loginUrl = LOGIN_URL,
                        didOpenBrowser = false
                    ),
                    isSyncOpen = true
                ),
                actions = SyncPaneActions(onClickCopyLoginUrl = { copied += it })
            )
        }

        onNodeWithText(LOGIN_URL).assertIsDisplayed()
        onNodeWithText("Copy link").performClick()

        assertEquals(listOf(LOGIN_URL), copied)

        // Compose resources pass `\n` through but leave `\'` alone, so an Android-style escaped
        // apostrophe reaches the screen backslash and all. Catch it here rather than in a review.
        onNodeWithText("couldn't", substring = true).assertIsDisplayed()
    }

    @Test
    fun theLoginUrlStaysHiddenWhileTheBrowserIsHandlingIt() = runComposeUiTest {
        setContent {
            SyncPane(
                state = SyncUiState(
                    // didOpenBrowser defaults to true: the normal path.
                    connection = SyncConnectionState.Connecting(loginUrl = LOGIN_URL),
                    isSyncOpen = true
                )
            )
        }

        // No reason to put a single-use login credential on screen when the browser has it.
        onNodeWithText(LOGIN_URL).assertDoesNotExist()
        onNodeWithText("Copy link").assertDoesNotExist()
    }

    @Test
    fun tryAgainIsOfferedSoAOneOffFailureIsNotADeadEnd() = runComposeUiTest {
        var retries = 0
        setContent {
            SyncPane(
                state = SyncUiState(
                    connection = SyncConnectionState.Connecting(
                        loginUrl = LOGIN_URL,
                        didOpenBrowser = false
                    ),
                    isSyncOpen = true
                ),
                actions = SyncPaneActions(onClickOpenLoginUrl = { retries++ })
            )
        }

        onNodeWithText("Try again").performClick()

        assertEquals(1, retries)
    }

    private companion object {
        const val LOGIN_URL = "https://cloud.example.org/index.php/login/v2/flow/abc123"
    }
}
