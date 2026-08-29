package eu.heha.conifer.log

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Mirrors everything logged through [io.github.aakira.napier.Napier] into this run's log file,
 * timestamped and with secrets stripped ([redactSecrets]).
 *
 * Installed alongside (not instead of) the platform's console antilog, and unlike
 * `DebugAntilog` it's safe in release builds - it never derives anything from a stack trace, so
 * R8 can't break it.
 *
 * Log calls happen wherever they happen, including the main thread; file writes must not. So
 * [performLog] only hands the formatted line to an unbounded [Channel] that a single coroutine on
 * [scope] drains into [sink] - which also keeps lines in order without locking.
 */
class FileAntilog(
    private val sink: LogFileSink,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Antilog() {

    private val lines = Channel<String>(Channel.UNLIMITED)

    /** Where the lines are going - what a crash breadcrumb points a reader at ([CrashBreadcrumb]). */
    internal val logFileLocation: String get() = sink.location

    init {
        scope.launch {
            for (line in lines) {
                // A log file that can't be written is a lost log line, never a crash.
                runCatching { sink.appendLine(line) }
            }
        }
    }

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?
    ) {
        lines.trySend(
            formatLogLine(
                at = clock.now(),
                priority = priority,
                tag = tag,
                throwable = throwable,
                message = message,
                timeZone = timeZone,
            )
        )
    }

    /**
     * Ends the log with [LOG_CLOSED_MARKER]: the run is at a point where being killed is ordinary
     * (see [eu.heha.conifer.LogClosingInitializer]), and this is what the next start reads to tell
     * an ordinary ending from a run that simply stopped ([lastRunEndedQuietly]).
     *
     * Blocking, for the same reason the crash line is: on a desktop this runs in a shutdown hook,
     * where the queue's other end will not be scheduled again.
     */
    internal fun closeLog() {
        logBlocking(LogLevel.INFO, tag = null, throwable = null, message = LOG_CLOSED_MARKER)
    }

    /** Undoes [closeLog] by writing after it - the app is back, and being killed now means something. */
    internal fun reopenLog() {
        logBlocking(LogLevel.INFO, tag = null, throwable = null, message = LOG_REOPENED_MARKER)
    }

    /**
     * Writes one line - and everything still queued ahead of it, so it lands in context - to [sink]
     * on the *calling* thread. Giving up the queue is the point: this is for the moment before the
     * app goes down (see [logUncaughtError]), which has no later in which a coroutine could run.
     *
     * Best effort in both directions. A queued line the drain coroutine has already taken is its to
     * write and may still be lost with the process, and [sink] now sees writes from two threads -
     * which is why a [LogFileSink] appends per line rather than holding an open writer.
     */
    internal fun logBlocking(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        while (true) {
            val queued = lines.tryReceive().getOrNull() ?: break
            runCatching { sink.appendLine(queued) }
        }
        val line = formatLogLine(
            at = clock.now(),
            priority = priority,
            tag = tag,
            throwable = throwable,
            message = message,
            timeZone = timeZone,
        )
        runCatching { sink.appendLine(line) }
    }
}

/**
 * One log file line: `2026-07-28 14:32:01.482 I  pull: 3 buckets changed`.
 *
 * A throwable is spelled out in full (stack trace) only from [LogLevel.ERROR] up - the sync stack
 * warns about plenty of retried, self-healing conditions, and a stack trace per retry buries the
 * one entry that actually matters.
 */
internal fun formatLogLine(
    at: Instant,
    priority: LogLevel,
    tag: String?,
    throwable: Throwable?,
    message: String?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = buildString {
    append(at.toLocalDateTime(timeZone).format(TIMESTAMP_FORMAT))
    append(' ')
    append(priority.symbol)
    append("  ")
    if (tag != null) {
        append('[').append(tag).append("] ")
    }
    if (!message.isNullOrEmpty()) {
        append(redactSecrets(message))
    }
    if (throwable != null) {
        if (!message.isNullOrEmpty()) append(" | ")
        val detail = if (priority >= LogLevel.ERROR) {
            throwable.stackTraceToString().trimEnd()
        } else {
            "${throwable::class.simpleName}: ${throwable.message}"
        }
        append(redactSecrets(detail))
    }
}

private val LogLevel.symbol: Char
    get() = when (this) {
        LogLevel.VERBOSE -> 'V'
        LogLevel.DEBUG -> 'D'
        LogLevel.INFO -> 'I'
        LogLevel.WARNING -> 'W'
        LogLevel.ERROR -> 'E'
        LogLevel.ASSERT -> 'A'
    }

private val TIMESTAMP_FORMAT = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute(); char(':'); second()
    char('.'); secondFraction(3)
}
