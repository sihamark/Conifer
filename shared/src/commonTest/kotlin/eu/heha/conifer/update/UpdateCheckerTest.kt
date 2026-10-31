package eu.heha.conifer.update

import eu.heha.conifer.prefs.InMemoryPreferencesStore
import eu.heha.conifer.prefs.UpdatePrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {

    @Test
    fun offersANewerReleaseAfterSettling() = runChecker(latest = "1.4.0") {
        start()
        assertNull(checker.availableUpdate.value)

        advanceTimeBy(UpdateChecker.SETTLE_DELAY + 1.minutes)

        assertEquals(RELEASE_1_4_0, checker.availableUpdate.value)
        assertEquals(1, requests)
    }

    @Test
    fun saysNothingAboutTheSameOrAnOlderRelease() = runChecker(latest = "1.3.1") {
        start()
        advanceTimeBy(1.hours)

        assertNull(checker.availableUpdate.value)
        assertEquals(1, requests)
    }

    @Test
    fun checksOnceADay() = runChecker(latest = "1.3.1") {
        start()
        advanceTimeBy(UpdateChecker.CHECK_INTERVAL * 2.5)

        assertEquals(3, requests)
    }

    /** A failure is retried within the hour rather than waiting a whole day, and never shown. */
    @Test
    fun retriesAFailedCheckSooner() = runChecker(latest = null) {
        start()
        advanceTimeBy(UpdateChecker.SETTLE_DELAY + UpdateChecker.RETRY_INTERVAL * 2.5)

        assertEquals(3, requests)
        assertNull(checker.availableUpdate.value)
    }

    @Test
    fun doesNotCheckWhileOffScreen() = runChecker(latest = "1.4.0") {
        isOnScreen.value = false
        start()
        advanceTimeBy(UpdateChecker.CHECK_INTERVAL * 3)
        assertEquals(0, requests)

        isOnScreen.value = true
        advanceTimeBy(UpdateChecker.SETTLE_DELAY + 1.minutes)
        assertEquals(1, requests)
    }

    /** A check made today is not repeated by the next start, whose hint comes from the stored one. */
    @Test
    fun aRestartShowsTheStoredAnswerWithoutAskingAgain() = runChecker(latest = "1.4.0") {
        prefs.setChecked(clock.now() - 1.hours, RELEASE_1_4_0)
        start()
        runCurrent()

        assertEquals(RELEASE_1_4_0, checker.availableUpdate.value)
        advanceTimeBy(UpdateChecker.SETTLE_DELAY + 1.minutes)
        assertEquals(0, requests)
    }

    @Test
    fun aDismissedReleaseStaysHiddenUntilANewerOne() = runChecker(latest = "1.4.0") {
        start()
        advanceTimeBy(UpdateChecker.SETTLE_DELAY + 1.minutes)

        checker.dismiss()
        assertNull(checker.availableUpdate.value)

        latest = "1.4.1"
        advanceTimeBy(UpdateChecker.CHECK_INTERVAL)
        assertEquals("1.4.1", checker.availableUpdate.value?.version)
    }

    @Test
    fun switchedOffItNeitherChecksNorShows() = runChecker(latest = "1.4.0") {
        start()
        advanceTimeBy(UpdateChecker.SETTLE_DELAY + 1.minutes)
        checker.setEnabled(false)

        assertNull(checker.availableUpdate.value)
        assertFalse(prefs.isCheckEnabled())
        advanceTimeBy(UpdateChecker.CHECK_INTERVAL * 3)
        assertEquals(1, requests)
    }

    /** A build whose own version cannot be read is never told it is behind. */
    @Test
    fun anUnreadableCurrentVersionShowsNothing() =
        runChecker(latest = "1.4.0", currentVersion = "dev") {
            start()
            advanceTimeBy(1.hours)

            assertNull(checker.availableUpdate.value)
        }

    private class Fixture(scope: TestScope, currentVersion: String, var latest: String?) {
        val testScope = scope
        val isOnScreen = MutableStateFlow(true)
        val prefs = UpdatePrefs(InMemoryPreferencesStore())
        var requests = 0
        val clock = object : Clock {
            override fun now(): Instant =
                START + Duration.parse("${scope.testScheduler.currentTime}ms")
        }
        val checker = UpdateChecker(
            source = {
                requests++
                val version = latest ?: error("offline")
                Release(version, "https://example.org/$version")
            },
            prefs = prefs,
            currentVersion = currentVersion,
            clock = clock
        )

        fun start() {
            testScope.backgroundScope.launch { checker.run(isOnScreen) }
        }
    }

    private fun runChecker(
        latest: String?,
        currentVersion: String = "1.3.1",
        block: suspend Fixture.() -> Unit
    ) = runTest { Fixture(this, currentVersion, latest).block() }

    private suspend fun Fixture.advanceTimeBy(duration: Duration) =
        testScope.advanceTimeBy(duration)

    private fun Fixture.runCurrent() = testScope.runCurrent()

    private companion object {
        val START = Instant.parse("2026-10-31T12:00:00Z")
        val RELEASE_1_4_0 = Release("1.4.0", "https://example.org/1.4.0")
    }
}
