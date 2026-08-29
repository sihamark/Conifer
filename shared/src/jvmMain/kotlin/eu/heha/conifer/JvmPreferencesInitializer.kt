package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toOkioPath
import java.io.File

object JvmPreferencesInitializer : PreferencesInitializer {
    override fun createStore(fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath {
            File(jvmDataFolder(), fileName).toOkioPath()
        }
}
