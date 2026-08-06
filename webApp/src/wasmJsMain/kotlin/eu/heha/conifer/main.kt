package eu.heha.conifer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ConiferApp.initialize(
        isDebug = true,
        platform = WasmPlatform,
        dateTimeFormats = WasmDateTimeFormats(),
        databaseInitializer = WasmDatabaseInitializer,
        syncPrefsInitializer = WasmSyncPrefsInitializer,
        credentialsInitializer = WasmCredentialsInitializer,
        browserOpener = WasmBrowserOpener
    )
    ComposeViewport(document.body!!) {
        ConiferApp.AppContent()
    }
}