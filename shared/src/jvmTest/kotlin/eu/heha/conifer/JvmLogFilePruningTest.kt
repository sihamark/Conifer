package eu.heha.conifer

import eu.heha.conifer.log.LAST_RUN_FILE_NAME
import eu.heha.conifer.log.LOG_FILE_PREFIX
import eu.heha.conifer.log.MAX_LOG_FILES
import eu.heha.conifer.log.logFileName
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * "A new log file per app start" only stays reasonable because each start also prunes - otherwise
 * a long-lived install accumulates one file per launch forever.
 */
class JvmLogFilePruningTest {

    @Test
    fun keepsTheNewestLogsAndLeavesForeignFilesAlone() {
        val folder = Files.createTempDirectory("conifer-log-prune").toFile()
        val start = Instant.fromEpochMilliseconds(1_785_242_096_789)
        val names = (0 until MAX_LOG_FILES + 4).map { runIndex ->
            logFileName(start + (runIndex * 3).hours, TimeZone.UTC)
                .also { File(folder, it).writeText("run $runIndex") }
        }
        val foreign = File(folder, "notes.txt").apply { writeText("not a log") }

        pruneOldLogFiles(folder)

        val remaining = folder.listFiles()!!.map { it.name }.sorted()
        // One slot is left free for the run that is about to create its own file.
        assertEquals(
            (names.takeLast(MAX_LOG_FILES - 1) + foreign.name).sorted(),
            remaining
        )
        assertTrue(remaining.count { it.startsWith(LOG_FILE_PREFIX) } == MAX_LOG_FILES - 1)
        folder.deleteRecursively()
    }

    /**
     * The last-run record sits in the log folder but is not a log, and it is read by the very start
     * that prunes - a name inside [LOG_FILE_PREFIX] would have it swept away moments before the
     * banner it feeds was to be shown.
     */
    @Test
    fun leavesTheLastRunRecordWhereTheNextStartCanFindIt() {
        val folder = Files.createTempDirectory("conifer-log-prune-record").toFile()
        val start = Instant.fromEpochMilliseconds(1_785_242_096_789)
        repeat(MAX_LOG_FILES + 4) { runIndex ->
            File(folder, logFileName(start + (runIndex * 3).hours, TimeZone.UTC))
                .writeText("run $runIndex")
        }
        val record = File(folder, LAST_RUN_FILE_NAME).apply { writeText("{}") }

        pruneOldLogFiles(folder)

        assertTrue(record.isFile, "the last-run record was pruned along with the logs")
        folder.deleteRecursively()
    }
}
