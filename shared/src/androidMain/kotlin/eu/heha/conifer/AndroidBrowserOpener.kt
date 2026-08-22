package eu.heha.conifer

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.aakira.napier.Napier

class AndroidBrowserOpener(
    private val context: Context
) : BrowserOpener {
    override fun open(url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.applicationContext.startActivity(intent)
        true
    } catch (e: Exception) {
        // ActivityNotFoundException on a device with no browser at all; the login URL stays off
        // the log either way, it is a single-use credential for this login.
        Napier.e(e) { "failed to open the login URL in the browser" }
        false
    }
}
