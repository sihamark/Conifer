package eu.heha.conifer

import kotlinx.browser.window

object WasmBrowserOpener : BrowserOpener {
    override fun open(url: String) {
        window.open(url, "_blank")
    }
}
