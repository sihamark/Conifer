package eu.heha.conifer

import eu.heha.conifer.model.database.AppDatabase
import kotlinx.coroutines.flow.StateFlow

interface Platform {
    val name: String
}

interface DatabaseInitializer {
    /**
     * Builds a fully configured [AppDatabase]. The SQLite driver and query coroutine context are
     * chosen per platform here (rather than in shared code) because the web target has no
     * `Dispatchers.IO` and uses a different, Web Worker–backed driver.
     */
    fun createDatabase(): AppDatabase
}

interface ClipboardController {
    fun copyToClipboard(text: String)
}

interface PermissionHandler {
    val isPermissionGranted: StateFlow<Boolean>
    val permissionRationale: String
    fun requestPermission()
}