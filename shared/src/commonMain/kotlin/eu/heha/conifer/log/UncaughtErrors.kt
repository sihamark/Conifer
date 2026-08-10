package eu.heha.conifer.log

import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier

/**
 * An error that reached the top of a thread, a coroutine or an event loop with nothing left to catch
 * it - what a platform's [eu.heha.conifer.UncaughtErrorInitializer] hands over on its way to letting
 * the app go down.
 */
data class UncaughtError(
    /** Where it surfaced, written into the log line's tag: a thread name, `native`. */
    val origin: String,
    val throwable: Throwable?,
    /**
     * What happened, for a platform that reports that in words rather than as a [throwable]. Null
     * otherwise: the throwable says it better, and at [LogLevel.ERROR] the log spells out its whole
     * stack trace.
     */
    val message: String? = null,
)

/**
 * Writes [error] into this run's log file - as a rule the last line that file will ever get, since
 * the process is on its way out. That's why the line goes to disk on the calling thread
 * ([FileAntilog.logBlocking]) rather than through the queue that normally carries log lines: the
 * queue's other end needs a coroutine to get scheduled, and there may be no next scheduling.
 *
 * Everything here is best effort and nothing here throws. A crash handler that crashes turns a
 * reported bug into an unreported one, and a log file that can't be written - a full disk is a
 * plausible cause of the crash in the first place - is not worth ending a second run over.
 */
internal fun logUncaughtError(error: UncaughtError, fileAntilog: FileAntilog?) {
    val message = "uncaught error" + (error.message?.let { ": $it" } ?: "")
    runCatching {
        if (fileAntilog == null) {
            // No log file on this platform - the browser has no file system to keep one in - so the
            // console that the platform prints its own crash to is all there is to write to.
            Napier.e(throwable = error.throwable, tag = error.origin) { message }
        } else {
            fileAntilog.logBlocking(
                priority = LogLevel.ERROR,
                tag = error.origin,
                throwable = error.throwable,
                message = message,
            )
        }
    }
}
