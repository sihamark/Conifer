package eu.heha.conifer

import android.app.Application

class ConiferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ConiferApp.initialize(
            isDebug = BuildConfig.DEBUG,
            platform = AndroidPlatform,
            databaseInitializer = AndroidDatabaseInitializer(this),
            syncPrefsInitializer = AndroidSyncPrefsInitializer(this),
            clipboardController = AndroidClipboardController(this)
        )
    }
}