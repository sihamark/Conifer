package eu.heha.conifer

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading a run's log back out for a crash report - the other direction of the log file, and the
 * half of a report that says what the app had been doing.
 */
class JvmLogTailTest {

    private val folder: File = Files.createTempDirectory("conifer-log-tail").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun readsTheWholeLogWhenItFitsInsideTheLimit() {
        File(folder, LOG_NAME).writeText("first line\nsecond line\n")

        assertEquals("first line\nsecond line\n", readLogTail(folder, LOG_NAME, 1_000))
    }

    /** The end, not the start: what a crash is explained by is the last thing that happened. */
    @Test
    fun keepsTheEndOfALogTooLongToCarry() {
        File(folder, LOG_NAME).writeText("older lines\n" + "x".repeat(100) + "the last line")

        val tail = readLogTail(folder, LOG_NAME, 20)

        assertEquals(20, tail?.length)
        assertTrue(tail!!.endsWith("the last line"), tail)
    }

    /**
     * A log pruned since the crash reads as nothing, and so does a name that would leave the folder
     * - the second belt to [eu.heha.conifer.log.logTailFileName]'s braces, since this is the layer
     * that actually opens the file.
     */
    @Test
    fun readsNothingItShouldNot() {
        val outside = File(folder.parentFile, "outside.log").apply { writeText("not yours") }

        assertNull(readLogTail(folder, LOG_NAME, 1_000), "there is no log file of that name")
        assertNull(readLogTail(folder, "../${outside.name}", 1_000), "read outside the log folder")

        outside.delete()
    }

    private companion object {
        const val LOG_NAME = "conifer-2026-07-28_143201.log"
    }
}
