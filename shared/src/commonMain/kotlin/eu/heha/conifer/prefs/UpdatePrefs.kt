package eu.heha.conifer.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.heha.conifer.update.Release
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/**
 * What the update check ([eu.heha.conifer.update.UpdateChecker]) keeps between runs: whether it is
 * on, when it last got an answer and what that answer was, and the version the user asked not to
 * be reminded of.
 */
class UpdatePrefs(private val store: DataStore<Preferences>) {

    suspend fun isCheckEnabled(): Boolean = read(CHECK_ENABLED) ?: true

    suspend fun setCheckEnabled(enabled: Boolean) {
        store.edit { it[CHECK_ENABLED] = enabled }
    }

    suspend fun lastCheckAt(): Instant? = read(LAST_CHECK_AT)?.let(Instant::fromEpochMilliseconds)

    /** The newest release as of the last check that got an answer. */
    suspend fun latestRelease(): Release? {
        val preferences = store.data.first()
        val version = preferences[LATEST_VERSION] ?: return null
        val url = preferences[LATEST_URL] ?: return null
        return Release(version, url)
    }

    suspend fun setChecked(time: Instant, latest: Release) {
        store.edit {
            it[LAST_CHECK_AT] = time.toEpochMilliseconds()
            it[LATEST_VERSION] = latest.version
            it[LATEST_URL] = latest.url
        }
    }

    suspend fun dismissedVersion(): String? = read(DISMISSED_VERSION)

    suspend fun setDismissedVersion(version: String) {
        store.edit { it[DISMISSED_VERSION] = version }
    }

    private suspend fun <T> read(key: Preferences.Key<T>): T? = store.data.first()[key]

    companion object {
        /** DataStore requires the file name to end in `.preferences_pb`. */
        const val STORE_FILE_NAME = "update.preferences_pb"

        private val CHECK_ENABLED = booleanPreferencesKey("checkEnabled")
        private val LAST_CHECK_AT = longPreferencesKey("lastCheckAt")
        private val LATEST_VERSION = stringPreferencesKey("latestVersion")
        private val LATEST_URL = stringPreferencesKey("latestUrl")
        private val DISMISSED_VERSION = stringPreferencesKey("dismissedVersion")
    }
}
