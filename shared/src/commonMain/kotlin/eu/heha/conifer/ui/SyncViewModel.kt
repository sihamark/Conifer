package eu.heha.conifer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.sync.SyncConnectionState
import eu.heha.conifer.sync.SyncCoordinator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Thin Compose-facing wrapper around [SyncCoordinator]: owns the sheet/popover visibility and
 * server-url input the coordinator itself has no opinion about, plus the background triggers
 * spec §5 calls for (debounced-after-edit, periodic) - the coordinator only ever runs when told
 * to, so sync stays fully opt-in regardless of what this class schedules.
 */
@OptIn(FlowPreview::class)
class SyncViewModel(
    private val coordinator: SyncCoordinator,
    private val bitsRepository: BitsRepository,
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
                coordinator.syncNow()
            }
        }
        viewModelScope.launch {
            // spec §5 trigger: "optionally periodic" - this single-screen app has no separate
            // foreground/background lifecycle signal to hook a real "app foreground" trigger
            // into instead, so a periodic loop stands in for it while the app is running.
            while (isActive) {
                delay(SYNC_INTERVAL)
                coordinator.syncNow()
            }
        }
    }

    /** Pressing the app bar's status icon: a debug glance once connected, else the full sheet. */
    fun onClickSyncIcon() {
        if (state.connection is SyncConnectionState.Connected) {
            if (state.isDebugOpen) {
                state = state.copy(isDebugOpen = false, areDebugDetailsOpen = false)
            } else {
                viewModelScope.launch {
                    val info = coordinator.debugInfo()
                    state = state.copy(isDebugOpen = true, debugInfo = info)
                }
            }
        } else {
            state = state.copy(isSheetOpen = true)
        }
    }

    fun onCloseSheet() {
        state = state.copy(isSheetOpen = false)
    }

    /** Closing the popover also collapses its details, so the next glance is a glance again. */
    fun onCloseDebug() {
        state = state.copy(isDebugOpen = false, areDebugDetailsOpen = false)
    }

    fun onToggleDebugDetails() {
        state = state.copy(areDebugDetailsOpen = !state.areDebugDetailsOpen)
    }

    fun onOpenSettingsFromDebug() {
        state = state.copy(isDebugOpen = false, areDebugDetailsOpen = false, isSheetOpen = true)
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
     * Also reachable from the debug popover, which stays open across the round - so refresh its
     * snapshot afterwards, otherwise the details it shows (last sync, root ETag, tally, error)
     * would still describe the *previous* round while sitting right under a "Sync now" the user
     * just pressed.
     */
    fun onClickSyncNow() {
        viewModelScope.launch {
            coordinator.syncNow()
            if (state.isDebugOpen) {
                state = state.copy(debugInfo = coordinator.debugInfo())
            }
        }
    }

    fun onClickDisconnect() {
        connectJob?.cancel()
        coordinator.disconnect()
        state = state.copy(isSheetOpen = false, serverUrlInput = "")
    }

    private companion object {
        val SYNC_INTERVAL = 5.minutes
    }
}

