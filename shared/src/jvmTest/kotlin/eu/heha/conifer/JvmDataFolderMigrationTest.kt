package eu.heha.conifer

import eu.heha.conifer.model.database.DATABASE_NAME
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one-way trip out of the installation folder. Worth its own test because it is the only code in
 * the app that moves somebody's notes and then deletes where they were: getting the conditions wrong
 * means either losing an update's worth of writing or overwriting it with an older copy.
 */
class JvmDataFolderMigrationTest {

    @Test
    fun takesTheWholeFolderAcrossAndLeavesNothingBehind() {
        val legacy = tempFolder("legacy")
        val target = tempFolder("target")
        File(legacy, DATABASE_NAME).writeText("the notes")
        // The write-ahead log and the shared-memory file are only consistent with the database if
        // they travel with it, and the logs are what a report of the move would be read out of.
        File(legacy, "$DATABASE_NAME-wal").writeText("the newest of them")
        File(legacy, "logs").mkdirs()
        File(legacy, "logs/conifer-run.log").writeText("a line")

        val note = migrateLegacyData(from = legacy, into = target)

        assertEquals("the notes", File(target, DATABASE_NAME).readText())
        assertEquals("the newest of them", File(target, "$DATABASE_NAME-wal").readText())
        assertEquals("a line", File(target, "logs/conifer-run.log").readText())
        assertFalse(legacy.exists(), "the old folder is deleted, so no older build can write to it")
        assertTrue(note?.contains(legacy.absolutePath) == true, "the log should name where it was")
    }

    @Test
    fun leavesAnOlderDatabaseWhereItIsWhenThisFolderAlreadyHasOne() {
        val legacy = tempFolder("legacy")
        val target = tempFolder("target")
        File(legacy, DATABASE_NAME).writeText("what the installation still has")
        File(target, DATABASE_NAME).writeText("what has been written since")

        val note = migrateLegacyData(from = legacy, into = target)

        assertEquals("what has been written since", File(target, DATABASE_NAME).readText())
        assertTrue(legacy.isDirectory, "and it is left alone rather than deleted")
        assertTrue(note != null, "a folder passed over is still worth a line")
    }

    @Test
    fun doesNothingWithoutSomethingToMove() {
        val target = tempFolder("target")
        assertNull(migrateLegacyData(from = null, into = target))
        assertNull(migrateLegacyData(from = File(target, "never-existed"), into = target))
        // A folder there but no database in it: an install that was never written to.
        assertNull(migrateLegacyData(from = tempFolder("empty"), into = target))
        // And the same folder twice, which a copy onto itself would empty.
        File(target, DATABASE_NAME).writeText("the notes")
        assertNull(migrateLegacyData(from = target, into = target))
        assertEquals("the notes", File(target, DATABASE_NAME).readText())
    }

    private fun tempFolder(name: String): File =
        Files.createTempDirectory("conifer-$name").toFile().also { it.deleteOnExit() }
}
