package eu.heha.conifer

import android.app.Application

class ConiferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ConiferApp.initialize(
            isDebug = BuildConfig.DEBUG,
            platform = AndroidPlatform,
            dateTimeFormats = AndroidDateTimeFormats(),
            databaseInitializer = AndroidDatabaseInitializer(this),
            syncPrefsInitializer = AndroidSyncPrefsInitializer(this),
            credentialsInitializer = AndroidCredentialsInitializer(this),
            browserOpener = AndroidBrowserOpener(this),
            clipboardController = AndroidClipboardController(this),
            logFileInitializer = AndroidLogFileInitializer(this)
        )
    }
}