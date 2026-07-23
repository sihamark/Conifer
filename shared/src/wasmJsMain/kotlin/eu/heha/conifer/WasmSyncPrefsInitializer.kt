package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import eu.heha.conifer.prefs.SyncPrefs

object WasmSyncPrefsInitializer : SyncPrefsInitializer {
    /**
     * The browser has no file system for DataStore's default file-based persistence, so the
     * preferences are kept in `window.localStorage` under [SyncPrefs.STORE_FILE_NAME] instead.
     */
    override fun createSyncPrefsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage = WebLocalStorage(
                serializer = PreferencesSerializer,
                name = SyncPrefs.STORE_FILE_NAME
            )
        )
}
