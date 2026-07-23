package eu.heha.conifer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.heha.conifer.prefs.SyncPrefs
import okio.Path.Companion.toOkioPath
import java.io.File

class AndroidSyncPrefsInitializer(
    private val context: Context
) : SyncPrefsInitializer {
    override fun createSyncPrefsStore(): DataStore<Preferences> {
        val appContext = context.applicationContext
        return PreferenceDataStoreFactory.createWithPath {
            File(appContext.filesDir, "datastore/${SyncPrefs.STORE_FILE_NAME}")
                .also { it.parentFile?.mkdirs() }
                .toOkioPath()
        }
    }
}
