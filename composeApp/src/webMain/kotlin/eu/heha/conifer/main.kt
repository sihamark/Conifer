package eu.heha.conifer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.aakira.napier.DebugAntilog

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ConiferApp.initialize(DebugAntilog())
    ComposeViewport {
        App()
    }
}