package eu.heha.conifer.log

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Telling the three ways a run can end apart, at the next start: it crashed and said so, it ended
 * properly and said that, or it stopped saying anything at all - which is the one that has no
 * evidence of its own and has to be read out of the shape of the log file it left.
 */
class LastRunTest {

    /** A crash explains itself, and is the better answer than anything the log's last line shows. */
    @Test
    fun readsACrashOffTheRecord() {
        val end = lastRunEnd(LastRunRecord(runningLogFile = LOG_NAME, crash = BREADCRUMB), READER)

        assertEquals(LastRunEnd.Crashed(BREADCRUMB), end)
    }

    /** The ordinary case: the app was put away or shut down, and its log says goodbye. */
    @Test
    fun saysNothingAboutARunThatClosedItsLog() {
        val closed = logTailReader("12:00:00.000 I  a line\n12:00:01.000 I  $LOG_CLOSED_MARKER\n")

        assertNull(lastRunEnd(LastRunRecord(runningLogFile = LOG_NAME), closed))
    }

    /**
     * The point of the marker: a log that simply stops belongs to a run that was killed - for memory,
     * by a signal, by a native crash below Kotlin - and nobody was left to write any of that down.
     */
    @Test
    fun reportsARunWhoseLogSimplyStops() {
        val end = lastRunEnd(LastRunRecord(runningLogFile = LOG_NAME), READER)

        val vanished = assertIs<LastRunEnd.Vanished>(end)
        assertEquals(LOG_NAME, vanished.logFile)
        // The one moment such a run does leave behind is in the name of its log file.
        assertEquals(
            logFileStartTime(LOG_NAME, TimeZone.currentSystemDefault()),
            vanished.run.startedAt
        )
    }

    /**
     * A phone app that has been put away keeps its process and goes on logging in the background, so
     * the goodbye is rarely the last thing in the log. What was said is what counts.
     */
    @Test
    fun saysNothingAboutARunThatSaidGoodbyeAndThenCarriedOnInTheBackground() {
        val record = LastRunRecord(runningLogFile = LOG_NAME, isLogClosed = true)

        assertNull(lastRunEnd(record, READER))
    }

    /** Coming back takes the goodbye back: being killed while on screen means something again. */
    @Test
    fun reportsARunThatCameBackAndThenStoppedForGood() {
        val record = LastRunRecord(runningLogFile = LOG_NAME, isLogClosed = false)

        assertIs<LastRunEnd.Vanished>(lastRunEnd(record, READER))
    }

    /** Saying goodbye and taking it back leaves the rest of the record alone. */
    @Test
    fun notesTheGoodbyeWithoutLosingTheRestOfTheRecord() {
        val store = InMemoryLastRunStore()
        writeLastRun(store, LastRunRecord(runningLogFile = LOG_NAME, vanish = VANISH))

        writeLogClosed(store, true)
        val closed = readLastRun(store)
        writeLogClosed(store, false)
        val reopened = readLastRun(store)

        assertEquals(LastRunRecord(LOG_NAME, null, VANISH, isLogClosed = true), closed)
        assertEquals(LastRunRecord(LOG_NAME, null, VANISH, isLogClosed = false), reopened)
    }

    /** Nothing to note it in yet - the first start writes the record, and nothing precedes that. */
    @Test
    fun leavesAnEmptyStoreEmptyWhenTheLogCloses() {
        val store = InMemoryLastRunStore()

        writeLogClosed(store, true)

        assertNull(readLastRun(store))
        assertEquals(0, store.writes)
    }

    /**
     * The fallback, for a record written before the goodbye was noted in it: the marker as the
     * *last* line, which a run that came back and carried on no longer has.
     */
    @Test
    fun reportsARunThatCameBackAndThenStopped() {
        val reopened = logTailReader(
            "12:00:01.000 I  $LOG_CLOSED_MARKER\n" +
                    "12:05:00.000 I  $LOG_REOPENED_MARKER\n" +
                    "12:05:01.000 I  pull: 3 buckets changed\n"
        )

        assertIs<LastRunEnd.Vanished>(lastRunEnd(LastRunRecord(runningLogFile = LOG_NAME), reopened))
    }

    /** An ending already worked out survives a restart made before the user got round to it. */
    @Test
    fun carriesAnUndismissedEndingOn() {
        val vanish = VanishedRun(logFile = LOG_NAME, startedAtEpochMillis = 0)

        val end = lastRunEnd(LastRunRecord(runningLogFile = "conifer-later.log", vanish = vanish), READER)

        assertEquals(LastRunEnd.Vanished(vanish), end)
    }

    /**
     * A log that cannot be read is not evidence of anything - it was pruned, or the platform keeps
     * none at all (the browser). Reporting a vanish there would be an accusation with nothing behind
     * it, and one the report could not say anything about either.
     */
    @Test
    fun saysNothingWithoutALogToLookAt() {
        assertNull(lastRunEnd(LastRunRecord(runningLogFile = LOG_NAME), logTailReader(null)))
        assertNull(lastRunEnd(LastRunRecord(runningLogFile = LOG_NAME), null))
        assertNull(lastRunEnd(LastRunRecord(runningLogFile = null), READER))
        assertNull(lastRunEnd(null, READER))
    }

    /** A reader that throws is a reader that answers nothing, not a start that fails. */
    @Test
    fun survivesAReaderThatThrows() {
        val failing = LogTailReader { _, _ -> throw RuntimeException("no such folder") }

        assertNull(lastRunEnd(LastRunRecord(runningLogFile = LOG_NAME), failing))
    }

    @Test
    fun readsTheStartTimeOutOfTheLogFileSName() {
        assertEquals(
            Instant.parse("2026-07-28T14:32:01Z"),
            logFileStartTime(LOG_NAME, TimeZone.UTC)
        )
        assertNull(logFileStartTime("conifer-not-a-time.log", TimeZone.UTC))
    }

    @Test
    fun recognisesTheClosingMarkerOnlyAtTheEnd() {
        assertTrue(lastRunEndedQuietly("a line\n12:00:01.000 I  $LOG_CLOSED_MARKER"))
        assertTrue(lastRunEndedQuietly("12:00:01.000 I  $LOG_CLOSED_MARKER\n\n"))
        assertTrue(!lastRunEndedQuietly("12:00:01.000 I  $LOG_CLOSED_MARKER\n12:00:02.000 I  more"))
        assertTrue(!lastRunEndedQuietly(""))
    }

    private fun logTailReader(tail: String?) = LogTailReader { fileName, _ ->
        tail.takeIf { fileName == LOG_NAME }
    }

    private companion object {
        const val LOG_NAME = "conifer-2026-07-28_143201.log"

        val VANISH = VanishedRun(logFile = LOG_NAME, startedAtEpochMillis = 0)

        /** A log that stops mid-sentence, which is what a killed run leaves behind. */
        val READER = LogTailReader { _, _ -> "12:00:00.000 I  pull: 3 buckets changed\n" }

        val BREADCRUMB = CrashBreadcrumb(
            buildLabel = "1.2.4 (9), commit 29ba2459, built 2026-08-10T19:58:08Z",
            atEpochMillis = 1_785_242_096_789,
            origin = "main",
            type = "IllegalStateException",
            message = "boom",
            logFile = "/logs/$LOG_NAME",
        )
    }
}
