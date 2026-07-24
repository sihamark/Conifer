package eu.heha.conifer.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Global, non-secret sync state (Nextcloud sync spec §3.2), backed by a Preferences DataStore
 * created per platform via `SyncPrefsInitializer`. Credentials never live here — they go into
 * the encrypted secret store (added with the login flow).
 */
class SyncPrefs(private val store: DataStore<Preferences>) {

    /** ETag of the remote posts folder at the last completed sync (fast-path check). */
    suspend fun rootEtag(): String? = read(ROOT_ETAG)

    suspend fun setRootEtag(etag: String) {
        store.edit { it[ROOT_ETAG] = etag }
    }

    suspend fun lastSyncAt(): Instant? = read(LAST_SYNC_AT)?.let(Instant::fromEpochMilliseconds)

    suspend fun setLastSyncAt(time: Instant) {
        store.edit { it[LAST_SYNC_AT] = time.toEpochMilliseconds() }
    }

    /** Time of the last completed tombstone garbage-collection pass (sync spec §8). */
    suspend fun lastGcAt(): Instant? = read(LAST_GC_AT)?.let(Instant::fromEpochMilliseconds)

    suspend fun setLastGcAt(time: Instant) {
        store.edit { it[LAST_GC_AT] = time.toEpochMilliseconds() }
    }

    /**
     * Stable id of this installation, generated on first use. Stamped into every local change
     * as `modifiedBy`, where it serves as the deterministic merge tiebreaker.
     */
    suspend fun deviceId(): String {
        val preferences = store.updateData { preferences ->
            if (preferences[DEVICE_ID] == null) {
                preferences.toMutablePreferences()
                    .apply { this[DEVICE_ID] = Uuid.random().toString() }
                    .toPreferences()
            } else {
                preferences
            }
        }
        return checkNotNull(preferences[DEVICE_ID])
    }

    suspend fun serverUrl(): String? = read(SERVER_URL)

    suspend fun setServerUrl(url: String) {
        store.edit { it[SERVER_URL] = url }
    }

    /** Name of the app's root folder on the Nextcloud instance. */
    suspend fun appRoot(): String = read(APP_ROOT) ?: DEFAULT_APP_ROOT

    suspend fun setAppRoot(appRoot: String) {
        store.edit { it[APP_ROOT] = appRoot }
    }

    private suspend fun <T> read(key: Preferences.Key<T>): T? = store.data.first()[key]

    companion object {
        /** DataStore requires the file name to end in `.preferences_pb`. */
        const val STORE_FILE_NAME = "sync.preferences_pb"
        const val DEFAULT_APP_ROOT = "Conifer"

        private val ROOT_ETAG = stringPreferencesKey("rootEtag")
        private val LAST_SYNC_AT = longPreferencesKey("lastSyncAt")
        private val LAST_GC_AT = longPreferencesKey("lastGcAt")
        private val DEVICE_ID = stringPreferencesKey("deviceId")
        private val SERVER_URL = stringPreferencesKey("serverUrl")
        private val APP_ROOT = stringPreferencesKey("appRoot")
    }
}
