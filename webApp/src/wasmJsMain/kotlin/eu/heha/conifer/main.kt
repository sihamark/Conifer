package eu.heha.conifer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ConiferApp.initialize(
        isDebug = true,
        platform = WasmPlatform,
        databaseInitializer = WasmDatabaseInitializer
    )
    ComposeViewport(document.body!!) {
        ConiferApp.AppContent()
    }
}