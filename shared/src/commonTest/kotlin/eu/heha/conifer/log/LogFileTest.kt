package eu.heha.conifer.log

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LogFileTest {

    @Test
    fun namesTheFileAfterTheLocalStartTime() {
        val name = logFileName(Instant.fromEpochMilliseconds(1_785_242_096_789), TimeZone.UTC)

        assertEquals("conifer-2026-07-28_123456.log", name)
    }

    /**
     * The platform initializers prune old logs by sorting file names, so a later run must sort
     * after an earlier one.
     */
    @Test
    fun sortsChronologicallyByName() {
        val start = Instant.fromEpochMilliseconds(1_785_242_096_789)
        val names = listOf(0.hours, 3.hours, 26.hours)
            .map { logFileName(start + it, TimeZone.UTC) }

        assertEquals(names, names.sorted())
        assertTrue(names.all { it.startsWith(LOG_FILE_PREFIX) && it.endsWith(LOG_FILE_SUFFIX) })
    }
}
