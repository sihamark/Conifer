package eu.heha.conifer

import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.io.File

/**
 * A desktop has no share sheet, so the report is written into a `reports/` folder beside the app's
 * data and that folder is opened in the file manager - which is the desktop's version of the same
 * gesture: the file is now somewhere the user can drag into a mail, and they were shown where.
 *
 * The file is written first and opened after. If the file manager will not open - a Linux JVM whose
 * AWT reports no `Desktop.Action.OPEN` even where `xdg-open` works - the report is still on disk and
 * this says so in the log, but it answers `false`, and the screen falls back to the clipboard rather
 * than claim something happened that the user cannot see.
 */
object JvmReportShareController : ReportShareController {
    override fun share(fileName: String, text: String): Boolean {
        val file = writeReport(reportsFolder(), fileName, text) ?: return false
        Napier.i { "crash report written to ${file.absolutePath}" }
        return revealInFileManager(file)
    }

    private fun reportsFolder(): File = File(jvmDataFolder, "reports")
}

/** The report as a file in [folder], replacing an earlier share of the same crash; null on failure. */
internal fun writeReport(folder: File, fileName: String, text: String): File? = runCatching {
    folder.mkdirs()
    File(folder, fileName).also { it.writeText(text) }
}.getOrNull()

/**
 * Shows [file] in the file manager: selected in its folder where the platform can do that (Finder,
 * Explorer), and otherwise by opening the folder itself.
 */
private fun revealInFileManager(file: File): Boolean {
    if (!Desktop.isDesktopSupported()) {
        Napier.w { "no AWT Desktop on this JVM - the crash report has to be found by hand" }
        return false
    }
    val desktop = Desktop.getDesktop()
    return runCatching {
        when {
            desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR) -> {
                desktop.browseFileDirectory(file)
                true
            }

            desktop.isSupported(Desktop.Action.OPEN) -> {
                desktop.open(file.parentFile)
                true
            }

            else -> {
                Napier.w { "AWT Desktop cannot open a folder - the crash report is at $file" }
                false
            }
        }
    }.getOrElse { error ->
        Napier.e(error) { "failed to show the crash report in the file manager" }
        false
    }
}
