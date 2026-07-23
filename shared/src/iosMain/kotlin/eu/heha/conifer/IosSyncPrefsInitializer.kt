package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.heha.conifer.prefs.SyncPrefs
import okio.Path.Companion.toPath

object IosSyncPrefsInitializer : SyncPrefsInitializer {
    override fun createSyncPrefsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath {
            (iosDocumentDirectory() + "/" + SyncPrefs.STORE_FILE_NAME).toPath()
        }
}
