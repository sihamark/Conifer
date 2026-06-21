package eu.heha.conifer

import platform.UIKit.UIPasteboard

object IosClipboardController : ClipboardController {
    override fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}