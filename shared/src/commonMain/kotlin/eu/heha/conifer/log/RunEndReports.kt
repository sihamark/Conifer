package eu.heha.conifer.log

/**
 * How the run before this one ended, the report made out of it, and the ability to forget it. One
 * instance, built at startup by `ConiferApp` and injected from there.
 *
 * [lastEnd] is worked out once rather than watched: the run it describes is over, so nothing about
 * it can change while this one looks at it. At startup and not on demand for a second reason - this
 * run writes its own record over the one being read, and asking later would get an answer about the
 * run the user is still in.
 */
class RunEndReports(
    private val store: LastRunStore? = null,
    val lastEnd: LastRunEnd? = null,
    private val logFiles: LogTailReader? = null,
    /** How the app names itself and this device - `coniferUserAgent`, as the log's header uses. */
    private val userAgent: String? = null,
    /** This run's own log file, kept in the record so that the next start knows where to look. */
    private val runningLogFile: String? = null,
) {
    /**
     * The whole report for [lastEnd] - what is known about the ending, and the end of the log file
     * of the run it happened to ([runEndReportText]) - or null when there is nothing to report.
     *
     * Reads a file, so it is not for the main thread. A log that has since been pruned or cannot be
     * read costs the report its second half and nothing else.
     */
    fun report(): String? {
        val lastEnd = lastEnd ?: return null
        val logTail = logTailFileName(lastEnd.logFile)?.let { fileName ->
            runCatching { logFiles?.readLogTail(fileName, MAX_LOG_TAIL_CHARS) }.getOrNull()
        }
        return runEndReportText(lastEnd, userAgent = userAgent, logTail = logTail)
    }

    /** What that report is called where it is shared as a file; null with nothing to report. */
    fun reportFileName(): String? = lastEnd?.let { runEndReportFileName(it) }

    /**
     * Drops the ending from the stored record, so the next start says nothing about it - what
     * dismissing the banner does. This run's own log file stays named there, since the next start
     * still has to know which log to look at; the log files themselves are untouched and are pruned
     * in their own time.
     */
    fun forget() {
        writeLastRun(store, LastRunRecord(runningLogFile = runningLogFile))
    }
}
