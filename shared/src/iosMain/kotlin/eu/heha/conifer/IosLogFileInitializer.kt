// File-level: both the NSFileManager calls (their `error:` out-parameter) and the posix append
// below are cinterop declarations.
@file:OptIn(ExperimentalForeignApi::class)

package eu.heha.conifer

import eu.heha.conifer.log.CRASH_BREADCRUMB_FILE_NAME
import eu.heha.conifer.log.CrashBreadcrumbStore
import eu.heha.conifer.log.LOG_FILE_PREFIX
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.log.MAX_LOG_FILES
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
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
        val folder = logFolder() ?: return null
        pruneOldLogFiles(folder)
        return PosixLogFileSink("$folder/$fileName")
    }

    override fun createCrashBreadcrumbStore(): CrashBreadcrumbStore? {
        val folder = logFolder() ?: return null
        return PosixCrashBreadcrumbStore("$folder/$CRASH_BREADCRUMB_FILE_NAME")
    }

    /** The log folder, created if it isn't there yet; null when it cannot be had at all. */
    private fun logFolder(): String? {
        val folder = iosDocumentDirectory() + "/logs"
        val created = NSFileManager.defaultManager.createDirectoryAtPath(
            path = folder,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (!created && !NSFileManager.defaultManager.fileExistsAtPath(folder)) return null
        return folder
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

/**
 * The crash breadcrumb as one small file, written through `fopen`/`fputs` for the same reason the log
 * lines are - it is written by a process that is going down, and the cheapest write is the one most
 * likely to land. Mode `w` truncates, which is what "the newest crash, replacing the last" means.
 *
 * Reading is a different situation entirely (an ordinary app start), so it goes through Foundation,
 * which can hand back the whole file as a string in one call. See [CrashBreadcrumbStore] for why
 * every failure here is swallowed.
 */
private class PosixCrashBreadcrumbStore(private val path: String) : CrashBreadcrumbStore {
    override fun write(text: String) {
        val file = fopen(path, "w") ?: return
        try {
            fputs(text, file)
        } finally {
            fclose(file)
        }
    }

    override fun read(): String? =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    override fun clear() {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}
