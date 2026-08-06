package eu.heha.conifer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.app_icon
import conifer.shared.generated.resources.app_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import kotlin.math.roundToInt

// Set to true by the Gradle `run` task; absent (false) for packaged release distributables.
private val isDebug: Boolean
    get() = System.getProperty("conifer.debug")?.toBoolean() == true

// Smallest window the UI still works in: narrower than the mockup's 390 dp phone, but the
// single-pane layout takes it, and the height still holds the top bar, a day header with a bit or
// two, and the composer. Deliberately below the 600 dp two-pane breakpoint, so the single-pane
// layout stays reachable by resizing.
private val MIN_WINDOW_SIZE = DpSize(300.dp, 480.dp)

/**
 * A window's AWT units line up 1:1 with dp — the display's scale factor is the toolkit's business,
 * not ours (checked on a 2x display: the default `WindowState` of 800 x 600.dp yields an 800 x 600
 * AWT window), so the size passes through unconverted.
 */
private fun DpSize.toAwtDimension() =
    Dimension(width.value.roundToInt(), height.value.roundToInt())

fun main() = application {
    ConiferApp.initialize(
        isDebug = isDebug,
        platform = JvmPlatform,
        dateTimeFormats = JvmDateTimeFormats(),
        databaseInitializer = JvmDatabaseInitializer,
        syncPrefsInitializer = JvmSyncPrefsInitializer,
        credentialsInitializer = JvmCredentialsInitializer,
        browserOpener = JvmBrowserOpener,
        clipboardController = JvmClipboardController,
        logFileInitializer = JvmLogFileInitializer
    )
    Window(
        onCloseRequest = ::exitApplication,
        icon = painterResource(Res.drawable.app_icon),
        title = stringResource(Res.string.app_name)
    ) {
        // Compose has no window-state property for this; the limit goes straight to the AWT window.
        LaunchedEffect(Unit) {
            window.minimumSize = MIN_WINDOW_SIZE.toAwtDimension()
        }
        ConiferApp.AppContent()
    }
}