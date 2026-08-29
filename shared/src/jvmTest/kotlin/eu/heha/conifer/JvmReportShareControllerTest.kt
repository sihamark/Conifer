package eu.heha.conifer

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The desktop's half of sharing: the report has to be a file on disk before the file manager can be
 * asked to show it. Only that half is tested here - opening a window on the machine running the
 * tests is not something a test may do.
 */
class JvmReportShareControllerTest {

    private val folder: File = Files.createTempDirectory("conifer-report-share").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun writesTheReportIntoAFolderItCreates() {
        val reports = File(folder, "reports")

        val file =
            assertNotNull(writeReport(reports, "conifer-crash-2026-07-28_123456.txt", "boom"))

        assertEquals("boom", file.readText())
        assertEquals(reports.canonicalFile, file.parentFile.canonicalFile)
    }

    /** Sharing the same crash twice is the same file, not two - see `crashReportFileName`. */
    @Test
    fun replacesAnEarlierShareOfTheSameCrash() {
        writeReport(folder, "report.txt", "first")

        val file = assertNotNull(writeReport(folder, "report.txt", "second"))

        assertEquals("second", file.readText())
        assertEquals(1, folder.listFiles()!!.size)
    }

    /** A folder that cannot be made stands in for every unwritable place: no report, no crash. */
    @Test
    fun staysQuietWhenItCannotWrite() {
        val blocked = File(folder, "blocked").apply { writeText("I am a file, not a folder") }

        assertNull(writeReport(File(blocked, "reports"), "report.txt", "boom"))
    }
}
