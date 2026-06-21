package eu.heha.conifer

import android.content.ClipData
import android.content.Context
import androidx.core.content.getSystemService

class AndroidClipboardController(
    private val context: Context
) : ClipboardController {
    override fun copyToClipboard(text: String) {
        val clipboardManager = context.getSystemService<android.content.ClipboardManager>()
        val clip = ClipData.newPlainText("Entry", text)
        clipboardManager?.setPrimaryClip(clip)
    }
}