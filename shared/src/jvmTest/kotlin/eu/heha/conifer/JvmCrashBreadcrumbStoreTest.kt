package eu.heha.conifer

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The file behind the crash banner: one crash kept, the newest, and no failure of its own on the way
 * in or out - see `CrashBreadcrumbStore`.
 */
class JvmCrashBreadcrumbStoreTest {

    @Test
    fun keepsWhatWasLastWrittenAndForgetsItOnRequest() {
        val file = tempFile()
        val store = FileCrashBreadcrumbStore(file)

        assertNull(store.read(), "nothing has been written yet")

        store.write("""{"origin":"main"}""")
        assertEquals("""{"origin":"main"}""", store.read())

        // Replaces rather than appends: the banner asks about the run that just ended, and a device
        // that crashes repeatedly must not accumulate.
        store.write("""{"origin":"worker"}""")
        assertEquals("""{"origin":"worker"}""", store.read())

        store.clear()
        assertNull(store.read())
        assertFalse(file.exists(), "clearing should take the file with it")

        file.parentFile.deleteRecursively()
    }

    /** A folder that isn't there stands in for every unwritable one: no crash, just no breadcrumb. */
    @Test
    fun staysQuietWhenTheFileCannotBeWritten() {
        val store = FileCrashBreadcrumbStore(File(tempFile().parentFile, "gone/last-crash.json"))

        store.write("""{"origin":"main"}""")

        assertNull(store.read())
        store.clear()
    }

    private fun tempFile() =
        File(Files.createTempDirectory("conifer-crash-breadcrumb").toFile(), "last-crash.json")
}
