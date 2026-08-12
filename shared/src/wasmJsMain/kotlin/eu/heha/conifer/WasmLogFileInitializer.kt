package eu.heha.conifer

import eu.heha.conifer.log.LAST_RUN_FILE_NAME
import eu.heha.conifer.log.LOG_FILE_PREFIX
import eu.heha.conifer.log.LastRunStore
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.log.LogTailReader
import kotlinx.browser.localStorage

/**
 * The browser has no file system, so `localStorage` is the file system: one key per run holding that
 * run's log, and one more holding the last-run record. Everything else - a new log per start, the
 * oldest pruned, the record read at the next start - then works here exactly as it does on the
 * platforms with real files.
 *
 * Which matters because a browser tab is *more* likely to end badly than an app is, not less: a
 * reload, a closed tab and a tab the browser discards for memory all end the run, and none of them
 * leaves anything behind unless it was written down as it went.
 *
 * The costs of storing it this way are real and are why the numbers here are smaller than
 * [eu.heha.conifer.log.MAX_LOG_FILES]: `localStorage` is a handful of megabytes for the whole origin,
 * shared with the app's preferences and its credentials, and every line written costs a synchronous
 * write. [MAX_WEB_LOG_RUNS] runs of [MAX_WEB_LOG_CHARS] each is the most this may claim.
 */
object WasmLogFileInitializer : LogFileInitializer {
    override fun createLogFile(fileName: String): LogFileSink? = runCatching {
        pruneOldLogs()
        localStorage.setItem(logKey(fileName), "")
        LocalStorageLogFileSink(fileName)
    }.getOrNull()

    override fun createLastRunStore(): LastRunStore = LocalStorageLastRunStore

    override fun readLogTail(fileName: String, maxChars: Int): String? = runCatching {
        localStorage.getItem(logKey(fileName))?.takeLast(maxChars)
    }.getOrNull()

    /**
     * Deletes all but the newest [MAX_WEB_LOG_RUNS] - 1 run logs, by key, which sorts
     * chronologically for the names [eu.heha.conifer.log.logFileName] produces. Only ever touches
     * keys this object wrote.
     */
    private fun pruneOldLogs() {
        val keys = (0 until localStorage.length)
            .mapNotNull { index -> localStorage.key(index) }
            .filter { it.startsWith(LOG_KEY_PREFIX + LOG_FILE_PREFIX) }
            .sorted()
        keys.dropLast(MAX_WEB_LOG_RUNS - 1).forEach { localStorage.removeItem(it) }
    }
}

/** How many runs' logs the browser keeps - fewer than a disk does, see [WasmLogFileInitializer]. */
const val MAX_WEB_LOG_RUNS = 3

/** How much of one run's log the browser keeps; the oldest lines go first once it is full. */
const val MAX_WEB_LOG_CHARS = 64_000

private const val LOG_KEY_PREFIX = "conifer-log:"

private const val LAST_RUN_KEY = "conifer-$LAST_RUN_FILE_NAME"

private fun logKey(fileName: String) = LOG_KEY_PREFIX + fileName

/**
 * Appends by reading the key, adding a line and writing it back - which is what `localStorage` gives
 * and is affordable because log lines are rare (a handful per sync round). A write per line is also
 * the point: the tab that gets discarded or reloaded is the one whose log is worth having, and
 * anything held in memory until later would be lost with it.
 */
private class LocalStorageLogFileSink(fileName: String) : LogFileSink {
    private val key = logKey(fileName)

    /** The file name alone, which is what a report is allowed to ask to read back. */
    override val location: String = fileName

    override fun appendLine(line: String) {
        val stored = localStorage.getItem(key).orEmpty() + line + "\n"
        // Trimmed from the front, so what is kept is the end - which is the part that explains an
        // ending. Whole lines only, so the log never starts halfway through one.
        val kept = if (stored.length <= MAX_WEB_LOG_CHARS) {
            stored
        } else {
            stored.takeLast(MAX_WEB_LOG_CHARS).substringAfter('\n', "")
        }
        localStorage.setItem(key, kept)
    }
}

/** The last-run record, in the one place a browser has that survives the page going away. */
private object LocalStorageLastRunStore : LastRunStore {
    override fun write(text: String) {
        runCatching { localStorage.setItem(LAST_RUN_KEY, text) }
    }

    override fun read(): String? = runCatching { localStorage.getItem(LAST_RUN_KEY) }.getOrNull()
}
