package eu.heha.conifer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.app_icon
import conifer.shared.generated.resources.app_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Set to true by the Gradle `run` task; absent (false) for packaged release distributables.
private val isDebug: Boolean
    get() = System.getProperty("conifer.debug")?.toBoolean() == true

fun main() = application {
    ConiferApp.initialize(
        isDebug = isDebug,
        platform = JvmPlatform,
        databaseInitializer = JvmDatabaseInitializer,
        syncPrefsInitializer = JvmSyncPrefsInitializer,
        clipboardController = JvmClipboardController
    )
    Window(
        onCloseRequest = ::exitApplication,
        icon = painterResource(Res.drawable.app_icon),
        title = stringResource(Res.string.app_name)
    ) {
        ConiferApp.AppContent()
    }
}