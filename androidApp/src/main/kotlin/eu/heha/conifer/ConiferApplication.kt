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
            preferencesInitializer = AndroidPreferencesInitializer(this),
            credentialsInitializer = AndroidCredentialsInitializer(this),
            browserOpener = AndroidBrowserOpener(this),
            clipboardController = AndroidClipboardController(this),
            reportShareController = AndroidReportShareController(this),
            logFileInitializer = AndroidLogFileInitializer(this),
            uncaughtErrorInitializer = AndroidUncaughtErrorInitializer,
            logClosingInitializer = AndroidLogClosingInitializer(this)
        )
    }
}