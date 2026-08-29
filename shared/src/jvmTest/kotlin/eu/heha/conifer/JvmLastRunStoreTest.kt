package eu.heha.conifer

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The file behind the banner: one record kept, the newest, and no failure of its own on the way in
 * or out - see `LastRunStore`.
 */
class JvmLastRunStoreTest {

    @Test
    fun keepsWhatWasLastWritten() {
        val file = tempFile()
        val store = FileLastRunStore(file)

        assertNull(store.read(), "nothing has been written yet")

        store.write("""{"origin":"main"}""")
        assertEquals("""{"origin":"main"}""", store.read())

        // Replaces rather than appends: the banner asks about the run that just ended, and a device
        // that crashes repeatedly must not accumulate.
        store.write("""{"origin":"worker"}""")
        assertEquals("""{"origin":"worker"}""", store.read())

        file.parentFile.deleteRecursively()
    }

    /** A folder that isn't there stands in for every unwritable one: no record, and no crash. */
    @Test
    fun staysQuietWhenTheFileCannotBeWritten() {
        val store = FileLastRunStore(File(tempFile().parentFile, "gone/last-run.json"))

        store.write("""{"origin":"main"}""")

        assertNull(store.read())
    }

    private fun tempFile() =
        File(Files.createTempDirectory("conifer-last-run").toFile(), "last-run.json")
}
