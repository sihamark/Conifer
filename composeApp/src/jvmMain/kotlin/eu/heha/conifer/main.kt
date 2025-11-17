package eu.heha.conifer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.aakira.napier.DebugAntilog

fun main() = application {
    ConiferApp.initialize(DebugAntilog())
    Window(
        onCloseRequest = ::exitApplication,
        title = "Conifer",
    ) {
        ConiferApp.AppContent()
    }
}