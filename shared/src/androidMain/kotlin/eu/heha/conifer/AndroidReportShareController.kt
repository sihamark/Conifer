package eu.heha.conifer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.aakira.napier.Napier
import java.io.File

/**
 * The system share sheet, with the report as an attached file.
 *
 * A file rather than `EXTRA_TEXT`: a report carries the tail of a log and is far too long for the
 * one-line text a share target expects, and an intent extra that size is close enough to the
 * transaction limit to be its own failure mode.
 *
 * The file lands in `cacheDir/reports`, which is what [FILE_PROVIDER_PATH] and the app module's
 * `file_paths.xml` agree on, and is handed over as a `content://` URI - the only shape another app
 * is allowed to read, and only for as long as this intent lives.
 *
 * Started with `FLAG_ACTIVITY_NEW_TASK` because this holds the application context: the screen is
 * not what asks for the share, the view model is.
 */
class AndroidReportShareController(private val context: Context) : ReportShareController {
    override fun share(fileName: String, text: String): Boolean {
        val app = context.applicationContext
        return runCatching {
            val folder = File(app.cacheDir, FILE_PROVIDER_PATH).also { it.mkdirs() }
            val file = File(folder, fileName).also { it.writeText(text) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(
                Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrElse { error ->
            // Most plausibly a missing or misdeclared FileProvider, which is a build-time mistake
            // and worth a line that says so; the screen falls back to the clipboard either way.
            Napier.e(error) { "could not offer the crash report to the share sheet" }
            false
        }
    }
}

/**
 * The folder under `cacheDir` the shared reports go in. Must stay in step with the `<cache-path>` in
 * the app module's `res/xml/file_paths.xml` - a FileProvider hands out nothing it was not told about.
 */
private const val FILE_PROVIDER_PATH = "reports"
