package eu.heha.conifer

import android.content.ClipData
import android.content.Context
import androidx.core.content.getSystemService

class AndroidClipboardManager(
    private val context: Context
) : ClipboardManager {
    override fun copyToClipboard(text: String) {
        val clipboardManager = context.getSystemService<android.content.ClipboardManager>()
        val clip = ClipData.newPlainText("Entry", text)
        clipboardManager?.setPrimaryClip(clip)
    }
}