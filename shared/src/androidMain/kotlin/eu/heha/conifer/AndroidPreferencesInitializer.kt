package eu.heha.conifer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toOkioPath
import java.io.File

class AndroidPreferencesInitializer(
    private val context: Context
) : PreferencesInitializer {
    override fun createStore(fileName: String): DataStore<Preferences> {
        val appContext = context.applicationContext
        return PreferenceDataStoreFactory.createWithPath {
            File(appContext.filesDir, "datastore/$fileName")
                .also { it.parentFile?.mkdirs() }
                .toOkioPath()
        }
    }
}
