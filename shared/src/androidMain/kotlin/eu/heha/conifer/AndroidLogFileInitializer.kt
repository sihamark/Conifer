package eu.heha.conifer

import android.content.Context
import eu.heha.conifer.log.LOG_FILE_PREFIX
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.log.MAX_LOG_FILES
import java.io.File

/**
 * Log files land in the app's private `files/logs/` folder - no storage permission involved, and
 * they go away with the app's data. Reachable over adb
 * (`adb shell run-as eu.heha.conifer cat files/logs/...`) and, on a debug build, via the file
 * picker if the app ever exposes them.
 */
class AndroidLogFileInitializer(private val context: Context) : LogFileInitializer {
    override fun createLogFile(fileName: String): LogFileSink? = runCatching {
        val folder = File(context.applicationContext.filesDir, "logs").also { it.mkdirs() }
        pruneOldLogFiles(folder)
        AndroidLogFileSink(File(folder, fileName))
    }.getOrNull()
}

/**
 * Deletes all but the newest [MAX_LOG_FILES] run logs in [folder] - by name, which sorts
 * chronologically for the names [eu.heha.conifer.log.logFileName] produces. Only ever touches
 * files that name generates.
 */
private fun pruneOldLogFiles(folder: File) {
    val logs = folder.listFiles { file: File ->
        file.isFile && file.name.startsWith(LOG_FILE_PREFIX)
    } ?: return
    logs.sortedBy { it.name }
        .dropLast(MAX_LOG_FILES - 1) // -1: this run's file is about to be created
        .forEach { it.delete() }
}

/** Per-line open/append/close - see the equivalent on JVM Desktop for why. */
private class AndroidLogFileSink(private val file: File) : LogFileSink {
    override val location: String = file.absolutePath

    override fun appendLine(line: String) {
        file.appendText(line + "\n")
    }
}
