package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.heha.conifer.auth.Credentials
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.model.database.AppDatabase
import kotlinx.coroutines.flow.StateFlow

interface Platform {
    val name: String
}

/**
 * Namespaces KSafe's key store and data file (spec-independent hardening): JVM Desktop's OS
 * secret store is per-OS-user, shared across every app on the machine, so two apps with no
 * namespace of their own would collide on the same key. Matches `AppConfig.namespace`
 * (`buildSrc`), duplicated here since `buildSrc` isn't on the app's runtime classpath.
 */
internal const val KSAFE_APP_NAMESPACE = "eu.heha.conifer"

interface DatabaseInitializer {
    /**
     * Builds a fully configured [AppDatabase]. The SQLite driver and query coroutine context are
     * chosen per platform here (rather than in shared code) because the web target has no
     * `Dispatchers.IO` and uses a different, Web Worker–backed driver.
     */
    fun createDatabase(): AppDatabase
}

interface SyncPrefsInitializer {
    /**
     * Creates the Preferences DataStore backing `SyncPrefs`. Platform-specific because each
     * platform chooses its own storage location (and the web target has no usable file system
     * for DataStore yet, so it falls back to an in-memory store).
     */
    fun createSyncPrefsStore(): DataStore<Preferences>
}

interface ClipboardController {
    fun copyToClipboard(text: String)
}

interface LogFileInitializer {
    /**
     * Opens [fileName] for appending in this platform's log folder, after deleting all but the
     * newest [eu.heha.conifer.log.MAX_LOG_FILES] files already there (see
     * [eu.heha.conifer.log.logFileName]).
     *
     * Returns `null` when there is nowhere to write - the web target has no file system, a
     * folder can turn out to be unwritable - because a missing log file must never stop the app
     * from starting. Implementations therefore swallow their own failures rather than throwing.
     */
    fun createLogFile(fileName: String): LogFileSink?
}

interface BrowserOpener {
    /**
     * Opens [url] in the system browser, e.g. for Nextcloud Login Flow v2 (spec §10).
     *
     * Returns whether the browser actually took it. There is no shortage of ways this fails
     * through no fault of the user — a Linux JVM whose AWT reports no `Desktop.Action.BROWSE`
     * even though `xdg-open` works, a popup blocker eating `window.open` — and Login Flow v2
     * simply stalls if nobody notices, since it waits for a browser that never opened. A `false`
     * lets the caller offer the URL for the user to open by hand instead.
     *
     * Best effort in the other direction too: `true` means the platform accepted the request, not
     * that a window is definitely on screen.
     */
    fun open(url: String): Boolean
}

interface CredentialsInitializer {
    /**
     * Builds the [Credentials] store. Platform-specific because KSafe needs a `Context` on
     * Android for its Keystore-backed encryption; other platforms need no setup.
     */
    fun createCredentials(): Credentials

    /**
     * Waits until [Credentials] is safe to read. A no-op everywhere except the web target: KSafe
     * there is backed by an async WebCrypto cache load, so a synchronous read (the property
     * delegate `Credentials` uses) before it finishes would silently see defaults instead of the
     * real stored value. Call once at startup before the first read.
     */
    suspend fun awaitCredentialsReady() {}
}

interface PermissionHandler {
    val isPermissionGranted: StateFlow<Boolean>

    /**
     * Why the permission is needed. Platform-specific because the wording depends on what the
     * permission enables there (e.g. replying to a notification on Android); the implementation
     * resolves the already localized strings from its platform resources.
     */
    val permissionRationale: PermissionRationale

    fun requestPermission()
}

/** A permission rationale as a highlighted lead-in plus the actual explanation. */
data class PermissionRationale(
    val lead: String,
    val text: String
)