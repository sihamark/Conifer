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
import eu.heha.conifer.log.FileAntilog
import eu.heha.conifer.log.LastRunEnd
import eu.heha.conifer.log.LastRunRecord
import eu.heha.conifer.log.LastRunStore
import eu.heha.conifer.log.LogTailReader
import eu.heha.conifer.log.RunEndReports
import eu.heha.conifer.log.lastRunEnd
import eu.heha.conifer.log.logFileName
import eu.heha.conifer.log.logTailFileName
import eu.heha.conifer.log.logUncaughtError
import eu.heha.conifer.log.readLastRun
import eu.heha.conifer.log.writeLastRun
import eu.heha.conifer.log.writeLogClosed
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
        appPresenceInitializer: AppPresenceInitializer? = null,
    ) {
        // DebugAntilog derives its tag from the runtime stack trace, which breaks under
        // R8/ProGuard optimization in release builds — so only install it for debug builds.
        if (isDebug) {
            Napier.base(DebugAntilog())
        }
        val fileAntilog = startLogFile(logFileInitializer, platform)
        // Read before the handler that writes it is installed, so what the screen reports is the
        // ending of the run before this one and never one of this run's own.
        val lastRun = logFileInitializer?.createLastRunStore()
        val runEndReports = startRunEndReports(lastRun, logFileInitializer, fileAntilog, platform)
        installUncaughtErrorHandler(uncaughtErrorInitializer, fileAntilog, lastRun)
        val appPresence = AppPresence()
        installAppPresence(appPresenceInitializer, fileAntilog, lastRun, appPresence)
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
                    runEndReports,
                    appPresence
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
     * Works out how the run before this one ended ([lastRunEnd]) and says so in this run's log - a
     * log that opens with "the run before this one crashed" is a good deal easier to read than one
     * that leaves the reader to line two files up by hand.
     *
     * Then puts the record back with this run's log file named in it and that same ending still in
     * it, which does two things: the next start knows which log to look at if this run ends without
     * a word, and an ending the user has not dismissed yet survives a restart made before they got
     * round to reporting it ([RunEndReports.forget] is what drops it).
     */
    private fun startRunEndReports(
        lastRun: LastRunStore?,
        logFiles: LogTailReader?,
        fileAntilog: FileAntilog?,
        platform: Platform,
    ): RunEndReports {
        val lastEnd = lastRunEnd(readLastRun(lastRun), logFiles)
        when (lastEnd) {
            is LastRunEnd.Crashed -> Napier.i {
                "the previous run ended in an uncaught error at ${lastEnd.breadcrumb.at}, " +
                        "in build ${lastEnd.breadcrumb.buildLabel} - see ${lastEnd.logFile}"
            }

            is LastRunEnd.Vanished -> Napier.i {
                "the previous run stopped without closing its log " +
                        "(started ${lastEnd.run.startedAt}) - see ${lastEnd.logFile}"
            }

            null -> Unit
        }
        val runningLogFile = logTailFileName(fileAntilog?.logFileLocation)
        writeLastRun(
            lastRun,
            LastRunRecord(
                runningLogFile = runningLogFile,
                crash = (lastEnd as? LastRunEnd.Crashed)?.breadcrumb,
                vanish = (lastEnd as? LastRunEnd.Vanished)?.run,
            )
        )
        return RunEndReports(
            store = lastRun,
            lastEnd = lastEnd,
            // The log files are read back through the same thing that wrote them, and the report
            // names the device the same way the log's own header does.
            logFiles = logFiles,
            userAgent = coniferUserAgent(platform),
            runningLogFile = runningLogFile,
        )
    }

    /**
     * Hands this platform's notice that the app has been put away, or brought back, to the two
     * things that live off it (see [AppPresenceInitializer]): the log, which says goodbye so that a
     * log which simply stops means something, and [presence], which is how the rest of the app finds
     * out that nobody is looking.
     *
     * The log part is conditional on there being a log at all; the presence part is not, since a
     * platform that writes no log still has an app that can be put away.
     */
    private fun installAppPresence(
        initializer: AppPresenceInitializer?,
        fileAntilog: FileAntilog?,
        lastRun: LastRunStore?,
        presence: AppPresence,
    ) {
        initializer?.installHandler(
            onPutAway = {
                presence.onPutAway()
                fileAntilog?.closeLog()
                writeLogClosed(lastRun, true)
            },
            onBroughtBack = {
                presence.onBroughtBack()
                fileAntilog?.reopenLog()
                writeLogClosed(lastRun, false)
            },
        )
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
        lastRun: LastRunStore?,
    ) {
        initializer?.installHandler { error -> logUncaughtError(error, fileAntilog, lastRun) }
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