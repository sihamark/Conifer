package eu.heha.conifer.update

import eu.heha.conifer.BuildInfo
import eu.heha.conifer.prefs.UpdatePrefs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Asks [source] for the newest release at most once a day while the app is on screen, and offers it
 * as [availableUpdate] when it is newer than [currentVersion] and not the one the user dismissed.
 *
 * The last answer is kept in [prefs], so the hint survives a restart without a network. A failed
 * check only goes into the log.
 */
class UpdateChecker(
    private val source: UpdateSource,
    private val prefs: UpdatePrefs,
    private val currentVersion: String = BuildInfo.versionName,
    private val clock: Clock = Clock.System,
) {
    private val enabled = MutableStateFlow(true)
    private val available = MutableStateFlow<Release?>(null)
    private var latest: Release? = null
    private var dismissedVersion: String? = null
    private var isLoaded = false
    private var failedAt: Instant? = null

    /** Whether the check runs at all; the user's switch. */
    val isEnabled: StateFlow<Boolean> = enabled.asStateFlow()

    /** The release to point the user at, or null while there is none worth mentioning. */
    val availableUpdate: StateFlow<Release?> = available.asStateFlow()

    /** Checks whenever one is due, while [isOnScreen] and the check is enabled. Never returns. */
    suspend fun run(isOnScreen: Flow<Boolean>) {
        load()
        combine(isOnScreen, enabled) { onScreen, isEnabled -> onScreen && isEnabled }
            .distinctUntilChanged()
            .collectLatest { isActive ->
                if (!isActive) return@collectLatest
                // Out of the way of the first screen and the first sync round.
                delay(SETTLE_DELAY)
                while (true) {
                    val wait = nextCheckAt() - clock.now()
                    if (wait.isPositive()) delay(wait)
                    check()
                }
            }
    }

    suspend fun setEnabled(isEnabled: Boolean) {
        load()
        prefs.setCheckEnabled(isEnabled)
        enabled.value = isEnabled
        publish()
    }

    /** Hides the current hint until a release newer than it appears. */
    suspend fun dismiss() {
        val version = available.value?.version ?: return
        prefs.setDismissedVersion(version)
        dismissedVersion = version
        publish()
    }

    private suspend fun load() {
        if (isLoaded) return
        enabled.value = prefs.isCheckEnabled()
        latest = prefs.latestRelease()
        dismissedVersion = prefs.dismissedVersion()
        isLoaded = true
        publish()
    }

    private suspend fun nextCheckAt(): Instant {
        val due = prefs.lastCheckAt()?.plus(CHECK_INTERVAL) ?: Instant.DISTANT_PAST
        val retry = failedAt?.plus(RETRY_INTERVAL) ?: return due
        return maxOf(due, retry)
    }

    private suspend fun check() {
        val release = try {
            source.latestRelease()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.d { "update check failed: $e" }
            failedAt = clock.now()
            return
        }
        failedAt = null
        prefs.setChecked(clock.now(), release)
        latest = release
        Napier.d { "update check: newest release ${release.version}, running $currentVersion" }
        publish()
    }

    private fun publish() {
        available.value = latest?.takeIf { enabled.value && isWorthMentioning(it.version) }
    }

    private fun isWorthMentioning(version: String): Boolean {
        val release = ReleaseVersion.parse(version) ?: return false
        val current = ReleaseVersion.parse(currentVersion) ?: return false
        val dismissed = dismissedVersion?.let(ReleaseVersion::parse)
        return release > current && (dismissed == null || release > dismissed)
    }

    companion object {
        val CHECK_INTERVAL = 24.hours
        val RETRY_INTERVAL = 1.hours
        val SETTLE_DELAY = 5.seconds
    }
}
