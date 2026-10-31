package eu.heha.conifer.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersionTest {

    @Suppress("KotlinMisorderedAssertEqualsArguments")
    @Test
    fun parsesABareThreePartVersion() {
        assertEquals(ReleaseVersion(1, 3, 1), ReleaseVersion.parse("1.3.1"))
        assertEquals(ReleaseVersion(10, 0, 22), ReleaseVersion.parse(" 10.0.22 "))
    }

    /** A pre-release or a malformed tag must never be taken for a newer release. */
    @Test
    fun rejectsAnythingElse() {
        listOf("", "1.3", "1.3.1.4", "v1.3.1", "1.3.1-rc1", "1.x.0", "1..1", "-1.0.0")
            .forEach { assertNull(ReleaseVersion.parse(it), it) }
    }

    @Test
    fun comparesNumericallyPartByPart() {
        assertTrue(ReleaseVersion.parse("1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertTrue(ReleaseVersion.parse("2.0.0")!! > ReleaseVersion.parse("1.99.99")!!)
        assertTrue(ReleaseVersion.parse("1.3.2")!! > ReleaseVersion.parse("1.3.1")!!)
        assertEquals(0, ReleaseVersion.parse("1.3.1")!!.compareTo(ReleaseVersion(1, 3, 1)))
    }
}
