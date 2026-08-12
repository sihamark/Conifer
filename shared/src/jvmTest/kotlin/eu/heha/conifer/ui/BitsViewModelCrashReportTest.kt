package eu.heha.conifer.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.ClipboardController
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.ReportShareController
import eu.heha.conifer.log.CrashBreadcrumb
import eu.heha.conifer.log.CrashReports
import eu.heha.conifer.log.InMemoryCrashBreadcrumbStore
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.prefs.DraftPrefs
import eu.heha.conifer.prefs.InMemoryPreferencesStore
import eu.heha.conifer.prefs.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What the banner's buttons actually do: assemble the report - which reads the log file of the run
 * that crashed, off the main thread - and get it out of the app, or fail to and say so by falling
 * back to the clipboard.
 *
 * Set up like [BitsViewModelDraftTest], for the same reasons: a real Main dispatcher of its own, and
 * assertions that wait rather than assume.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain/resetMain
class BitsViewModelCrashReportTest {

    private lateinit var mainThread: ExecutorCoroutineDispatcher

    @BeforeTest
    fun setUp() {
        mainThread = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-main") }
            .asCoroutineDispatcher()
        Dispatchers.setMain(mainThread)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        mainThread.close()
    }

    @Test
    fun sharesTheReportWithTheLogOfTheRunThatCrashed() = runBlocking(Dispatchers.Main) {
        val sharer = RecordingShareController(isTaken = true)
        val model = viewModel(shareController = sharer)

        model.shareCrashReport()

        assertTrue(awaitTrue { sharer.shared != null }, "nothing was ever offered for sharing")
        val (fileName, report) = assertNotNull(sharer.shared)
        assertTrue(fileName.startsWith("conifer-crash-") && fileName.endsWith(".txt"), fileName)
        assertContains(report, BUILD_LABEL)
        assertContains(report, "IllegalStateException: sync bucket 2026-07 was not readable")
        assertContains(report, "Conifer (Mac OS X, Java 21)")
        assertContains(report, "pull: 3 buckets changed")
    }

    /** A report the user asked to send and which then went nowhere is the worst outcome available. */
    @Test
    fun fallsBackToTheClipboardWhenThePlatformWillNotTakeIt() = runBlocking(Dispatchers.Main) {
        val clipboard = RecordingClipboardController()
        val model = viewModel(
            shareController = RecordingShareController(isTaken = false),
            clipboardController = clipboard,
        )

        model.shareCrashReport()

        assertTrue(awaitTrue { clipboard.text != null }, "the refused report was not copied either")
        assertContains(assertNotNull(clipboard.text), "IllegalStateException")
    }

    @Test
    fun copyingPutsTheSameReportOnTheClipboard() = runBlocking(Dispatchers.Main) {
        val clipboard = RecordingClipboardController()
        val model = viewModel(clipboardController = clipboard)

        model.copyCrashReportToClipboard()

        assertTrue(awaitTrue { clipboard.text != null }, "nothing was copied")
        assertContains(assertNotNull(clipboard.text), "pull: 3 buckets changed")
    }

    /** Dismissing takes the banner down and forgets the crash, so the next start is quiet. */
    @Test
    fun dismissingForgetsTheCrash() = runBlocking(Dispatchers.Main) {
        val store =
            InMemoryCrashBreadcrumbStore("""{"buildLabel":"x","atEpochMillis":0,"origin":"main"}""")
        val model = viewModel(store = store)
        assertEquals(BREADCRUMB, model.state.lastCrash)

        model.dismissCrashReport()

        assertNull(model.state.lastCrash)
        assertNull(store.read())
    }

    private fun viewModel(
        shareController: ReportShareController? = null,
        clipboardController: ClipboardController? = null,
        store: InMemoryCrashBreadcrumbStore? = null,
    ) = BitsViewModel(
        repository = repository(),
        dateTimeFormats = IsoDateTimeFormats,
        draftPrefs = DraftPrefs(InMemoryPreferencesStore()),
        clipboardController = clipboardController,
        reportShareController = shareController,
        crashReports = CrashReports(
            store = store,
            lastCrash = BREADCRUMB,
            logFiles = { _, _ -> "12:34:50.001 I  pull: 3 buckets changed" },
            userAgent = "Conifer (Mac OS X, Java 21)",
        )
    )

    private fun repository(): BitsRepository {
        val dbFile = Files.createTempDirectory("conifer-crash-report-test").resolve("test.db")
        val database = Room.databaseBuilder<AppDatabase>(name = dbFile.toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val controller = DatabaseController(object : DatabaseInitializer {
            override fun createDatabase() = database
        })
        return BitsRepository(controller, SyncPrefs(InMemoryPreferencesStore()))
    }

    private class RecordingShareController(private val isTaken: Boolean) : ReportShareController {
        @Volatile
        var shared: Pair<String, String>? = null

        override fun share(fileName: String, text: String): Boolean {
            shared = fileName to text
            return isTaken
        }
    }

    private class RecordingClipboardController : ClipboardController {
        @Volatile
        var text: String? = null

        override fun copyToClipboard(text: String) {
            this.text = text
        }
    }

    /** As in [BitsViewModelDraftTest]: whether [condition] comes true within a few seconds. */
    private suspend fun awaitTrue(condition: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(5.seconds) {
            while (!condition()) {
                Snapshot.sendApplyNotifications()
                delay(10.milliseconds)
            }
            true
        } ?: false

    private fun assertContains(text: String, part: String) =
        assertTrue(part in text, "expected \"$part\" in:\n$text")

    private companion object {
        const val BUILD_LABEL = "1.2.4 (9), commit 29ba2459, built 2026-08-10T19:58:08Z"
        val BREADCRUMB = CrashBreadcrumb(
            buildLabel = BUILD_LABEL,
            atEpochMillis = 1_785_242_096_789,
            origin = "main",
            type = "IllegalStateException",
            message = "sync bucket 2026-07 was not readable",
            frames = listOf("at eu.heha.conifer.sync.SyncEngine.push(SyncEngine.kt:214)"),
            logFile = "/logs/conifer-2026-07-28_143201.log",
        )
    }
}
