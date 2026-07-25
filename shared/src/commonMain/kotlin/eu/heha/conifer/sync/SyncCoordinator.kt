package eu.heha.conifer.sync

import eu.heha.conifer.BrowserOpener
import eu.heha.conifer.auth.Credentials
import eu.heha.conifer.auth.LoginFlowV2
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.prefs.SyncPrefs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * Orchestrates the optional Nextcloud sync feature end to end for the UI: Login Flow v2,
 * [Credentials] storage, and running [SyncEngine]. A thin, testable layer between the sync
 * engine (which knows nothing about UI state) and a ViewModel (which knows nothing about
 * WebDAV or the login flow).
 *
 * Sync is opt-in: until [connect] succeeds, [state] stays [SyncConnectionState.Disconnected] and
 * nothing here touches the network or the database - the app works exactly as it always has.
 */
class SyncCoordinator(
    private val syncPrefs: SyncPrefs,
    private val credentials: Credentials,
    private val databaseController: DatabaseController,
    private val browserOpener: BrowserOpener,
    private val loginFlow: LoginFlowV2 = LoginFlowV2(),
) {
    private val _state = MutableStateFlow<SyncConnectionState>(SyncConnectionState.Disconnected)
    val state: StateFlow<SyncConnectionState> = _state

    private var lastError: String? = null

    /**
     * Brings [state] in line with whatever [Credentials]/[SyncPrefs] already hold (e.g. from a
     * previous app run). Call once at startup, before anything else - [Credentials] delegate
     * reads are synchronous so the constructor can't do this itself without an initial flash of
     * the wrong state on platforms where [SyncPrefs] genuinely needs a suspend read.
     */
    suspend fun refreshState() {
        val username = credentials.username
        val appPassword = credentials.appPassword
        val server = syncPrefs.serverUrl()
        _state.value = if (username.isNotEmpty() && appPassword.isNotEmpty() && server != null) {
            SyncConnectionState.Connected(
                server = server,
                username = username,
                isSyncing = false,
                lastSyncAt = syncPrefs.lastSyncAt(),
            )
        } else {
            SyncConnectionState.Disconnected
        }
    }

    /**
     * Runs the Login Flow v2 dance against [serverUrl]: starts a session, opens it in the
     * system browser, and waits for the user to finish signing in there. On success, stores the
     * app password/username and immediately runs a first sync. Cancel the calling coroutine to
     * give up early (e.g. the user closes the sheet) - [state] falls back to [SyncConnectionState.Disconnected]
     * either way.
     */
    suspend fun connect(serverUrl: String) {
        val normalized = serverUrl.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }.trimEnd('/')
        try {
            val session = loginFlow.start(normalized)
            _state.value = SyncConnectionState.Connecting(session.loginUrl)
            browserOpener.open(session.loginUrl)
            val result = loginFlow.awaitCompletion(session)
            credentials.username = result.loginName
            credentials.appPassword = result.appPassword
            syncPrefs.setServerUrl(result.server)
            _state.value = SyncConnectionState.Connected(
                server = result.server,
                username = result.loginName,
                isSyncing = false,
                lastSyncAt = null,
            )
            syncNow()
        } catch (e: CancellationException) {
            _state.value = SyncConnectionState.Disconnected
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "Login Flow v2 failed for $normalized" }
            lastError = e.message ?: e::class.simpleName
            _state.value = SyncConnectionState.Disconnected
        }
    }

    /**
     * Forgets the stored credentials so sync stops. Deliberately leaves [SyncPrefs] (server URL,
     * root ETag, device id, ...) untouched, so reconnecting to the same account resumes instead
     * of forcing a full re-pull.
     */
    fun disconnect() {
        credentials.username = ""
        credentials.appPassword = ""
        _state.value = SyncConnectionState.Disconnected
    }

    /** Runs one [SyncEngine.sync] round. A no-op unless [state] is currently [SyncConnectionState.Connected]. */
    suspend fun syncNow() {
        val current = _state.value as? SyncConnectionState.Connected ?: return
        if (current.isSyncing) return
        _state.value = current.copy(isSyncing = true)
        try {
            val remoteStore =
                KtorWebDavStore(current.server, current.username, credentials.appPassword)
            SyncEngine(remoteStore, databaseController, syncPrefs).sync()
            lastError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "sync failed" }
            lastError = e.message ?: e::class.simpleName
        } finally {
            val latest = _state.value as? SyncConnectionState.Connected
            if (latest != null) {
                _state.value = latest.copy(isSyncing = false, lastSyncAt = syncPrefs.lastSyncAt())
            }
        }
    }

    /** Current folder bits are synced into on the Nextcloud instance, relative to its files root. */
    suspend fun appRoot(): String = syncPrefs.appRoot()

    /**
     * Changes the folder bits are synced into. [SyncEngine] reads [SyncPrefs.appRoot] fresh every
     * time it runs, so no reconnect is needed - but a bit already clean from syncing into the old
     * folder would otherwise never be written into the new (empty) one, since only dirty bits get
     * pushed. So this also resets every bit to dirty-with-no-remote-identity and clears the
     * bucket/readable-rendering caches (see [eu.heha.conifer.model.database.SyncDao.resetForNewAppRoot]), then runs a sync
     * immediately rather than waiting for the next debounced/periodic trigger. Blank input is
     * ignored rather than persisted, so the field can never be left empty; a no-op path (equal to
     * the current one) skips the reset so it doesn't force a pointless full re-sync.
     */
    suspend fun setAppRoot(path: String) {
        val normalized = path.trim().trim('/')
        if (normalized.isBlank() || normalized == syncPrefs.appRoot()) return
        syncPrefs.setAppRoot(normalized)
        databaseController.syncDao().resetForNewAppRoot()
        syncPrefs.clearRootEtag()
        syncNow()
    }

    /** Snapshot for the debug popover - troubleshooting details, never a bit's content. */
    suspend fun debugInfo(): SyncDebugInfo = SyncDebugInfo(
        deviceId = syncPrefs.deviceId(),
        appRoot = syncPrefs.appRoot(),
        lastSyncAt = syncPrefs.lastSyncAt(),
        rootEtag = syncPrefs.rootEtag(),
        lastGcAt = syncPrefs.lastGcAt(),
        lastError = lastError,
    )
}

sealed interface SyncConnectionState {
    data object Disconnected : SyncConnectionState

    /** [loginUrl] is blank for the instant between starting the session and it being opened. */
    data class Connecting(val loginUrl: String) : SyncConnectionState

    data class Connected(
        val server: String,
        val username: String,
        val isSyncing: Boolean,
        val lastSyncAt: Instant?,
    ) : SyncConnectionState
}

data class SyncDebugInfo(
    val deviceId: String,
    val appRoot: String,
    val lastSyncAt: Instant?,
    val rootEtag: String?,
    val lastGcAt: Instant?,
    val lastError: String?,
)
