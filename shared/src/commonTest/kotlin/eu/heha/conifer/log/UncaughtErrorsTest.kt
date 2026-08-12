package eu.heha.conifer.log

import io.github.aakira.napier.LogLevel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What an uncaught error has to leave behind: a line in this run's log file, written before the
 * process is gone, with the run's last lines still above it - and no second failure of its own.
 */
class UncaughtErrorsTest {

    /**
     * The point of the whole thing: the crash reaches the sink without anything else getting to run
     * first. The drain coroutine on `backgroundScope` never starts here (nothing yields to the test
     * dispatcher), which is exactly the situation a dying process is in.
     */
    @Test
    fun writesTheErrorToTheLogFileWithoutWaitingForACoroutine() = runTest {
        val sink = RecordingSink()

        logUncaughtError(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            fileAntilog = fileAntilog(sink),
        )

        assertEquals(1, sink.lines.size, "expected exactly the crash line, got ${sink.lines}")
        val line = sink.lines.single()
        assertTrue(
            line.startsWith("2026-07-28 12:34:56.789 E  [main] uncaught error | "),
            "unexpected line: $line"
        )
        // At ERROR level the whole stack trace goes in - that is what makes the log answerable.
        assertContains(line, "IllegalStateException")
        assertTrue(line.lines().size > 1, "expected a stack trace: $line")
    }

    /** The lines before the crash are the ones that explain it, so they have to get out too. */
    @Test
    fun flushesTheLinesStillQueuedBehindIt() = runTest {
        val sink = RecordingSink()
        val antilog = fileAntilog(sink)

        antilog.log(LogLevel.INFO, null, null, "pull: 3 buckets changed")
        logUncaughtError(UncaughtError(origin = "main", throwable = RuntimeException()), antilog)

        assertEquals(2, sink.lines.size, "expected the queued line and the crash: ${sink.lines}")
        assertContains(sink.lines.first(), "pull: 3 buckets changed")
        assertContains(sink.lines.last(), "uncaught error")
    }

    /** A platform can report an error in words (a browser event) rather than as a throwable. */
    @Test
    fun writesAMessageOnlyErrorToo() = runTest {
        val sink = RecordingSink()

        logUncaughtError(
            error = UncaughtError(origin = "worker", throwable = null, message = "script error"),
            fileAntilog = fileAntilog(sink),
        )

        assertEquals(
            listOf("2026-07-28 12:34:56.789 E  [worker] uncaught error: script error"),
            sink.lines
        )
    }

    /** A crash handler that crashes replaces a reported bug with an unreported one. */
    @Test
    fun survivesASinkThatCannotBeWritten() = runTest {
        val failing = object : LogFileSink {
            override val location: String = "full-disk"
            override fun appendLine(line: String) = throw RuntimeException("no space left on device")
        }

        logUncaughtError(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            fileAntilog = fileAntilog(failing),
        )
    }

    /**
     * The log line is the record; the breadcrumb is what makes the next run mention it at all. It
     * points back at the log file of the run that crashed, which is where the rest of the story is.
     */
    @Test
    fun leavesTheCrashInTheRecordForTheNextRun() = runTest {
        val sink = RecordingSink()
        val lastRun = InMemoryLastRunStore()

        logUncaughtError(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            fileAntilog = fileAntilog(sink),
            lastRun = lastRun,
            at = AT,
        )

        val breadcrumb = readLastRun(lastRun)?.crash
        assertEquals("main", breadcrumb?.origin)
        assertEquals("IllegalStateException", breadcrumb?.type)
        assertEquals("boom", breadcrumb?.message)
        assertEquals(AT, breadcrumb?.at)
        assertEquals(sink.location, breadcrumb?.logFile)
    }

    /**
     * A breadcrumb that cannot be written costs a banner. The log line it comes after must survive
     * that - which is also why it is written first.
     */
    @Test
    fun writesTheLogLineEvenWhenTheRecordCannotBeStored() = runTest {
        val sink = RecordingSink()
        val failing = object : LastRunStore {
            override fun write(text: String) = throw RuntimeException("no space left on device")
            override fun read(): String? = null
        }

        logUncaughtError(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            fileAntilog = fileAntilog(sink),
            lastRun = failing,
        )

        assertContains(sink.lines.single(), "uncaught error")
    }

    /** No log file on this platform (web) - there is nowhere to write, and that is not a failure. */
    @Test
    fun survivesHavingNoLogFileAtAll() {
        logUncaughtError(
            error = UncaughtError(origin = "main", throwable = IllegalStateException("boom")),
            fileAntilog = null,
        )
    }

    private fun TestScope.fileAntilog(sink: LogFileSink) = FileAntilog(
        sink = sink,
        scope = backgroundScope,
        clock = object : Clock {
            override fun now(): Instant = AT
        },
        timeZone = TimeZone.UTC,
    )

    private class RecordingSink : LogFileSink {
        override val location: String = "memory"
        val lines = mutableListOf<String>()
        override fun appendLine(line: String) {
            lines += line
        }
    }

    private companion object {
        /** 2026-07-28 12:34:56.789 UTC. */
        val AT = Instant.fromEpochMilliseconds(1_785_242_096_789)
    }
}
