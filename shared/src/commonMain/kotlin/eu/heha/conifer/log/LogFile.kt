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
 * How many run log files a platform keeps around: the newest [MAX_LOG_FILES] survive, older ones
 * are deleted when a new run starts. Without this, "a new log file per app start" would grow
 * without bound.
 */
const val MAX_LOG_FILES = 10

/** Prefix every run log file shares - the platforms prune by it, so nothing else may use it. */
const val LOG_FILE_PREFIX = "conifer-"

const val LOG_FILE_SUFFIX = ".log"

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
