package eu.heha.conifer.log

import eu.heha.conifer.AppJson
import eu.heha.conifer.buildLabel
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * What the run that crashed leaves behind for the run after it: which build it was, when it went
 * down and what took it - the short version of the log file's last line, in the one place the next
 * run is willing to look.
 *
 * It exists because a log file nobody opens answers nothing. The next start reads this one small
 * file, and that is what lets the app say "the last run ended in an error" instead of waiting for
 * someone to go looking in a folder they have never heard of ([CrashBreadcrumbStore]).
 *
 * Deliberately small and deliberately not the log: it is written on the crashing thread, on its way
 * out, where the only affordable write is a short one. Everything here is either already in the log
 * file or a pointer to it ([logFile]).
 */
@Serializable
data class CrashBreadcrumb(
    /** The build it happened in - `buildLabel()`, so a report names a commit and not a month. */
    val buildLabel: String,
    val atEpochMillis: Long,
    /** Where it surfaced: a thread name, `native` - [UncaughtError.origin]. */
    val origin: String,
    /** The exception's class, `null` where the platform reported the crash in words instead. */
    val type: String? = null,
    val message: String? = null,
    /** The top [MAX_CRASH_FRAMES] stack frames, which is as much as names the fault. */
    val frames: List<String> = emptyList(),
    /** The log file of the run that crashed - where everything leading up to it is. */
    val logFile: String? = null,
) {
    val at: Instant get() = Instant.fromEpochMilliseconds(atEpochMillis)
}

/**
 * How many stack frames the breadcrumb keeps. Enough to say where the fault is, few enough that
 * writing it stays one short write - the whole trace is in the log file anyway, and this is written
 * by a process with seconds to live.
 */
internal const val MAX_CRASH_FRAMES = 8

/**
 * The breadcrumb for [error], with the same redaction every log line goes through ([redactSecrets]):
 * an exception message quoting the request that failed is as capable of carrying a token here as it
 * is there, and this one is built to be handed to somebody else.
 */
internal fun crashBreadcrumb(
    error: UncaughtError,
    at: Instant,
    logFile: String?,
    buildLabel: String = buildLabel(),
): CrashBreadcrumb = CrashBreadcrumb(
    buildLabel = buildLabel,
    atEpochMillis = at.toEpochMilliseconds(),
    origin = error.origin,
    type = error.throwable?.let { it::class.simpleName },
    // The platform's own wording where there is no throwable to ask (see UncaughtError.message).
    message = (error.throwable?.message ?: error.message)?.let { redactSecrets(it) },
    frames = error.throwable?.topFrames().orEmpty(),
    logFile = logFile,
)

/**
 * The top frames of this throwable's trace, as text.
 *
 * Taken off [stackTraceToString] rather than off a structured trace, because a structured one is
 * not the same thing on every target - and its first line is the type and message, which
 * [crashBreadcrumb] already has from the throwable itself.
 */
private fun Throwable.topFrames(): List<String> = stackTraceToString()
    .lineSequence()
    .drop(1)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .take(MAX_CRASH_FRAMES)
    .map { redactSecrets(it) }
    .toList()

/**
 * Stores [breadcrumb] as the crash the next run will hear about, replacing any older one.
 *
 * Called from the crash path and so silent about its own failures: this runs after the log line has
 * already been written ([logUncaughtError]), and a breadcrumb that cannot be stored costs a banner,
 * while a crash handler that throws costs the report entirely.
 */
internal fun writeCrashBreadcrumb(store: CrashBreadcrumbStore?, breadcrumb: CrashBreadcrumb) {
    store ?: return
    runCatching { store.write(AppJson.encodeToString(breadcrumb)) }
}

/**
 * The breadcrumb a previous run left, or null where there is none - and also where there is one
 * that cannot be read. Nothing stored here is worth failing a start over: the file is written by a
 * process on its way out, so a truncated or half-written one is a thing that happens, and an older
 * version of the app is entitled to have written a shape this one no longer knows.
 */
fun readCrashBreadcrumb(store: CrashBreadcrumbStore?): CrashBreadcrumb? {
    store ?: return null
    return runCatching {
        store.read()?.let { AppJson.decodeFromString<CrashBreadcrumb>(it) }
    }.getOrNull()
}

/**
 * The crash the previous run left behind, the report made out of it, and the ability to forget it.
 * One instance, built at startup by `ConiferApp` and injected from there.
 *
 * [lastCrash] is read once rather than watched: the only writer is a run that is over, so it cannot
 * change while this one is looking at it. Read at startup and not on demand for a second reason -
 * this run's own crash would overwrite it, and the banner would then report the crash the user is
 * still in rather than the one before it.
 */
class CrashReports(
    private val store: CrashBreadcrumbStore? = null,
    val lastCrash: CrashBreadcrumb? = null,
    private val logFiles: LogTailReader? = null,
    /** How the app names itself and this device - `coniferUserAgent`, as the log's header uses. */
    private val userAgent: String? = null,
) {
    /**
     * The whole report for [lastCrash] - the summary and the end of the log file of the run that
     * crashed ([crashReportText]) - or null when there is no crash to report.
     *
     * Reads a file, so it is not for the main thread. A log file that has since been pruned or
     * cannot be read costs the report its second half and nothing else.
     */
    fun report(): String? {
        val lastCrash = lastCrash ?: return null
        val logTail = logTailFileName(lastCrash.logFile)?.let { fileName ->
            runCatching { logFiles?.readLogTail(fileName, MAX_LOG_TAIL_CHARS) }.getOrNull()
        }
        return crashReportText(lastCrash, userAgent = userAgent, logTail = logTail)
    }

    /** What that report is called where it is shared as a file; null with no crash to report. */
    fun reportFileName(): String? = lastCrash?.let { crashReportFileName(it) }

    /**
     * Drops the stored crash, so the next start says nothing. What dismissing the banner does - the
     * log file it points at stays where it is, and is pruned in its own time.
     */
    fun forget() {
        runCatching { store?.clear() }
    }
}
