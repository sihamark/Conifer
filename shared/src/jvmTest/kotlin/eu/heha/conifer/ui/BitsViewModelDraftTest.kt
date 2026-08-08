package eu.heha.conifer.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.prefs.ComposerDraft
import eu.heha.conifer.prefs.DraftPrefs
import eu.heha.conifer.prefs.InMemoryPreferencesStore
import eu.heha.conifer.prefs.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The composer's draft surviving the app being put away: [BitsViewModel] restoring what
 * [DraftPrefs] holds, and keeping it up to date while the user types.
 *
 * Real dispatchers throughout (the database is a real Room one), so every assertion waits for the
 * state it is about rather than assuming a turn of the scheduler has been enough - see [awaitTrue].
 *
 * Each test body runs on [Dispatchers.Main], because that is where the screen calls the ViewModel
 * from and the ViewModel is written for exactly that: `state = state.copy(...)` reads and then
 * writes, so a call arriving on a second thread can drop what a coroutine of its own wrote in
 * between — the typed text, in the one case where this test noticed. Nothing in the app makes that
 * call off the main thread; a test that did was only testing something the app never does.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain/resetMain
class BitsViewModelDraftTest {

    private val draftPrefs = DraftPrefs(InMemoryPreferencesStore())
    private lateinit var mainThread: ExecutorCoroutineDispatcher

    @BeforeTest
    fun setUp() {
        // viewModelScope needs a Main dispatcher, and a single thread of its own is the honest
        // stand-in for one: that is what the real main thread is.
        //
        // Not Dispatchers.Unconfined, which cannot serve as Main at all — it refuses to dispatch
        // (only `yield` may ask it to), while the dispatcher setMain installs never asks whether
        // dispatching is needed and simply does. Every database query here hops to Dispatchers.IO
        // and hops back, and that hop back is a dispatch: it throws on a pool thread, where no
        // test catches it, and kotlinx-coroutines-test reports it against whichever test runs next.
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
    fun restoresTheDraftIntoTheComposer() = runBlocking(Dispatchers.Main) {
        draftPrefs.save(
            ComposerDraft(
                text = "half a thought",
                composerDate = LocalDate(2026, 8, 6),
                composerTime = LocalTime(14, 30)
            )
        )

        val model = viewModel()

        assertTrue(awaitTrue { model.state.newBitText == "half a thought" })
        assertEquals(LocalDate(2026, 8, 6), model.state.composerDate)
        assertEquals(LocalTime(14, 30), model.state.composerTime)
        assertNull(model.state.editingBitId)
    }

    @Test
    fun restoresAnInterruptedEditAsAnEdit() = runBlocking(Dispatchers.Main) {
        val repository = repository()
        val bit = Bit(text = "as it was", date = LocalDateTime(2026, 8, 6, 14, 30))
        repository.add(bit)
        draftPrefs.save(ComposerDraft(text = "as it will be", editingBitId = bit.id))

        val model = viewModel(repository)

        assertTrue(awaitTrue { model.state.newBitText == "as it will be" })
        assertEquals(bit.id, model.state.editingBitId)

        // And saving it updates that bit rather than adding a second one.
        model.onClickAdd()
        assertTrue(awaitTrue { repository.getBit(bit.id)?.text == "as it will be" })
        assertEquals(1, repository.getBits().first().size)
    }

    @Test
    fun restoresTheTextOfAnEditWhoseBitIsGone() = runBlocking(Dispatchers.Main) {
        draftPrefs.save(ComposerDraft(text = "outlived its bit", editingBitId = "deleted-bit"))

        val model = viewModel()

        assertTrue(awaitTrue { model.state.newBitText == "outlived its bit" })
        assertNull(model.state.editingBitId)
    }

    @Test
    fun typingIsStoredOnceItStandsStill() = runBlocking(Dispatchers.Main) {
        val model = viewModel()
        awaitTrue { model.state.today != LocalDate.fromEpochDays(0) }

        model.onNewBitTextChange("worth keeping")
        // What GlobalSnapshotManager does for the running app: let the snapshot the write landed in
        // reach the observers, snapshotFlow among them.
        Snapshot.sendApplyNotifications()

        assertTrue(awaitTrue { draftPrefs.draft()?.text == "worth keeping" })
    }

    @Test
    fun addingTheBitDropsTheDraft() = runBlocking(Dispatchers.Main) {
        val model = viewModel()

        model.onNewBitTextChange("added straight away")
        Snapshot.sendApplyNotifications()
        model.onClickAdd()

        assertTrue(awaitTrue { model.state.newBitText.isEmpty() })
        assertNull(draftPrefs.draft())
    }

    @Test
    fun cancellingAnEditDropsTheDraft() = runBlocking(Dispatchers.Main) {
        val repository = repository()
        val bit = Bit(text = "left alone", date = LocalDateTime(2026, 8, 6, 14, 30))
        repository.add(bit)
        val model = viewModel(repository)

        model.startEditing(bit)
        model.onNewBitTextChange("never mind")
        Snapshot.sendApplyNotifications()
        assertTrue(awaitTrue { draftPrefs.draft()?.text == "never mind" })

        model.cancelEdit()
        Snapshot.sendApplyNotifications()

        assertTrue(awaitTrue { draftPrefs.draft() == null })
        assertEquals("left alone", repository.getBit(bit.id)?.text)
    }

    private fun viewModel(repository: BitsRepository = repository()) = BitsViewModel(
        repository = repository,
        dateTimeFormats = IsoDateTimeFormats,
        draftPrefs = draftPrefs
    )

    private fun repository(): BitsRepository {
        val dbFile = Files.createTempDirectory("conifer-draft-test").resolve("test.db")
        val database = Room.databaseBuilder<AppDatabase>(name = dbFile.toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val controller = DatabaseController(object : DatabaseInitializer {
            override fun createDatabase() = database
        })
        return BitsRepository(controller, SyncPrefs(InMemoryPreferencesStore()))
    }

    /**
     * Whether [condition] holds within a few seconds, checked often enough that a test costs no
     * more than the work it is waiting for. Generous on the timeout because it is only ever reached
     * by a failing test, where a slow machine must not be the reason.
     */
    private suspend fun awaitTrue(condition: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(5.seconds) {
            while (!condition()) {
                Snapshot.sendApplyNotifications()
                delay(10.milliseconds)
            }
            true
        } ?: false
}
