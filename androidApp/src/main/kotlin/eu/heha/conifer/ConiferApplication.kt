package eu.heha.conifer

import android.app.Application
import io.github.aakira.napier.DebugAntilog

class ConiferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ConiferApp.initialize(
            antilog = DebugAntilog(),
            platform = AndroidPlatform,
            databaseInitializer = AndroidDatabaseInitializer(this),
            clipboardController = AndroidClipboardController(this)
        )
    }
}