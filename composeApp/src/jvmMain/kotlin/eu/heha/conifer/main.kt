package eu.heha.conifer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import conifer.composeapp.generated.resources.Res
import conifer.composeapp.generated.resources.app_icon
import io.github.aakira.napier.DebugAntilog
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    ConiferApp.initialize(DebugAntilog())
    Window(
        onCloseRequest = ::exitApplication,
        icon = painterResource(Res.drawable.app_icon),
        title = "Conifer",
    ) {
        ConiferApp.AppContent()
    }
}