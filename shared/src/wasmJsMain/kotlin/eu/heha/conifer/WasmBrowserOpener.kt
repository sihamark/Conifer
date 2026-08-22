package eu.heha.conifer

import io.github.aakira.napier.Napier
import kotlinx.browser.window

object WasmBrowserOpener : BrowserOpener {
    override fun open(url: String): Boolean {
        // A blocked popup is the normal failure here: browsers only allow window.open during a
        // user gesture, and the login URL arrives after a round trip to the server, by which time
        // the click that started it may no longer count. Then this returns null and the user has
        // to open the URL themselves.
        val opened = window.open(url, "_blank") != null
        if (!opened) Napier.w { "window.open was blocked - the login URL has to be opened by hand" }
        return opened
    }
}
