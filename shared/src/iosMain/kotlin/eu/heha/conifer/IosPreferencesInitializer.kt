package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

object IosPreferencesInitializer : PreferencesInitializer {
    override fun createStore(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath {
            (iosDocumentDirectory() + "/" + fileName).toPath()
        }
}
