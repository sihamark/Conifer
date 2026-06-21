package eu.heha.conifer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.app_icon
import conifer.shared.generated.resources.app_name
import io.github.aakira.napier.DebugAntilog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    ConiferApp.initialize(
        antilog = DebugAntilog(),
        platform = JvmPlatform,
        databaseInitializer = JvmDatabaseInitializer,
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