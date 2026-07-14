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

    /**
     * Why the permission is needed. Platform-specific because the wording depends on what the
     * permission enables there (e.g. replying to a notification on Android); the implementation
     * resolves the already localized strings from its platform resources.
     */
    val permissionRationale: PermissionRationale

    fun requestPermission()
}

/** A permission rationale as a highlighted lead-in plus the actual explanation. */
data class PermissionRationale(
    val lead: String,
    val text: String
)