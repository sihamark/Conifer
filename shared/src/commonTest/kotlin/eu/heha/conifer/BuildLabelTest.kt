package eu.heha.conifer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The one line that says which build wrote a log. Its wording is checked exactly, because it is read
 * by whoever receives the log and compared against what they think they shipped.
 */
class BuildLabelTest {

    @Test
    fun namesTheVersionTheCommitAndTheBuildTime() {
        val label = buildLabel(
            versionName = "1.2.4",
            versionCode = 9,
            commit = "29ba2459",
            isModified = false,
            buildTime = AT,
        )

        assertEquals("1.2.4 (9), commit 29ba2459, built 2026-07-28T12:34:56Z", label)
    }

    /** A hash that describes source living on one machine only has to say so. */
    @Test
    fun marksABuildMadeFromAModifiedWorkingTree() {
        val label = buildLabel(
            versionName = "1.2.4",
            versionCode = 9,
            commit = "29ba2459",
            isModified = true,
            buildTime = AT,
        )

        assertEquals("1.2.4 (9), commit 29ba2459 (modified), built 2026-07-28T12:34:56Z", label)
    }

    /**
     * The generated object is real and readable from common code - which is what the whole
     * `generateBuildInfo` task exists for. Its values are whatever built this test, so only their
     * presence can be asserted.
     */
    @Test
    fun readsTheGeneratedBuildInfo() {
        assertEquals(BuildInfo.buildTimeMillis, BuildInfo.buildTime.toEpochMilliseconds())

        val label = buildLabel()

        assertContains(label, BuildInfo.versionName)
        assertContains(label, BuildInfo.commit)
    }

    private companion object {
        /** 2026-07-28 12:34:56.789 UTC - the fraction is there to be dropped. */
        val AT = Instant.fromEpochMilliseconds(1_785_242_096_789)
    }
}
