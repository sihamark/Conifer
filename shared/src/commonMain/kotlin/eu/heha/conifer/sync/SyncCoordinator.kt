package eu.heha.conifer.sync

import eu.heha.conifer.BrowserOpener
import eu.heha.conifer.auth.Credentials
import eu.heha.conifer.auth.LoginFlowV2
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.net.CONIFER_USER_AGENT
import eu.heha.conifer.prefs.SyncPrefs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant
import kotlin.time.TimeSource

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
    private val userAgent: String = CONIFER_USER_AGENT,
    private val loginFlow: LoginFlowV2 = LoginFlowV2(userAgent = userAgent),
) {
    private val _state = MutableStateFlow<SyncConnectionState>(SyncConnectionState.Disconnected)
    val state: StateFlow<SyncConnectionState> = _state

    private var lastError: String? = null
    private var lastStats: SyncStats? = null

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
     * Non-null (with a human-readable custody detail) if [Credentials]'s encryption key currently
     * isn't in an OS-backed secure store, e.g. an unreachable OS keyring on headless Linux. Meant
     * to be checked by the UI *before* calling [connect] - once [Credentials.username]/
     * [Credentials.appPassword] are actually written, they're written through this same weaker
     * custody regardless, so the warning only helps if it's seen before that happens.
     */
    val insecureKeyCustody: String?
        get() = credentials.keyCustodyDescription.takeUnless { credentials.isKeySecurelyStored }

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
            // Never logged beyond this point: session.loginUrl and its poll token are
            // single-use credentials for this very login (see LoginFlowV2).
            Napier.i { "login flow v2 started for $normalized" }
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
            Napier.i { "connected as ${result.loginName} to ${result.server}" }
            syncNow(SyncTrigger.AfterConnect)
        } catch (e: CancellationException) {
            Napier.i { "login flow v2 for $normalized cancelled" }
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
        Napier.i { "disconnected - credentials cleared, local sync state kept" }
    }

    /**
     * Runs one [SyncEngine.sync] round. A no-op unless [state] is currently
     * [SyncConnectionState.Connected]. [trigger] exists purely for the log file: "it synced twice
     * and then stopped" is only answerable if each round says what started it.
     */
    suspend fun syncNow(trigger: SyncTrigger = SyncTrigger.Manual) {
        val current = _state.value as? SyncConnectionState.Connected ?: run {
            Napier.d { "sync (${trigger.label}) skipped - not connected" }
            return
        }
        if (current.isSyncing) {
            Napier.d { "sync (${trigger.label}) skipped - one is already running" }
            return
        }
        _state.value = current.copy(isSyncing = true)
        val startedAt = TimeSource.Monotonic.markNow()
        Napier.i { "sync started (${trigger.label}) as ${current.username} on ${current.server}" }
        try {
            val remoteStore = KtorWebDavStore(
                serverUrl = current.server,
                username = current.username,
                password = credentials.appPassword,
                userAgent = userAgent
            )
            val stats = SyncEngine(remoteStore, databaseController, syncPrefs).sync()
            lastStats = stats
            lastError = null
            Napier.i {
                "sync finished in ${startedAt.elapsedNow()}: ${stats.pushed} pushed, " +
                        "${stats.pulled} pulled, ${stats.merged} merged"
            }
        } catch (e: CancellationException) {
            Napier.i { "sync cancelled after ${startedAt.elapsedNow()}" }
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "sync failed after ${startedAt.elapsedNow()}" }
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
        Napier.i { "app folder changed to '$normalized' - every bit marked dirty for a full re-push" }
        syncPrefs.setAppRoot(normalized)
        databaseController.syncDao().resetForNewAppRoot()
        syncPrefs.clearRootEtag()
        syncNow(SyncTrigger.AfterAppRootChange)
    }

    /** Snapshot for the debug popover - troubleshooting details, never a bit's content. */
    suspend fun debugInfo(): SyncDebugInfo = SyncDebugInfo(
        deviceId = syncPrefs.deviceId(),
        appRoot = syncPrefs.appRoot(),
        lastSyncAt = syncPrefs.lastSyncAt(),
        rootEtag = syncPrefs.rootEtag(),
        lastGcAt = syncPrefs.lastGcAt(),
        lastError = lastError,
        lastStats = lastStats,
    )
}

/**
 * What started a [SyncCoordinator.syncNow] round. Log-only - the engine behaves identically
 * either way; [label] is what shows up in the log file.
 */
enum class SyncTrigger(val label: String) {
    Manual("manual"),
    AfterEdit("after edit"),
    Periodic("periodic"),
    AfterConnect("after connect"),
    AfterAppRootChange("app folder changed"),
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
    val lastStats: SyncStats?,
)
