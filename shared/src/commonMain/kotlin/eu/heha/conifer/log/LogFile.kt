package eu.heha.conifer.log

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The one open log file of the current app run, created by a platform's
 * [eu.heha.conifer.LogFileInitializer] and written through by [FileAntilog].
 *
 * Implementations are called from a single background coroutine - with one exception, an uncaught
 * error being written on the crashing thread ([FileAntilog.logBlocking]), so an append has to
 * tolerate one from elsewhere landing beside it. Appending per line, rather than holding an open
 * writer, is what both makes that safe and keeps a killed run's lines from sitting in a buffer.
 *
 * They also need to survive a failed write (a full disk, a revoked folder): losing log lines is
 * acceptable, taking the app down over them is not.
 */
interface LogFileSink {
    /** Where the file is - written into the log's own header, and what a bug report needs. */
    val location: String

    /** Appends [line] and a line break. [line] may itself span several lines (a stack trace). */
    fun appendLine(line: String)
}

/**
 * The one small file beside the run logs that holds the [CrashBreadcrumb] of the run that crashed -
 * created by a platform's [eu.heha.conifer.LogFileInitializer], written on the crashing thread and
 * read once at the next start.
 *
 * At most one crash is kept, the newest, so a write replaces rather than appends: the banner asks
 * about the run that just ended, and a device that crashes repeatedly must not accumulate.
 *
 * Implementations survive their own failures - an unwritable folder, a file half written by a
 * process that was killed - rather than throwing. A crash handler that fails writes off the report
 * it was in the middle of making, and a start that fails over an unreadable breadcrumb turns a
 * crashed run into two.
 */
interface CrashBreadcrumbStore {
    /** Replaces whatever is stored with [text]. Called on the thread that is crashing. */
    fun write(text: String)

    /** What [write] last stored, or null where there is nothing (or nothing readable). */
    fun read(): String?

    /** Forgets the stored crash, so the next start has nothing to report. */
    fun clear()
}

/**
 * How many run log files a platform keeps around: the newest [MAX_LOG_FILES] survive, older ones
 * are deleted when a new run starts. Without this, "a new log file per app start" would grow
 * without bound.
 */
const val MAX_LOG_FILES = 10

/** Prefix every run log file shares - the platforms prune by it, so nothing else may use it. */
const val LOG_FILE_PREFIX = "conifer-"

const val LOG_FILE_SUFFIX = ".log"

/**
 * What the platforms call the [CrashBreadcrumbStore]'s file, in the same folder as the run logs.
 *
 * Deliberately not starting with [LOG_FILE_PREFIX]: that prefix is what pruning deletes by, and a
 * breadcrumb named like a log would be swept away by the very start that was meant to read it.
 */
const val CRASH_BREADCRUMB_FILE_NAME = "last-crash.json"

/**
 * `conifer-2026-07-28_143201.log`. Named after the *local* start time, so that an alphabetical
 * listing of the folder is also chronological - that's what the platform initializers prune by,
 * rather than a file timestamp they'd each have to query differently. (A backwards clock or a
 * timezone change can therefore mis-order the pruning; the cost of that is deleting a slightly
 * wrong old log, which isn't worth more machinery.)
 */
fun logFileName(startedAt: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    LOG_FILE_PREFIX + startedAt.toLocalDateTime(timeZone).format(FILE_NAME_FORMAT) + LOG_FILE_SUFFIX

private val FILE_NAME_FORMAT = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char('_')
    hour(); minute(); second()
}
