package eu.heha.conifer.log

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
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
 * The last line of a log file whose run ended the way runs are supposed to - see
 * [eu.heha.conifer.LogClosingInitializer] for when that is, and [lastRunEndedQuietly] for what its
 * absence is then taken to mean.
 */
const val LOG_CLOSED_MARKER = "--- log closed ---"

/** What [LOG_CLOSED_MARKER] is undone by, when the app comes back rather than going away. */
const val LOG_REOPENED_MARKER = "--- log reopened ---"

/**
 * Reads a run's log file back out of the platform's log folder - the other direction of
 * [LogFileSink], and what turns a crash report from a summary into something that can be read.
 *
 * By file name rather than by path, and the name is resolved against the log folder by whoever
 * implements this. That is the safety property: the name a report is built from comes off a file on
 * disk ([CrashBreadcrumb.logFile]), and a name is a great deal harder to point somewhere else than
 * a path is (see [logTailFileName], which is what checks it).
 */
fun interface LogTailReader {
    /**
     * The last [maxChars] characters of the log file [fileName], or null where there is no such
     * file, it cannot be read, or this platform keeps no logs at all. Never throws.
     */
    fun readLogTail(fileName: String, maxChars: Int): String?
}

/**
 * The one small file beside the run logs that holds the [LastRunRecord] - created by a platform's
 * [eu.heha.conifer.LogFileInitializer], written at every start and again on the crashing thread if a
 * run ends that way, and read once at the next start.
 *
 * One record is kept, the newest, so a write replaces rather than appends: the banner asks about the
 * run that just ended, and a device that crashes repeatedly must not accumulate.
 *
 * Implementations survive their own failures - an unwritable folder, a file half written by a
 * process that was killed - rather than throwing. A crash handler that fails writes off the report
 * it was in the middle of making, and a start that fails over an unreadable record turns a crashed
 * run into two.
 */
interface LastRunStore {
    /**
     * Replaces whatever is stored with [text]. Also called on the thread that is crashing.
     *
     * The only way the record ever changes, deletion included: forgetting an ending is writing the
     * record back without it ([RunEndReports.forget]), because this run's log file has to stay named
     * in it either way.
     */
    fun write(text: String)

    /** What [write] last stored, or null where there is nothing (or nothing readable). */
    fun read(): String?
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
 * What the platforms call the [LastRunStore]'s file, in the same folder as the run logs.
 *
 * Deliberately not starting with [LOG_FILE_PREFIX]: that prefix is what pruning deletes by, and a
 * record named like a log would be swept away by the very start that was meant to read it.
 */
const val LAST_RUN_FILE_NAME = "last-run.json"

/**
 * `conifer-2026-07-28_143201.log`. Named after the *local* start time, so that an alphabetical
 * listing of the folder is also chronological - that's what the platform initializers prune by,
 * rather than a file timestamp they'd each have to query differently. (A backwards clock or a
 * timezone change can therefore mis-order the pruning; the cost of that is deleting a slightly
 * wrong old log, which isn't worth more machinery.)
 */
fun logFileName(startedAt: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    LOG_FILE_PREFIX + startedAt.toLocalDateTime(timeZone).format(FILE_NAME_FORMAT) + LOG_FILE_SUFFIX

/**
 * The name of the log file at [path], if [path] names one - the file name a [LogTailReader] may be
 * asked for, and null for anything else.
 *
 * Everything but the last path segment is dropped (on either kind of separator, since a Windows
 * desktop writes the other one), and what is left has to look like a name [logFileName] produced.
 * A report is built from a path stored in a file, and a file on disk is not a promise: this is what
 * keeps `conifer-../../../.ssh/id_rsa.log` from being a thing the app will read out and hand to
 * whoever the report goes to.
 */
fun logTailFileName(path: String?): String? {
    val name = path?.substringAfterLast('/')?.substringAfterLast('\\') ?: return null
    val isALogFileName = name.startsWith(LOG_FILE_PREFIX) &&
            name.endsWith(LOG_FILE_SUFFIX) &&
            ".." !in name
    return name.takeIf { isALogFileName }
}

/**
 * When the run that wrote [fileName] started, out of the name itself - which is how a run that left
 * nothing else behind still gets a "when" in the report about it ([VanishedRun]).
 *
 * In the local time zone, because that is the one [logFileName] wrote it in. A machine whose zone has
 * changed since therefore reads an hour or two off; that is the cost of a name that sorts
 * chronologically in a folder listing, and it buys a great deal more than it costs.
 */
fun logFileStartTime(
    fileName: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Instant? = runCatching {
    LocalDateTime.parse(
        input = fileName.removePrefix(LOG_FILE_PREFIX).removeSuffix(LOG_FILE_SUFFIX),
        format = FILE_NAME_FORMAT,
    ).toInstant(timeZone)
}.getOrNull()

private val FILE_NAME_FORMAT = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char('_')
    hour(); minute(); second()
}
