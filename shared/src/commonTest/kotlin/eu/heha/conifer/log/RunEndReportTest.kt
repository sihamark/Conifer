package eu.heha.conifer.log

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The report is what actually gets sent, so it has to answer the two questions a maintainer asks in
 * order: which build ended how, and what had the app been doing. The first comes from whatever the
 * run left behind, the second from its log file - and the second is the reason this is a file to
 * share rather than a line to read out.
 */
class RunEndReportTest {

    @Test
    fun namesTheBuildTheDeviceTheErrorAndTheLog() {
        val text = runEndReportText(CRASHED, userAgent = "Conifer (Android 36)")

        assertContains(text, BUILD_LABEL)
        assertContains(text, "Conifer (Android 36)")
        assertContains(text, "2026-07-28T12:34:56.789Z")
        assertContains(text, "IllegalStateException: boom")
        assertContains(text, "at eu.heha.conifer.sync.SyncEngine.push(SyncEngine.kt:214)")
        assertContains(text, LOG_PATH)
    }

    /** The lines leading up to a bad ending are the ones that explain it. */
    @Test
    fun carriesTheLogOfTheRunThatEnded() {
        val text = runEndReportText(CRASHED, logTail = "12:34:50.001 I  pull: 3 buckets changed")

        assertContains(text, "--- the log of the run that ended ---")
        assertContains(text, "pull: 3 buckets changed")
    }

    /**
     * A reader who cannot see the top of the file would otherwise take the first line they do see
     * for the start of the run, and read a cut log as a short one.
     */
    @Test
    fun saysSoWhenTheLogWasCutShort() {
        val text = runEndReportText(CRASHED, logTail = "x".repeat(MAX_LOG_TAIL_CHARS))

        assertContains(text, "last $MAX_LOG_TAIL_CHARS characters")
    }

    /** A log that has since been pruned costs the report its second half and nothing else. */
    @Test
    fun isStillWorthSendingWithoutTheLog() {
        val text = runEndReportText(CRASHED, logTail = null)

        assertContains(text, "IllegalStateException: boom")
        assertTrue("the log of the run" !in text, "an empty log section was written anyway: $text")
    }

    /**
     * A run that vanished has no build and no error to give, because nothing was left running to
     * write either down - and the report says that rather than guessing at this run's build.
     */
    @Test
    fun saysWhatLittleIsKnownAboutARunThatVanished() {
        val text = runEndReportText(VANISHED, userAgent = "Conifer (iOS 26.4)", logTail = "a line")

        assertContains(text, "ended without a word")
        assertContains(text, "Conifer (iOS 26.4)")
        assertContains(text, LOG_NAME)
        assertContains(text, "a line")
        assertTrue(BUILD_LABEL !in text, "a build was claimed that nobody recorded: $text")
    }

    @Test
    fun isNamedAfterTheEndingItReports() {
        assertEquals(
            "conifer-crash-2026-07-28_123456.txt",
            runEndReportFileName(CRASHED, TimeZone.UTC)
        )
        // A run that vanished is named after when it started, that being the only moment known.
        assertEquals(
            "conifer-run-2026-07-28_143201.txt",
            runEndReportFileName(VANISHED, TimeZone.UTC)
        )
    }

    /** [RunEndReports] is what the screen holds, and the whole report comes out of it in one call. */
    @Test
    fun isAssembledFromTheRecordAndTheLogFolder() {
        val reports = RunEndReports(
            lastEnd = CRASHED,
            logFiles = { fileName, maxChars ->
                assertEquals(LOG_NAME, fileName)
                assertEquals(MAX_LOG_TAIL_CHARS, maxChars)
                "12:34:50.001 I  pull: 3 buckets changed"
            },
            userAgent = "Conifer (Android 36)",
        )

        val report = assertNotNull(reports.report())

        assertContains(report, "Conifer (Android 36)")
        assertContains(report, "pull: 3 buckets changed")
        // The name is spelled in the reader's own time zone, which is the machine's here - so this
        // is about its shape, and the test above is about its spelling.
        val fileName = assertNotNull(reports.reportFileName())
        assertTrue(fileName.startsWith("conifer-crash-") && fileName.endsWith(".txt"), fileName)
    }

    /** Nothing to report, nothing to name - the banner is not up in the first place. */
    @Test
    fun isNothingWithoutAnEnding() {
        val reports = RunEndReports()

        assertNull(reports.report())
        assertNull(reports.reportFileName())
    }

    /**
     * The path a report is built from comes off a file on disk, and a file on disk is not a promise.
     * Only a name that looks like a log file's, and only in the log folder: the reader is handed the
     * name alone, so a path that tries to leave the folder never becomes a read at all.
     */
    @Test
    fun onlyEverReadsSomethingShapedLikeALogFile() {
        assertEquals(LOG_NAME, logTailFileName(LOG_PATH))
        assertEquals(LOG_NAME, logTailFileName("""C:\Users\alice\conifer\logs\$LOG_NAME"""))
        assertNull(logTailFileName(null))
        assertNull(logTailFileName("/etc/passwd"))
        assertNull(logTailFileName("/logs/conifer-../../../.ssh/id_rsa.log"))
        assertNull(logTailFileName("/logs/last-run.json"))
    }

    /** A crash whose log path is not one, so the reader is never asked for it. */
    @Test
    fun readsNoLogForAnEndingThatNamesNone() {
        var wasRead = false
        val reports = RunEndReports(
            lastEnd = LastRunEnd.Crashed(BREADCRUMB.copy(logFile = "/etc/passwd")),
            logFiles = { _, _ -> wasRead = true; "root:x:0:0" },
        )

        val report = assertNotNull(reports.report())

        assertTrue(!wasRead, "a path that is not a log file name was read anyway")
        assertTrue("root:x:0:0" !in report)
    }

    private companion object {
        /** 2026-07-28 12:34:56.789 UTC. */
        val AT = Instant.fromEpochMilliseconds(1_785_242_096_789)
        const val BUILD_LABEL = "1.2.4 (9), commit 29ba2459, built 2026-08-10T19:58:08Z"
        const val LOG_NAME = "conifer-2026-07-28_143201.log"
        const val LOG_PATH = "/logs/$LOG_NAME"
        val BREADCRUMB = CrashBreadcrumb(
            buildLabel = BUILD_LABEL,
            atEpochMillis = AT.toEpochMilliseconds(),
            origin = "main",
            type = "IllegalStateException",
            message = "boom",
            frames = listOf("at eu.heha.conifer.sync.SyncEngine.push(SyncEngine.kt:214)"),
            logFile = LOG_PATH,
        )
        val CRASHED = LastRunEnd.Crashed(BREADCRUMB)

        /** 2026-07-28 14:32:01 UTC, which is what [LOG_NAME] says the run started at. */
        val VANISHED = LastRunEnd.Vanished(
            VanishedRun(
                logFile = LOG_NAME,
                startedAtEpochMillis = logFileStartTime(LOG_NAME, TimeZone.UTC)
                    ?.toEpochMilliseconds(),
            )
        )
    }
}
