package eu.heha.conifer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.heha.conifer.auth.Credentials
import eu.heha.conifer.di.coreModule
import eu.heha.conifer.di.platformModule
import eu.heha.conifer.log.FileAntilog
import eu.heha.conifer.log.logFileName
import eu.heha.conifer.net.coniferUserAgent
import eu.heha.conifer.ui.BitsRoute
import eu.heha.conifer.ui.LocalDateTimeFormats
import eu.heha.conifer.ui.theme.ConiferTheme
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.compose.getKoin
import org.koin.core.context.startKoin
import kotlin.time.Clock

object ConiferApp {

    fun initialize(
        isDebug: Boolean,
        platform: Platform,
        dateTimeFormats: DateTimeFormats,
        databaseInitializer: DatabaseInitializer,
        syncPrefsInitializer: SyncPrefsInitializer,
        credentialsInitializer: CredentialsInitializer,
        browserOpener: BrowserOpener,
        clipboardController: ClipboardController? = null,
        logFileInitializer: LogFileInitializer? = null,
    ) {
        // DebugAntilog derives its tag from the runtime stack trace, which breaks under
        // R8/ProGuard optimization in release builds — so only install it for debug builds.
        if (isDebug) {
            Napier.base(DebugAntilog())
        }
        startLogFile(logFileInitializer, platform)
        startKoin {
            modules(
                coreModule,
                platformModule(
                    platform,
                    dateTimeFormats,
                    databaseInitializer,
                    syncPrefsInitializer,
                    credentialsInitializer,
                    browserOpener,
                    clipboardController
                )
            )
        }
    }

    /**
     * Starts this run's own log file (a new one per app start, see [logFileName]) and mirrors
     * every [Napier] call into it from here on - the sync stack's running commentary is what
     * makes a "it didn't sync and I don't know why" report answerable at all.
     *
     * Best-effort by design: a platform without a writable file system (web) or with an
     * unwritable folder passes/returns null and the app simply runs without a log file.
     */
    private fun startLogFile(initializer: LogFileInitializer?, platform: Platform) {
        val startedAt = Clock.System.now()
        val sink = initializer?.createLogFile(logFileName(startedAt)) ?: return
        Napier.base(FileAntilog(sink))
        Napier.i { "--- log started, ${coniferUserAgent(platform)}, file ${sink.location} ---" }
    }

    @Composable
    fun AppContent(permissionHandler: PermissionHandler? = null) {
        // A no-op everywhere except the web target, where KSafe needs to finish an async
        // WebCrypto cache load before Credentials (read synchronously) can see real values.
        var credentialsReady by remember { mutableStateOf(false) }
        val koin = getKoin()
        LaunchedEffect(Unit) {
            koin.get<CredentialsInitializer>().awaitCredentialsReady()
            val credentials = koin.get<Credentials>()
            if (!credentials.isKeySecurelyStored) {
                // Not a fatal condition (the data is still AES-256-GCM encrypted either way, see
                // Credentials.isKeySecurelyStored) - but a silent key-custody downgrade to a
                // filesystem-permission-only fallback should never go unnoticed.
                Napier.w {
                    "credentials encryption key is not stored in an OS-backed secure store " +
                            "(KSafe fell back to its software tier) - see KSafe.protectionInfo for details"
                }
            }
            credentialsReady = true
        }
        if (!credentialsReady) return

        ConiferTheme {
            // The one place the platform's spellings are handed to the screen; everything below
            // reads them off LocalDateTimeFormats rather than being threaded them one at a time.
            CompositionLocalProvider(LocalDateTimeFormats provides koin.get<DateTimeFormats>()) {
                BitsRoute(permissionHandler)
            }
        }
    }
}