package eu.heha.conifer

import eu.heha.conifer.log.LastRunRecord
import eu.heha.conifer.log.logFileName
import eu.heha.conifer.log.readLastRun
import eu.heha.conifer.log.writeLastRun
import kotlinx.browser.localStorage
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The browser's stand-in for a log folder, run in a real browser: `localStorage` is the only thing
 * on this platform that outlives the page, and a tab is reloaded, closed or discarded far more often
 * than an app is killed - so a run whose log is only in memory is a run that answers nothing.
 */
class WasmLogFileInitializerTest {

    @BeforeTest
    fun setUp() = clearConiferKeys()

    @AfterTest
    fun tearDown() = clearConiferKeys()

    @Test
    fun keepsALogThatCanBeReadBackAfterTheRun() {
        val sink = assertNotNull(WasmLogFileInitializer.createLogFile(LOG_NAME))

        sink.appendLine("12:00:00.000 I  --- log started ---")
        sink.appendLine("12:00:01.000 I  pull: 3 buckets changed")

        // By name, exactly as the file platforms are asked (see LogTailReader), and this is what a
        // report about the previous run reads.
        assertEquals(
            "12:00:00.000 I  --- log started ---\n12:00:01.000 I  pull: 3 buckets changed\n",
            WasmLogFileInitializer.readLogTail(LOG_NAME, 1_000)
        )
        assertEquals(LOG_NAME, sink.location)
    }

    /** Whole lines only, and the end kept rather than the start: that is the half that explains. */
    @Test
    fun growsNoFurtherThanTheBrowserCanAfford() {
        val sink = assertNotNull(WasmLogFileInitializer.createLogFile(LOG_NAME))

        // Every line the same length, so that "no line was cut in half" is checkable by length.
        repeat(MAX_WEB_LOG_CHARS / 1_000 + 5) { index ->
            sink.appendLine(index.toString().padStart(4, '0') + "x".repeat(996))
        }

        val stored = assertNotNull(WasmLogFileInitializer.readLogTail(LOG_NAME, MAX_WEB_LOG_CHARS))
        assertTrue(stored.length <= MAX_WEB_LOG_CHARS, "kept ${stored.length} characters")
        assertTrue(stored.endsWith("\n"), "the log should end on a line break")
        assertTrue(
            stored.lines().none { it.isNotEmpty() && it.length != 1_000 },
            "a line was cut in half rather than dropped whole"
        )
    }

    /** As on the platforms with real files: a new log per start, and only the newest few kept. */
    @Test
    fun keepsOnlyTheNewestRuns() {
        val start = Instant.fromEpochMilliseconds(1_785_242_096_789)
        val names = (0 until MAX_WEB_LOG_RUNS + 3).map { runIndex ->
            logFileName(start + (runIndex * 3).hours, TimeZone.UTC).also { name ->
                WasmLogFileInitializer.createLogFile(name)
                    ?.appendLine("run $runIndex")
            }
        }

        val kept = names.filter { WasmLogFileInitializer.readLogTail(it, 100) != null }

        // The last created is the run under way, so the ones before it are pruned down to fill the
        // remaining slots - the same arithmetic as MAX_LOG_FILES - 1 on a disk.
        assertEquals(names.takeLast(MAX_WEB_LOG_RUNS), kept)
    }

    @Test
    fun keepsTheLastRunRecordAcrossThePageGoingAway() {
        val store = WasmLogFileInitializer.createLastRunStore()

        writeLastRun(store, LastRunRecord(runningLogFile = LOG_NAME))

        assertEquals(LOG_NAME, readLastRun(store)?.runningLogFile)
        // Forgetting an ending is writing the record back without it, never a delete - the log file
        // this run is writing has to stay named for the next start either way.
        writeLastRun(store, LastRunRecord(runningLogFile = LOG_NAME))
        assertNull(readLastRun(store)?.crash)
    }

    /** The browser's version of a share sheet is a download; all it has to do is not throw. */
    @Test
    fun offersAReportAsADownload() {
        assertTrue(WasmReportShareController.share("conifer-crash-2026-07-28_123456.txt", "boom"))
    }

    private fun clearConiferKeys() {
        (0 until localStorage.length)
            .mapNotNull { localStorage.key(it) }
            .filter { it.startsWith("conifer-") }
            .forEach { localStorage.removeItem(it) }
    }

    private companion object {
        const val LOG_NAME = "conifer-2026-07-28_143201.log"
    }
}
