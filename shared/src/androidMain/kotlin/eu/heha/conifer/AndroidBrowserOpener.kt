package eu.heha.conifer

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.aakira.napier.Napier

class AndroidBrowserOpener(
    private val context: Context
) : BrowserOpener {
    override fun open(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Napier.e(e) { "failed to open $url in the browser" }
        }
    }
}
