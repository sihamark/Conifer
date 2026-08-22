package eu.heha.conifer

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

object IosBrowserOpener : BrowserOpener {
    override fun open(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl)
    }
}
