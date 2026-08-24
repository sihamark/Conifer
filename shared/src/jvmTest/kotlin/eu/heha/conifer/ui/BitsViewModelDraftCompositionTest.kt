package eu.heha.conifer.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.prefs.DraftPrefs
import eu.heha.conifer.prefs.InMemoryPreferencesStore
import eu.heha.conifer.prefs.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * That the draft is stored under a *running composition*, which is the only way it ever happens in
 * the app: [BitsViewModel] watches its own state through `snapshotFlow`, and what makes a snapshot
 * write reach that flow is Compose sending its apply notifications — something the app's own
 * recomposer does and a plain unit test has to stand in for by hand (see [BitsViewModelDraftTest]).
 *
 * So this test does no standing in: it composes something that reads the state and then only waits.
 */
@OptIn(ExperimentalTestApi::class)
class BitsViewModelDraftCompositionTest : BitsViewModelTestCase() {

    private val draftPrefs = DraftPrefs(InMemoryPreferencesStore())

    @Test
    fun typingIsStoredWithNothingButACompositionRunning() = runComposeUiTest {
        val model = track(
            BitsViewModel(
                repository = repository(),
                dateTimeFormats = IsoDateTimeFormats,
                draftPrefs = draftPrefs
            )
        )
        setContent {
            // Whatever reads the state is what keeps the snapshot machinery honest; the real screen
            // reads a great deal more of it.
            Text(model.state.newBitText)
        }

        // On the main thread, as the screen would type: see [BitsViewModelDraftTest] on why the
        // ViewModel's state does not survive being written from two threads at once.
        withContext(Dispatchers.Main) { model.onNewBitTextChange("typed under composition") }
        waitForIdle()

        // On a real dispatcher, and on neither of the two obvious alternatives. Not the test's own
        // context: this body runs under `runTest`, where `delay` is virtual and would spend the
        // five seconds in an instant, long before the half-second debounce this is waiting for has
        // had any real time to pass. Not `runBlocking` either: that would hold the thread running
        // the composition while asking the composition to make progress.
        val stored = withContext(Dispatchers.Default) {
            withTimeoutOrNull(5.seconds) {
                while (draftPrefs.draft() == null) delay(10.milliseconds)
                draftPrefs.draft()?.text
            }
        }

        assertEquals("typed under composition", stored)
    }

    private fun repository(): BitsRepository {
        val dbFile = Files.createTempDirectory("conifer-draft-composition-test").resolve("test.db")
        val database = Room.databaseBuilder<AppDatabase>(name = dbFile.toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val controller = DatabaseController(object : DatabaseInitializer {
            override fun createDatabase() = database
        })
        return BitsRepository(controller, SyncPrefs(InMemoryPreferencesStore()))
    }
}
