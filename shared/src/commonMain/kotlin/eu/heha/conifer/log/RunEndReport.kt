package eu.heha.conifer.log

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 * How much of the crashed run's log a report carries. Generous, because the lines before a crash are
 * the ones that explain it and a run log is rarely anywhere near this long - but bounded, because a
 * report is something a person sends from a phone, and the whole of a week-long desktop session is
 * not that.
 */
const val MAX_LOG_TAIL_CHARS = 100_000

/**
 * The ending as something to hand over: what is known about how the run stopped, the device, and
 * then the log of that run - which is the part that says what the app had been doing.
 *
 * Nothing here is anything the user wrote. A bit's text never reaches a log line, and every line
 * that does went through [redactSecrets] on its way to the file, so what a report carries is what
 * the app said about itself. That is the promise the banner makes on this function's behalf.
 *
 * [logTail] is the end of that log file (see [LogTailReader]). A crash is still worth reporting
 * without it, since the summary names the build and the error either way; a run that vanished is
 * very nearly the log alone, which is exactly why the report carries it.
 */
fun runEndReportText(
    end: LastRunEnd,
    userAgent: String? = null,
    logTail: String? = null,
): String = buildString {
    when (end) {
        is LastRunEnd.Crashed -> appendCrash(end.breadcrumb, userAgent)
        is LastRunEnd.Vanished -> appendVanish(end.run, userAgent)
    }
    if (logTail.isNullOrBlank()) return@buildString
    appendLine()
    // Said out loud, because a reader who cannot see the top of the file would otherwise take the
    // first line they do see for the start of the run - and read a truncated log as a short one.
    appendLine(
        if (logTail.length < MAX_LOG_TAIL_CHARS) {
            "--- the log of the run that ended ---"
        } else {
            "--- the log of the run that ended, last $MAX_LOG_TAIL_CHARS characters ---"
        }
    )
    append(logTail)
}

/** The half of the report that a crash can fill in, because something was still running to write it. */
private fun StringBuilder.appendCrash(breadcrumb: CrashBreadcrumb, userAgent: String?) {
    appendLine("Conifer crash report")
    appendLine("build  ${breadcrumb.buildLabel}")
    userAgent?.let { appendLine("device $it") }
    appendLine("when   ${breadcrumb.at}")
    appendLine("where  ${breadcrumb.origin}")
    val error = listOfNotNull(breadcrumb.type, breadcrumb.message).joinToString(": ")
    appendLine("error  ${error.ifEmpty { "unknown" }}")
    breadcrumb.frames.forEach { appendLine("       $it") }
    breadcrumb.logFile?.let { appendLine("log    $it") }
}

/**
 * The same header for a run that left no note - which is most of what there is to say about it, and
 * why the log below matters more here than it does for a crash.
 *
 * No build line: nothing was left running to write one down, and this run's own build would be a
 * guess about the last one's. The log names it in its second line, which is where a reader looks.
 */
private fun StringBuilder.appendVanish(run: VanishedRun, userAgent: String?) {
    appendLine("Conifer run report")
    appendLine("what   the run ended without a word - no crash was recorded")
    userAgent?.let { appendLine("device $it") }
    run.startedAt?.let { appendLine("when   the run started $it") }
    run.logFile?.let { appendLine("log    $it") }
}

/**
 * What a shared report is called: `conifer-crash-2026-07-28_143201.txt`, after the moment it
 * reports rather than the moment it was shared - two shares of the same ending are the same file,
 * and two endings never collide.
 *
 * A run that vanished is named after when it *started*, that being the only moment anybody knows.
 */
fun runEndReportFileName(
    end: LastRunEnd,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val at = when (end) {
        is LastRunEnd.Crashed -> end.breadcrumb.at
        is LastRunEnd.Vanished -> end.run.startedAt
    }
    val kind = if (end is LastRunEnd.Crashed) "crash" else "run"
    val moment = at?.toLocalDateTime(timeZone)?.format(REPORT_NAME_FORMAT)
        // A vanished run whose log file name did not parse leaves nothing to name the file after.
        ?: "unknown-time"
    return "conifer-$kind-$moment.txt"
}

/** As [logFileName] spells a moment, so that a report and the log it quotes sort alike. */
private val REPORT_NAME_FORMAT = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char('_')
    hour(); minute(); second()
}
