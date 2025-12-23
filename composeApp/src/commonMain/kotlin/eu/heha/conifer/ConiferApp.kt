package eu.heha.conifer

import androidx.compose.runtime.Composable
import androidx.room.RoomDatabase
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.ui.BitsRoute
import eu.heha.conifer.ui.theme.ConiferTheme
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.StateFlow

object ConiferApp {

    val repository by lazy { BitsRepository() }
    var platform: Platform? = null
        private set

    var databaseInitializer: DatabaseInitializer? = null
        private set

    fun initialize(
        antilog: Antilog,
        platform: Platform,
        databaseInitializer: DatabaseInitializer,
    ) {
        Napier.base(antilog)

        this.platform = platform
        this.databaseInitializer = databaseInitializer
    }

    interface PermissionHandler {
        val isPermissionGranted: StateFlow<Boolean>
        val permissionRationale: String
        fun requestPermission()
    }

    @Composable
    fun AppContent(permissionHandler: PermissionHandler? = null) {
        ConiferTheme {
            BitsRoute(permissionHandler)
        }
    }
}

interface Platform {
    val name: String
}

interface DatabaseInitializer {
    fun createBuilder(): RoomDatabase.Builder<AppDatabase>
}