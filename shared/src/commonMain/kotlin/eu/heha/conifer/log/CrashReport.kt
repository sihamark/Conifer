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
 * The crash as something to hand over: the build it happened in, the device, the error itself, and
 * then the log of the run that crashed - which is the part that says what the app had been doing.
 *
 * Nothing here is anything the user wrote. A bit's text never reaches a log line, and every line
 * that does went through [redactSecrets] on its way to the file, so what a report carries is what
 * the app said about itself. That is the promise the banner makes on this function's behalf.
 *
 * [logTail] is the end of that log file (see [LogTailReader]); a report is still worth sending
 * without it, since the summary above names the build and the error either way.
 */
fun crashReportText(
    breadcrumb: CrashBreadcrumb,
    userAgent: String? = null,
    logTail: String? = null,
): String = buildString {
    appendLine("Conifer crash report")
    appendLine("build  ${breadcrumb.buildLabel}")
    userAgent?.let { appendLine("device $it") }
    appendLine("when   ${breadcrumb.at}")
    appendLine("where  ${breadcrumb.origin}")
    val error = listOfNotNull(breadcrumb.type, breadcrumb.message).joinToString(": ")
    appendLine("error  ${error.ifEmpty { "unknown" }}")
    breadcrumb.frames.forEach { appendLine("       $it") }
    breadcrumb.logFile?.let { appendLine("log    $it") }
    if (logTail.isNullOrBlank()) return@buildString
    appendLine()
    // Said out loud, because a reader who cannot see the top of the file would otherwise take the
    // first line they do see for the start of the run - and read a truncated log as a short one.
    appendLine(
        if (logTail.length < MAX_LOG_TAIL_CHARS) {
            "--- the log of the run that crashed ---"
        } else {
            "--- the log of the run that crashed, last $MAX_LOG_TAIL_CHARS characters ---"
        }
    )
    append(logTail)
}

/**
 * What a shared report is called: `conifer-crash-2026-07-28_143201.txt`, after the moment it
 * reports rather than the moment it was shared - two shares of the same crash are the same file,
 * and two crashes never collide.
 */
fun crashReportFileName(
    breadcrumb: CrashBreadcrumb,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = "conifer-crash-" +
        breadcrumb.at.toLocalDateTime(timeZone).format(REPORT_NAME_FORMAT) +
        ".txt"

/** As [logFileName] spells a moment, so that a report and the log it quotes sort alike. */
private val REPORT_NAME_FORMAT = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char('_')
    hour(); minute(); second()
}
