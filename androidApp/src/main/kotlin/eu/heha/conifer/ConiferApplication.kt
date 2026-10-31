package eu.heha.conifer

import android.app.Application
import eu.heha.conifer.net.coniferUserAgent
import eu.heha.conifer.update.GitHubReleases

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
            appPresenceInitializer = AndroidAppPresenceInitializer(this),
            updateSource = GitHubReleases(coniferUserAgent(AndroidPlatform))
        )
    }
}