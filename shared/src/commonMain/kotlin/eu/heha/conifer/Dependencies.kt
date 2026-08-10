package eu.heha.conifer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.heha.conifer.auth.Credentials
import eu.heha.conifer.log.LogFileSink
import eu.heha.conifer.log.UncaughtError
import eu.heha.conifer.model.database.AppDatabase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

interface Platform {
    val name: String

    /**
     * Whether this kind of device is one that comes with a keyboard — what decides whether the app
     * offers to show its keyboard shortcuts at all (see `ShortcutsOverlay`). The shortcuts
     * themselves are always live; a device with no keyboard simply never sends the events.
     *
     * A property of the platform rather than of the moment, because that is the honest shape of what
     * can be known here: a desktop window or a browser tab always has a keyboard, a phone usually
     * has none, and neither answer changes while the app runs. What it cannot see is a keyboard
     * attached to a tablet later on, so the screen also watches for a modifier key actually being
     * used and takes that as the same answer — see `BitsPane`.
     */
    val hasHardwareKeyboard: Boolean

    /**
     * Whether this platform's *own* text editing moves and selects by word with Alt — which on
     * Apple's platforms it does, Alt being ⌥ there, and nowhere else. It decides which modifier the
     * screen's shortcuts are held down with (see `ShortcutChord`): where the answer is yes, ⌥←/→
     * belongs to the words in the text field and the shortcuts add Ctrl rather than take it away.
     *
     * Not "is this macOS": what matters here is the keyboard convention and not the operating
     * system, and the two only line up by coincidence. A browser is asked the same question and
     * answers for the machine it is running on, since that is whose conventions the person at it
     * has.
     */
    val usesOptionForWordJump: Boolean
}

/**
 * Namespaces KSafe's key store and data file (spec-independent hardening): JVM Desktop's OS
 * secret store is per-OS-user, shared across every app on the machine, so two apps with no
 * namespace of their own would collide on the same key. Matches `AppConfig.namespace`
 * (`buildSrc`), duplicated here since `buildSrc` isn't on the app's runtime classpath.
 */
internal const val KSAFE_APP_NAMESPACE = "eu.heha.conifer"

/**
 * Dates and times spelled the way the reader's own system spells them — which is a question only the
 * platform can answer, since it is the one holding the locale, the 12-or-24-hour setting and the
 * names of the months.
 *
 * Every method takes a date or a time and returns something to show a person. None of it is ever
 * parsed back: what goes into the database, the sync files and the logs stays ISO
 * (`printIso` and the database converters), because those are read by machines and by other
 * devices, whose locale is none of this app's business.
 *
 * The default implementation is [eu.heha.conifer.ui.IsoDateTimeFormats], which is what previews and
 * tests get: no locale of its own, so a screenshot of a preview looks the same on every machine.
 */
interface DateTimeFormats {
    /** The time of day: `14:30`, or `2:30 PM` where that is how it is written. */
    fun timeOfDay(time: LocalTime): String

    /** The weekday, shortened: `Wed`, `Mi`, `水`. For the day strip and the sidebar. */
    fun weekdayShort(date: LocalDate): String

    /** Day and month without the year, in the locale's own order: `6.8.`, `8/6`. */
    fun dayAndMonth(date: LocalDate): String

    /** A whole date, as short as it can be while still being unambiguous: `6 Aug 2026`. */
    fun date(date: LocalDate): String

    /** A whole date with the weekday written out, for a heading: `Thursday, 6 August 2026`. */
    fun dateWithWeekday(date: LocalDate): String
}

interface DatabaseInitializer {
    /**
     * Builds a fully configured [AppDatabase]. The SQLite driver and query coroutine context are
     * chosen per platform here (rather than in shared code) because the web target has no
     * `Dispatchers.IO` and uses a different, Web Worker–backed driver.
     */
    fun createDatabase(): AppDatabase
}

interface PreferencesInitializer {
    /**
     * Creates the Preferences DataStore named [fileName] — `SyncPrefs.STORE_FILE_NAME` and
     * `DraftPrefs.STORE_FILE_NAME`, which are kept apart so that sync bookkeeping and the
     * composer's unsent draft never share a file.
     *
     * Platform-specific because each platform chooses its own storage location (and the browser
     * has no file system for DataStore at all, so it stores them in `localStorage` instead).
     */
    fun createStore(fileName: String): DataStore<Preferences>
}

interface ClipboardController {
    fun copyToClipboard(text: String)
}

interface LogFileInitializer {
    /**
     * Opens [fileName] for appending in this platform's log folder, after deleting all but the
     * newest [eu.heha.conifer.log.MAX_LOG_FILES] files already there (see
     * [eu.heha.conifer.log.logFileName]).
     *
     * Returns `null` when there is nowhere to write - the web target has no file system, a
     * folder can turn out to be unwritable - because a missing log file must never stop the app
     * from starting. Implementations therefore swallow their own failures rather than throwing.
     */
    fun createLogFile(fileName: String): LogFileSink?
}

interface UncaughtErrorInitializer {
    /**
     * Installs this platform's hook for errors that nothing caught - an exception off the end of any
     * thread, an unhandled coroutine failure, a throw out of a composable - and calls
     * [report] with each one.
     *
     * Called once at startup. Implementations report and then hand the error on to whatever handled
     * it before, unchanged: the app is not being rescued here, it is being written down on its way
     * out (see [eu.heha.conifer.log.logUncaughtError]), and a crash the platform stops reporting is
     * a crash that stops reaching a crash dialog, stderr or an iOS crash report.
     *
     * [report] is called on the thread that failed, at a point where the app may have seconds to
     * live, so implementations must call it before doing anything slower.
     */
    fun installHandler(report: (UncaughtError) -> Unit)
}

interface BrowserOpener {
    /**
     * Opens [url] in the system browser, e.g. for Nextcloud Login Flow v2 (spec §10).
     *
     * Returns whether the browser actually took it. There is no shortage of ways this fails
     * through no fault of the user — a Linux JVM whose AWT reports no `Desktop.Action.BROWSE`
     * even though `xdg-open` works, a popup blocker eating `window.open` — and Login Flow v2
     * simply stalls if nobody notices, since it waits for a browser that never opened. A `false`
     * lets the caller offer the URL for the user to open by hand instead.
     *
     * Best effort in the other direction too: `true` means the platform accepted the request, not
     * that a window is definitely on screen.
     */
    fun open(url: String): Boolean
}

interface CredentialsInitializer {
    /**
     * Builds the [Credentials] store. Platform-specific because KSafe needs a `Context` on
     * Android for its Keystore-backed encryption; other platforms need no setup.
     */
    fun createCredentials(): Credentials

    /**
     * Waits until [Credentials] is safe to read. A no-op everywhere except the web target: KSafe
     * there is backed by an async WebCrypto cache load, so a synchronous read (the property
     * delegate `Credentials` uses) before it finishes would silently see defaults instead of the
     * real stored value. Call once at startup before the first read.
     */
    suspend fun awaitCredentialsReady() {}
}

interface PermissionHandler {
    val isPermissionGranted: StateFlow<Boolean>

    /**
     * Why the permission is needed. Platform-specific because the wording depends on what the
     * permission enables there (e.g. replying to a notification on Android); the implementation
     * resolves the already localized strings from its platform resources.
     */
    val permissionRationale: PermissionRationale

    fun requestPermission()
}

/** A permission rationale as a highlighted lead-in plus the actual explanation. */
data class PermissionRationale(
    val lead: String,
    val text: String
)