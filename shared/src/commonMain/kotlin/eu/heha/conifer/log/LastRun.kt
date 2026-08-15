package eu.heha.conifer.log

import eu.heha.conifer.AppJson
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The one thing an app run leaves behind for the next one: which log file it is writing, whatever it
 * managed to say on its way out, and whatever the run before it left that the user has not dismissed
 * yet. Stored as JSON in [LastRunStore].
 *
 * [runningLogFile] is what makes an ending detectable at all. A crash writes [crash] and is thereby
 * explained; every other way a run can end - a phone killing a backgrounded app for memory, a
 * process killed outright, a machine losing power - leaves nothing behind by definition, so the next
 * start has to go and look at the log file the last one was writing and see how it stops
 * ([lastRunEnd]).
 */
@Serializable
data class LastRunRecord(
    /** The log file of the run that wrote this record, by name - see [logTailFileName]. */
    val runningLogFile: String? = null,
    /** What the crash handler wrote as the app went down, if it got that far. */
    val crash: CrashBreadcrumb? = null,
    /** A run that ended without a word, carried until the user has been told about it. */
    val vanish: VanishedRun? = null,
    /**
     * Whether this run has said goodbye and not taken it back - see [writeLogClosed], which is also
     * why this is here rather than read off the end of the log.
     */
    val isLogClosed: Boolean = false,
)

/**
 * A run that stopped without saying so: no crash to report and no [LOG_CLOSED_MARKER] at the end of
 * its log.
 *
 * There is no build and no error here because there was nobody left to write either down - that is
 * what makes this its own kind of ending. What it has instead is the log file, which was written as
 * the run went along and does name the build (its second line, see `buildLabel`).
 */
@Serializable
data class VanishedRun(
    val logFile: String? = null,
    /** When that run started, off its log file's name; null where the name did not say. */
    val startedAtEpochMillis: Long? = null,
) {
    val startedAt: Instant? get() = startedAtEpochMillis?.let { Instant.fromEpochMilliseconds(it) }
}

/** How the run before this one ended, as far as this one can tell. */
sealed interface LastRunEnd {
    /** The log file of that run, which a report carries the end of. */
    val logFile: String?

    /** It crashed, and said so: an uncaught error reached [logUncaughtError] and was written down. */
    data class Crashed(val breadcrumb: CrashBreadcrumb) : LastRunEnd {
        override val logFile: String? get() = breadcrumb.logFile
    }

    /**
     * It stopped without a word. Which of the several ways that can happen is not knowable from
     * here - the app was killed for memory, the phone rebooted, a native crash took the process
     * before any Kotlin handler could run - and the banner says as much rather than guessing.
     */
    data class Vanished(val run: VanishedRun) : LastRunEnd {
        override val logFile: String? get() = run.logFile
    }
}

/**
 * How much of the previous log has to be read to see how it ends. Only the last line matters, and a
 * line is a line: this is generous enough for a stack trace's worth of overshoot.
 */
private const val LOG_ENDING_CHARS = 2_000

/**
 * How the run described by [record] ended, or null where there is nothing to report - which is the
 * ordinary case, and includes a first ever start (no record) and a run that closed its log.
 *
 * In order: a crash explains itself; an ending already worked out and not yet dismissed is carried
 * on unchanged, so that restarting before getting round to reporting it loses nothing; a run that
 * said goodbye ([LastRunRecord.isLogClosed]) was put away, and being killed after that is ordinary;
 * and failing all three, the log file the last run was writing is read to see whether it stops
 * mid-sentence.
 */
fun lastRunEnd(record: LastRunRecord?, logFiles: LogTailReader?): LastRunEnd? {
    record ?: return null
    record.crash?.let { return LastRunEnd.Crashed(it) }
    record.vanish?.let { return LastRunEnd.Vanished(it) }
    if (record.isLogClosed) return null
    val logFile = logTailFileName(record.runningLogFile) ?: return null
    val ending = runCatching { logFiles?.readLogTail(logFile, LOG_ENDING_CHARS) }.getOrNull()
    // A log that cannot be read is not evidence of anything; it is pruned, or it was never written.
    if (ending == null || lastRunEndedQuietly(ending)) return null
    return LastRunEnd.Vanished(
        VanishedRun(
            logFile = logFile,
            startedAtEpochMillis = logFileStartTime(logFile)?.toEpochMilliseconds(),
        )
    )
}

/**
 * Whether a log ending in [tail] belongs to a run that said goodbye - the [LOG_CLOSED_MARKER] its
 * last line, put there by the platform's [eu.heha.conifer.AppPresenceInitializer].
 *
 * Only the fallback for a record written before [LastRunRecord.isLogClosed] existed, and only ever
 * forgiving: a phone app that has been put away is not stopped, and anything it does in the
 * background afterwards writes lines after the marker, which is why this question is no longer the
 * one that decides.
 */
fun lastRunEndedQuietly(tail: String): Boolean =
    tail.trimEnd().lines().lastOrNull()?.endsWith(LOG_CLOSED_MARKER) == true

/** The record [store] holds, or null where there is none or none that can be read. */
fun readLastRun(store: LastRunStore?): LastRunRecord? {
    store ?: return null
    return runCatching {
        store.read()?.let { AppJson.decodeFromString<LastRunRecord>(it) }
    }.getOrNull()
}

/**
 * Replaces what [store] holds with [record]. Silent about its own failures: this is called both from
 * an ordinary start and from the crash path, and on that path a record that cannot be stored costs a
 * banner while a handler that throws costs the report entirely.
 */
fun writeLastRun(store: LastRunStore?, record: LastRunRecord) {
    store ?: return
    runCatching { store.write(AppJson.encodeToString(record)) }
}

/**
 * Notes in [store] that this run has said goodbye ([isClosed]) or taken it back, leaving the rest of
 * the record - this run's log file, an ending nobody has dismissed yet - as it was.
 *
 * Here rather than only at the end of the log because a phone app that is put away is not stopped:
 * it keeps its process, and whatever it goes on doing there - a sync round every few minutes, a
 * draft saving itself - writes lines after the marker. Reading the log's last line then said
 * "vanished" about the most ordinary ending there is. The marker still goes into the log for
 * whoever reads it; this is what the next start decides on.
 */
fun writeLogClosed(store: LastRunStore?, isClosed: Boolean) {
    store ?: return
    val record = readLastRun(store) ?: return
    writeLastRun(store, record.copy(isLogClosed = isClosed))
}
