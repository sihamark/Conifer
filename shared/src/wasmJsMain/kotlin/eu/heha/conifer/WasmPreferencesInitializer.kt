package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer

object WasmPreferencesInitializer : PreferencesInitializer {
    /**
     * The browser has no file system for DataStore's default file-based persistence, so the
     * preferences are kept in `window.localStorage` under [fileName] instead.
     */
    override fun createStore(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage = WebLocalStorage(
                serializer = PreferencesSerializer,
                name = fileName
            )
        )
}
