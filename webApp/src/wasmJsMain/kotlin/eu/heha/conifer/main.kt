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
        preferencesInitializer = WasmPreferencesInitializer,
        credentialsInitializer = WasmCredentialsInitializer,
        browserOpener = WasmBrowserOpener,
        reportShareController = WasmReportShareController,
        // The browser has no files, so all three of these keep to localStorage - see
        // WasmLogFileInitializer for what that costs and why a tab is worth it anyway.
        logFileInitializer = WasmLogFileInitializer,
        uncaughtErrorInitializer = WasmUncaughtErrorInitializer,
        logClosingInitializer = WasmLogClosingInitializer
    )
    ComposeViewport(document.body!!) {
        ConiferApp.AppContent()
    }
}