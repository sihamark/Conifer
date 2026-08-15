package eu.heha.conifer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.heha.conifer.AppPresence
import eu.heha.conifer.ClipboardController
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.sync.SyncConnectionState
import eu.heha.conifer.sync.SyncCoordinator
import eu.heha.conifer.sync.SyncTrigger
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Thin Compose-facing wrapper around [SyncCoordinator]: owns the sync surface's visibility and the
 * server-url input the coordinator itself has no opinion about, plus the background triggers
 * spec §5 calls for (debounced-after-edit, and [runSyncTriggers] for the other two) - the
 * coordinator only ever runs when told to, so sync stays fully opt-in regardless of what this class
 * schedules.
 */
@OptIn(FlowPreview::class)
class SyncViewModel(
    private val coordinator: SyncCoordinator,
    private val bitsRepository: BitsRepository,
    /** Whether anybody is looking - see [runSyncTriggers], which is all this is used for. */
    appPresence: AppPresence = AppPresence(),
    /** Absent on platforms without a clipboard; the copy affordance is then simply not offered. */
    private val clipboardController: ClipboardController? = null,
) : ViewModel() {

    var state by mutableStateOf(SyncUiState())
        private set

    private var connectJob: Job? = null

    init {
        viewModelScope.launch {
            coordinator.refreshState()
            coordinator.state.collect { connection ->
                state = state.copy(connection = connection)
            }
        }
        viewModelScope.launch {
            val appRoot = coordinator.appRoot()
            state = state.copy(appRootInput = appRoot, savedAppRoot = appRoot)
        }
        viewModelScope.launch {
            // spec §5 trigger: "after a local edit (debounced ≥ 10 s)". This also fires on a
            // remote pull's own writes to `bits`, which just resolves to a cheap fast-path
            // sync() next time around - harmless, just not perfectly precise.
            bitsRepository.getBits().drop(1).debounce(10.seconds).collect {
                coordinator.syncNow(SyncTrigger.AfterEdit)
            }
        }
        viewModelScope.launch {
            // spec §5 triggers: "after the app comes to the front" and "optionally periodic".
            runSyncTriggers(
                isOnScreen = appPresence.isOnScreen,
                connection = coordinator.state,
                interval = SYNC_INTERVAL
            ) { trigger -> coordinator.syncNow(trigger) }
        }
    }

    /**
     * Pressing the app bar's status icon shows or hides the sync surface. Which surface that is -
     * the glance, the settings sheet or the third pane - is the layout's call, not this class's;
     * see [SyncPresentation]. Disconnected there is no status to glance at, so it opens on the
     * settings straight away.
     */
    fun onClickSyncIcon() {
        if (state.isSyncOpen) {
            onCloseSync()
        } else {
            state = state.copy(
                isSyncOpen = true,
                areSettingsOpen = state.connection !is SyncConnectionState.Connected
            )
            // Fetched even while the details themselves are collapsed: it is what decides whether
            // the surface offers to show them at all.
            viewModelScope.launch {
                state = state.copy(debugInfo = coordinator.debugInfo())
            }
        }
    }

    /** Closing also folds the surface back up, so the next glance is a glance again. */
    fun onCloseSync() {
        state = state.copy(
            isSyncOpen = false,
            areSettingsOpen = false,
            areDebugDetailsOpen = false
        )
    }

    fun onToggleDebugDetails() {
        state = state.copy(areDebugDetailsOpen = !state.areDebugDetailsOpen)
    }

    fun onOpenSettings() {
        state = state.copy(areSettingsOpen = true, areDebugDetailsOpen = false)
    }

    fun onServerUrlChange(url: String) {
        state = state.copy(serverUrlInput = url)
    }

    fun onAppRootChange(path: String) {
        state = state.copy(appRootInput = path)
    }

    /** Persists the currently entered app-root path. A no-op for blank input. */
    fun onClickSaveAppRoot() {
        val path = state.appRootInput.trim()
        if (path.isBlank()) return
        viewModelScope.launch {
            coordinator.setAppRoot(path)
            state = state.copy(appRootInput = path, savedAppRoot = path)
        }
    }

    /**
     * Before actually starting the Login Flow v2 dance, checks whether the credentials that dance
     * ends with storing would land in a weaker key custody than usual (see
     * [SyncCoordinator.insecureKeyCustody]) - if so, [state.insecureKeyCustody][SyncUiState]
     * gates the real attempt behind [onClickConnectAnyway]/[onCancelInsecureKeyWarning] instead of
     * connecting right away.
     */
    fun onClickConnect() {
        val url = state.serverUrlInput.trim()
        if (url.isBlank()) return
        val custody = coordinator.insecureKeyCustody
        if (custody != null) {
            state = state.copy(insecureKeyCustody = custody)
        } else {
            startConnecting(url)
        }
    }

    fun onClickConnectAnyway() {
        state = state.copy(insecureKeyCustody = null)
        startConnecting(state.serverUrlInput.trim())
    }

    fun onCancelInsecureKeyWarning() {
        state = state.copy(insecureKeyCustody = null)
    }

    private fun startConnecting(url: String) {
        if (url.isBlank()) return
        connectJob?.cancel()
        connectJob = viewModelScope.launch { coordinator.connect(url) }
    }

    fun onClickCancelConnect() {
        connectJob?.cancel()
    }

    /**
     * Only reachable while a login is waiting on a browser that never opened. The URL is a
     * single-use credential for this login, so it goes to the clipboard and nowhere else - never
     * to the log.
     */
    fun onClickCopyLoginUrl(loginUrl: String) {
        clipboardController?.copyToClipboard(loginUrl)
    }

    fun onClickOpenLoginUrl() {
        coordinator.retryOpenLoginUrl()
    }

    /**
     * Also reachable from the surfaces that stay open across the round - so refresh their snapshot
     * afterwards, otherwise the details they show (last sync, root ETag, tally, error) would still
     * describe the *previous* round while sitting right under a "Sync now" the user just pressed.
     */
    fun onClickSyncNow() {
        viewModelScope.launch {
            coordinator.syncNow()
            if (state.isSyncOpen) {
                state = state.copy(debugInfo = coordinator.debugInfo())
            }
        }
    }

    /**
     * Leaves the surface itself alone: the sheet has nothing left to show once disconnected and
     * closes itself around this (see `SyncSettingsSheet`), while the third pane simply carries on
     * with the connect form.
     */
    fun onClickDisconnect() {
        connectJob?.cancel()
        coordinator.disconnect()
        state = state.copy(serverUrlInput = "")
    }

    private companion object {
        val SYNC_INTERVAL = 5.minutes
    }
}

/**
 * The sync triggers that nobody asks for: one round when the app comes to the front, and one every
 * [interval] for as long as it stays there and stays connected. Runs until cancelled; [sync] is
 * [SyncCoordinator.syncNow].
 *
 * Neither runs off screen. A phone app that has been put away is not stopped - it keeps its process
 * for as long as the system lets it - so the old loop went on syncing with nobody watching, which
 * costs battery for bits nobody is reading and buried the app's own goodbye in the log under the
 * lines it wrote afterwards (see [eu.heha.conifer.AppPresenceInitializer]). Neither runs while
 * disconnected either, where every round was only ever a "skipped - not connected" line.
 *
 * Coming to the front is not on its own a reason to sync: a rotation puts the app away and brings it
 * straight back, and so does a glance at another app. So a round that has just run - less than
 * [interval] ago, as the connection itself reports - is left to stand, and the loop below is what
 * catches up eventually.
 */
internal suspend fun runSyncTriggers(
    isOnScreen: Flow<Boolean>,
    connection: Flow<SyncConnectionState>,
    interval: Duration,
    clock: Clock = Clock.System,
    sync: suspend (SyncTrigger) -> Unit,
) {
    combine(isOnScreen, connection) { onScreen, connectionState ->
        (connectionState as? SyncConnectionState.Connected)?.takeIf { onScreen }
    }
        // Only the difference between being able to sync and not: a connection that reports a round
        // starting, finishing or being logged in as somebody else is not a reason to start over.
        .distinctUntilChangedBy { it != null }
        .collectLatest { connected ->
            connected ?: return@collectLatest
            val sinceLastSync = connected.lastSyncAt?.let { clock.now() - it }
            if (sinceLastSync == null || sinceLastSync >= interval) sync(SyncTrigger.AppForeground)
            while (true) {
                delay(interval)
                sync(SyncTrigger.Periodic)
            }
        }
}

