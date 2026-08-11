package eu.heha.conifer

import eu.heha.conifer.log.CRASH_BREADCRUMB_FILE_NAME
import eu.heha.conifer.log.CrashBreadcrumbStore
import eu.heha.conifer.log.LOG_FILE_PREFIX
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.log.MAX_LOG_FILES
import java.io.File

/** Log files land in `logs/` next to the desktop app's `data/` folder (see [jvmDataFolder]). */
object JvmLogFileInitializer : LogFileInitializer {
    override fun createLogFile(fileName: String): LogFileSink? = runCatching {
        val folder = logFolder()
        pruneOldLogFiles(folder)
        JvmLogFileSink(File(folder, fileName))
    }.getOrNull()

    override fun createCrashBreadcrumbStore(): CrashBreadcrumbStore? = runCatching {
        FileCrashBreadcrumbStore(File(logFolder(), CRASH_BREADCRUMB_FILE_NAME))
    }.getOrNull()

    private fun logFolder(): File = File(jvmDataFolder(), "logs").also { it.mkdirs() }
}

/**
 * The crash breadcrumb as one small file, replaced whole on every write - see [CrashBreadcrumbStore]
 * for why it swallows its own failures rather than reporting them.
 *
 * Internal rather than private so a test can point one at a temporary file; the object above is the
 * only production caller, and it always names [CRASH_BREADCRUMB_FILE_NAME] in the log folder.
 */
internal class FileCrashBreadcrumbStore(private val file: File) : CrashBreadcrumbStore {
    override fun write(text: String) {
        runCatching { file.writeText(text) }
    }

    override fun read(): String? = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()

    override fun clear() {
        runCatching { file.delete() }
    }
}

/**
 * Deletes all but the newest [MAX_LOG_FILES] run logs in [folder] - by name, which sorts
 * chronologically for the names [eu.heha.conifer.log.logFileName] produces. Only ever touches
 * files that name generates, so anything else a user put there is safe.
 */
internal fun pruneOldLogFiles(folder: File) {
    val logs = folder.listFiles { file: File ->
        file.isFile && file.name.startsWith(LOG_FILE_PREFIX)
    } ?: return
    logs.sortedBy { it.name }
        .dropLast(MAX_LOG_FILES - 1) // -1: this run's file is about to be created
        .forEach { it.delete() }
}

/**
 * Opens and closes the file per line rather than holding a buffered writer: log lines are rare
 * (a handful per sync round), and an app killed mid-run - which is exactly when the log matters -
 * would otherwise lose whatever was still buffered.
 */
private class JvmLogFileSink(private val file: File) : LogFileSink {
    override val location: String = file.absolutePath

    override fun appendLine(line: String) {
        file.appendText(line + "\n")
    }
}
