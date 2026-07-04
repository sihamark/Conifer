package eu.heha.conifer

import androidx.room.RoomDatabase
import eu.heha.conifer.model.database.AppDatabase
import kotlinx.coroutines.flow.StateFlow

interface Platform {
    val name: String
}

interface DatabaseInitializer {
    fun createBuilder(): RoomDatabase.Builder<AppDatabase>
}

interface ClipboardController {
    fun copyToClipboard(text: String)
}

interface PermissionHandler {
    val isPermissionGranted: StateFlow<Boolean>
    val permissionRationale: String
    fun requestPermission()
}