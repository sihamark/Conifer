package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.heha.conifer.prefs.SyncPrefs
import okio.Path.Companion.toOkioPath
import java.io.File

object JvmSyncPrefsInitializer : SyncPrefsInitializer {
    override fun createSyncPrefsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath {
            File(jvmDataFolder(), SyncPrefs.STORE_FILE_NAME).toOkioPath()
        }
}
