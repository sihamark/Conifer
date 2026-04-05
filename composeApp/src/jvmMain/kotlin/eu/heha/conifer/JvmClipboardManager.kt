package eu.heha.conifer

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

object JvmClipboardManager : ClipboardManager {
    override fun copyToClipboard(text: String) {
        val stringSelection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard
            .setContents(stringSelection, null)
    }
}