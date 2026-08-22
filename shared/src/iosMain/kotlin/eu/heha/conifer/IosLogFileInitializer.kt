// File-level: both the NSFileManager calls (their `error:` out-parameter) and the posix append
// below are cinterop declarations.
@file:OptIn(ExperimentalForeignApi::class)

package eu.heha.conifer

import eu.heha.conifer.log.LOG_FILE_PREFIX
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.log.MAX_LOG_FILES
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

/**
 * Log files land in `Documents/logs/`, alongside the database ([iosDocumentDirectory]) - so
 * they're included in an iTunes/Finder file transfer and a device backup, which is how a log
 * gets off an iPhone at all.
 */
object IosLogFileInitializer : LogFileInitializer {
    override fun createLogFile(fileName: String): LogFileSink? {
        val folder = iosDocumentDirectory() + "/logs"
        val created = NSFileManager.defaultManager.createDirectoryAtPath(
            path = folder,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (!created && !NSFileManager.defaultManager.fileExistsAtPath(folder)) return null
        pruneOldLogFiles(folder)
        return PosixLogFileSink("$folder/$fileName")
    }
}

/**
 * Deletes all but the newest [MAX_LOG_FILES] run logs in [folder] - by name, which sorts
 * chronologically for the names [eu.heha.conifer.log.logFileName] produces. Only ever touches
 * files that name generates.
 */
private fun pruneOldLogFiles(folder: String) {
    val names = NSFileManager.defaultManager
        .contentsOfDirectoryAtPath(folder, error = null)
        ?.filterIsInstance<String>()
        ?.filter { it.startsWith(LOG_FILE_PREFIX) }
        ?: return
    names.sorted()
        .dropLast(MAX_LOG_FILES - 1) // -1: this run's file is about to be created
        .forEach { NSFileManager.defaultManager.removeItemAtPath("$folder/$it", error = null) }
}

/**
 * Appends through `fopen`/`fputs` rather than `NSFileHandle`: no `NSData` round trip for a line of
 * text, and opening/closing per line means an app killed mid-run - exactly when the log matters -
 * has nothing left buffered to lose.
 */
private class PosixLogFileSink(private val path: String) : LogFileSink {
    override val location: String = path

    override fun appendLine(line: String) {
        val file = fopen(path, "a") ?: return
        try {
            fputs(line + "\n", file)
        } finally {
            fclose(file)
        }
    }
}
