package eu.heha.conifer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.heha.conifer.ConiferApp.installUncaughtErrorHandler
import eu.heha.conifer.auth.Credentials
import eu.heha.conifer.di.coreModule
import eu.heha.conifer.di.platformModule
import eu.heha.conifer.log.CrashBreadcrumb
import eu.heha.conifer.log.CrashBreadcrumbStore
import eu.heha.conifer.log.CrashReports
import eu.heha.conifer.log.FileAntilog
import eu.heha.conifer.log.logFileName
import eu.heha.conifer.log.logUncaughtError
import eu.heha.conifer.log.readCrashBreadcrumb
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
        preferencesInitializer: PreferencesInitializer,
        credentialsInitializer: CredentialsInitializer,
        browserOpener: BrowserOpener,
        clipboardController: ClipboardController? = null,
        reportShareController: ReportShareController? = null,
        logFileInitializer: LogFileInitializer? = null,
        uncaughtErrorInitializer: UncaughtErrorInitializer? = null,
    ) {
        // DebugAntilog derives its tag from the runtime stack trace, which breaks under
        // R8/ProGuard optimization in release builds — so only install it for debug builds.
        if (isDebug) {
            Napier.base(DebugAntilog())
        }
        val fileAntilog = startLogFile(logFileInitializer, platform)
        // Read before the handler that writes it is installed, so what the screen reports is the
        // crash of the run before this one and never one of this run's own.
        val breadcrumbs = logFileInitializer?.createCrashBreadcrumbStore()
        val crashReports = CrashReports(
            store = breadcrumbs,
            lastCrash = readLastCrash(breadcrumbs),
            // The log files are read back through the same thing that wrote them, and the report
            // names the device the same way the log's own header does.
            logFiles = logFileInitializer,
            userAgent = coniferUserAgent(platform),
        )
        installUncaughtErrorHandler(uncaughtErrorInitializer, fileAntilog, breadcrumbs)
        startKoin {
            modules(
                coreModule,
                platformModule(
                    platform,
                    dateTimeFormats,
                    databaseInitializer,
                    preferencesInitializer,
                    credentialsInitializer,
                    browserOpener,
                    clipboardController,
                    reportShareController,
                    crashReports
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
     * unwritable folder passes/returns null and the app simply runs without a log file - which is
     * what the returned null then means to [installUncaughtErrorHandler].
     */
    private fun startLogFile(initializer: LogFileInitializer?, platform: Platform): FileAntilog? {
        val startedAt = Clock.System.now()
        val sink = initializer?.createLogFile(logFileName(startedAt)) ?: return null
        val antilog = FileAntilog(sink)
        Napier.base(antilog)
        Napier.i { "--- log started, ${coniferUserAgent(platform)}, file ${sink.location} ---" }
        // Its own line, and the second one, so that a log opened by someone else answers "which
        // build is this?" before it answers anything else (see buildLabel).
        Napier.i { "--- build ${buildLabel()} ---" }
        return antilog
    }

    /**
     * Reads the crash the previous run left behind, if it left one, and says so in this run's log -
     * a log that opens with "the run before this one crashed" is a good deal easier to read than one
     * that leaves the reader to line two files up by hand.
     *
     * The breadcrumb itself is kept until the user dismisses the banner it feeds
     * ([eu.heha.conifer.log.CrashReports.forget]), so a crash survives a restart the user made
     * before they got round to reporting it.
     */
    private fun readLastCrash(breadcrumbs: CrashBreadcrumbStore?): CrashBreadcrumb? {
        val lastCrash = readCrashBreadcrumb(breadcrumbs) ?: return null
        Napier.i {
            "the previous run ended in an uncaught error at ${lastCrash.at}, " +
                    "in build ${lastCrash.buildLabel} - see ${lastCrash.logFile}"
        }
        return lastCrash
    }

    /**
     * Sends every error nothing caught - on any thread, in any coroutine, thrown out of a
     * composable - to this run's log file before the app goes down, so that the crash which ended a
     * run is the last thing the log of that run says. Without this the run just stops mid-sentence,
     * and a log shared afterwards has no answer for why.
     *
     * The same error also goes into [breadcrumbs] in short form, which is what the next run reads to
     * offer the crash for sharing - the log file alone waits for somebody to think of looking.
     *
     * Installed here rather than lazily, and right after the log file, so that the window in which a
     * crash goes unrecorded is as small as the app can make it: everything after this line - Koin,
     * the database, the first composition - is covered.
     *
     * Only the log gains anything. The platform's own crash handling still runs afterwards
     * untouched, so the Android crash dialog, the stack trace on stderr and the iOS crash report all
     * happen exactly as they did before (see [UncaughtErrorInitializer]).
     */
    private fun installUncaughtErrorHandler(
        initializer: UncaughtErrorInitializer?,
        fileAntilog: FileAntilog?,
        breadcrumbs: CrashBreadcrumbStore?,
    ) {
        initializer?.installHandler { error -> logUncaughtError(error, fileAntilog, breadcrumbs) }
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