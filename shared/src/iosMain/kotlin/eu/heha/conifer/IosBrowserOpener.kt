package eu.heha.conifer

import io.github.aakira.napier.Napier
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

object IosBrowserOpener : BrowserOpener {
    override fun open(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            Napier.w { "the login URL did not parse as an NSURL" }
            return false
        }
        return UIApplication.sharedApplication.openURL(nsUrl)
    }
}
