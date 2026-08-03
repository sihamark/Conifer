package eu.heha.conifer

import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.net.URI

object JvmBrowserOpener : BrowserOpener {
    override fun open(url: String): Boolean {
        // Deliberately no URL in any message below: the Login Flow v2 URL is a single-use
        // credential for this login, and the debug console doesn't pass through the log file's
        // redactor. The caller puts it on screen instead.
        if (!Desktop.isDesktopSupported()) {
            Napier.w { "no AWT Desktop on this JVM - the login URL has to be opened by hand" }
            return false
        }
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            // Routine on Linux, where AWT reports no BROWSE support on desktops that open a
            // browser perfectly well from a shell.
            Napier.w { "AWT Desktop cannot BROWSE - the login URL has to be opened by hand" }
            return false
        }
        return try {
            desktop.browse(URI(url))
            true
        } catch (e: Exception) {
            Napier.e(e) { "failed to open the login URL in the browser" }
            false
        }
    }
}
