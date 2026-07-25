package eu.heha.conifer

import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.net.URI

object JvmBrowserOpener : BrowserOpener {
    override fun open(url: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                    .isSupported(Desktop.Action.BROWSE)
            ) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                Napier.w { "no Desktop browse support on this platform - open manually: $url" }
            }
        } catch (e: Exception) {
            Napier.e(e) { "failed to open $url in the browser" }
        }
    }
}
