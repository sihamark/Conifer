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
 * order: which build died of what, and what had the app been doing. The first comes from the
 * breadcrumb, the second from the log file of the run that crashed - and the second is the reason
 * this is a file to share rather than a line to read out.
 */
class CrashReportTest {

    @Test
    fun namesTheBuildTheDeviceTheErrorAndTheLog() {
        val text = crashReportText(BREADCRUMB, userAgent = "Conifer (Android 36)")

        assertContains(text, BUILD_LABEL)
        assertContains(text, "Conifer (Android 36)")
        assertContains(text, "2026-07-28T12:34:56.789Z")
        assertContains(text, "IllegalStateException: boom")
        assertContains(text, "at eu.heha.conifer.sync.SyncEngine.push(SyncEngine.kt:214)")
        assertContains(text, LOG_PATH)
    }

    /** The lines leading up to a crash are the ones that explain it. */
    @Test
    fun carriesTheLogOfTheRunThatCrashed() {
        val text = crashReportText(BREADCRUMB, logTail = "12:34:50.001 I  pull: 3 buckets changed")

        assertContains(text, "--- the log of the run that crashed ---")
        assertContains(text, "pull: 3 buckets changed")
    }

    /**
     * A reader who cannot see the top of the file would otherwise take the first line they do see
     * for the start of the run, and read a cut log as a short one.
     */
    @Test
    fun saysSoWhenTheLogWasCutShort() {
        val text = crashReportText(BREADCRUMB, logTail = "x".repeat(MAX_LOG_TAIL_CHARS))

        assertContains(text, "last $MAX_LOG_TAIL_CHARS characters")
    }

    /** A log that has since been pruned costs the report its second half and nothing else. */
    @Test
    fun isStillWorthSendingWithoutTheLog() {
        val text = crashReportText(BREADCRUMB, logTail = null)

        assertContains(text, "IllegalStateException: boom")
        assertTrue("the log of the run" !in text, "an empty log section was written anyway: $text")
    }

    @Test
    fun isNamedAfterTheCrashItReports() {
        assertEquals(
            "conifer-crash-2026-07-28_123456.txt",
            crashReportFileName(BREADCRUMB, TimeZone.UTC)
        )
    }

    /** [CrashReports] is what the screen holds, and the whole report comes out of it in one call. */
    @Test
    fun isAssembledFromTheBreadcrumbAndTheLogFolder() {
        val reports = CrashReports(
            lastCrash = BREADCRUMB,
            logFiles = { fileName, maxChars ->
                assertEquals("conifer-2026-07-28_143201.log", fileName)
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
    fun isNothingWithoutACrash() {
        val reports = CrashReports()

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
        assertEquals("conifer-2026-07-28_143201.log", logTailFileName(LOG_PATH))
        assertEquals(
            "conifer-2026-07-28_143201.log",
            logTailFileName("""C:\Users\alice\conifer\logs\conifer-2026-07-28_143201.log""")
        )
        assertNull(logTailFileName(null))
        assertNull(logTailFileName("/etc/passwd"))
        assertNull(logTailFileName("/logs/conifer-../../../.ssh/id_rsa.log"))
        assertNull(logTailFileName("/logs/last-crash.json"))
    }

    /** A crash whose log path is not one, so the reader is never asked for it. */
    @Test
    fun readsNoLogForACrashThatNamesNone() {
        var wasRead = false
        val reports = CrashReports(
            lastCrash = BREADCRUMB.copy(logFile = "/etc/passwd"),
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
        const val LOG_PATH = "/logs/conifer-2026-07-28_143201.log"
        val BREADCRUMB = CrashBreadcrumb(
            buildLabel = BUILD_LABEL,
            atEpochMillis = AT.toEpochMilliseconds(),
            origin = "main",
            type = "IllegalStateException",
            message = "boom",
            frames = listOf("at eu.heha.conifer.sync.SyncEngine.push(SyncEngine.kt:214)"),
            logFile = LOG_PATH,
        )
    }
}
