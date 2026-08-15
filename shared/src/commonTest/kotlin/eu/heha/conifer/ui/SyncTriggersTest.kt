package eu.heha.conifer.ui

import eu.heha.conifer.sync.SyncConnectionState
import eu.heha.conifer.sync.SyncTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * When [runSyncTriggers] asks for a sync round, which is a question about who is looking and whether
 * there is a server to sync with at all - not about syncing itself, which is the coordinator's and
 * the engine's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncTriggersTest {

    @Test
    fun syncsWhenTheAppComesToTheFrontAndThenEveryInterval() = runTriggers {
        connect()

        assertEquals(listOf(SyncTrigger.AppForeground), triggers)

        advanceTimeBy(INTERVAL * 2.5)

        assertEquals(
            listOf(SyncTrigger.AppForeground, SyncTrigger.Periodic, SyncTrigger.Periodic),
            triggers
        )
    }

    /** The point of the whole thing: a phone put away keeps its process, and stops syncing anyway. */
    @Test
    fun stopsWhileTheAppIsOffScreen() = runTriggers {
        connect()
        triggers.clear()

        isOnScreen.value = false
        advanceTimeBy(INTERVAL * 10)

        assertEquals(emptyList(), triggers)
    }

    /** Nothing to sync with: every round used to be a "skipped - not connected" line, forever. */
    @Test
    fun staysQuietWhileDisconnected() = runTriggers {
        advanceTimeBy(INTERVAL * 10)

        assertEquals(emptyList(), triggers)
    }

    /**
     * A rotation puts the app away and brings it straight back, and a glance at another app is not
     * much slower. Neither is worth a round of its own when one has just run.
     */
    @Test
    fun doesNotSyncAgainWhenItComesBackRightAway() = runTriggers {
        connect(lastSyncAt = NOW - INTERVAL / 2)

        isOnScreen.value = false
        runCurrent()
        isOnScreen.value = true
        runCurrent()

        assertEquals(emptyList(), triggers)
    }

    /** Long enough away for the bits to have moved on, though, and it is worth asking again. */
    @Test
    fun syncsOnComingBackFromLongerAway() = runTriggers {
        connect(lastSyncAt = NOW - INTERVAL * 2)

        assertEquals(listOf(SyncTrigger.AppForeground), triggers)
    }

    /** Losing the connection stops the loop; getting it back starts one over. */
    @Test
    fun followsTheConnectionComingAndGoing() = runTriggers {
        connect()
        triggers.clear()

        connection.value = SyncConnectionState.Disconnected
        advanceTimeBy(INTERVAL * 3)

        assertEquals(emptyList(), triggers, "a disconnected app has nothing to sync with")

        connect()

        assertEquals(listOf(SyncTrigger.AppForeground), triggers)
    }

    /** The world [runSyncTriggers] runs in: who is looking, what the connection says, what it asked for. */
    private class Triggers(val scope: TestScope) {
        val isOnScreen = MutableStateFlow(true)
        val connection = MutableStateFlow<SyncConnectionState>(SyncConnectionState.Disconnected)
        val triggers = mutableListOf<SyncTrigger>()

        fun connect(lastSyncAt: Instant? = null) {
            connection.value = SyncConnectionState.Connected(
                server = "https://cloud.example",
                username = "hans",
                isSyncing = false,
                lastSyncAt = lastSyncAt
            )
            scope.runCurrent()
        }

        fun advanceTimeBy(duration: kotlin.time.Duration) = scope.advanceTimeBy(duration)

        fun runCurrent() = scope.runCurrent()
    }

    /**
     * Runs [block] against a [Triggers] with the collector going, on virtual time - the intervals
     * here are minutes, and no test waits for one.
     */
    private fun runTriggers(block: Triggers.() -> Unit) = runTest {
        val triggers = Triggers(this)
        val collector = launch {
            runSyncTriggers(
                isOnScreen = triggers.isOnScreen,
                connection = triggers.connection,
                interval = INTERVAL,
                // Fixed, so that the age of a last sync is what the test wrote and not what the
                // virtual clock has since made of it.
                clock = object : Clock {
                    override fun now(): Instant = NOW
                }
            ) { trigger -> triggers.triggers += trigger }
        }
        runCurrent()
        triggers.block()
        collector.cancel()
    }

    private companion object {
        val INTERVAL = 5.minutes
        val NOW = Instant.parse("2026-08-15T12:00:00Z")
    }
}
