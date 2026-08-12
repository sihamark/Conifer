package eu.heha.conifer.log

import eu.heha.conifer.buildLabel
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * What the run that crashed leaves behind for the run after it: which build it was, when it went
 * down and what took it - the short version of the log file's last line, in the one place the next
 * run is willing to look ([LastRunStore]).
 *
 * It exists because a log file nobody opens answers nothing. The next start reads this one small
 * file, and that is what lets the app say "the last run ended in an error" instead of waiting for
 * someone to go looking in a folder they have never heard of.
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
