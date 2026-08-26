package eu.heha.conifer.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.prefs.DraftPrefs
import eu.heha.conifer.prefs.InMemoryPreferencesStore
import eu.heha.conifer.prefs.SyncPrefs
import eu.heha.conifer.ui.bits.DAY_LIST_PAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Which [BitsViewModel] actions ask the day lists to scroll back to today, and which deliberately
 * do not — the view model's half of the behaviour [eu.heha.conifer.ui.bits.DayListHomeScrollTest]
 * covers from the list's end. That test bumps
 * [eu.heha.conifer.ui.bits.BitsPaneState.scrollDaysHomeRequest] itself and watches the days move;
 * this one presses the actions and watches the number, so that between them the whole chain from
 * Esc to a scrolled list is held down.
 *
 * Also the day the actions move to, since every date step, skip and "today" goes through the same
 * `movedTo` — in particular that a move landing past the oldest day the lists count back to grows
 * them to reach it, rather than being clamped to what they happen to show.
 *
 * The harness: a real Room database in a temp folder, an in-memory preferences store, and
 * [BitsViewModelTestCase]'s main thread, which the test bodies run on because that is the thread the
 * screen calls these from.
 *
 * Dates are all relative to [eu.heha.conifer.ui.bits.BitsPaneState.today], which comes from the
 * clock — a test that wrote today down would pass until midnight.
 */
class BitsViewModelDayListsHomeTest : BitsViewModelTestCase() {

    @Test
    fun escWithNothingSelectedStillAsksTheDayListsHome() = runBlocking(Dispatchers.Main) {
        // The case the request exists for, and the reason it is a counter: a list scrolled a year
        // into the past with nothing selected is exactly the state in which Esc has something to
        // do, and it is also the state in which Esc changes no other field at all.
        val model = viewModel()
        val before = model.state.scrollDaysHomeRequest

        model.resetSelection()

        assertEquals(before + 1, model.state.scrollDaysHomeRequest)
        assertFalse(model.state.hasSelection)
    }

    @Test
    fun escDropsTheWholeSelectionAndAsksTheDayListsHome() = runBlocking(Dispatchers.Main) {
        val model = viewModel()
        model.selectDate(model.state.today.daysBack(3))
        model.selectTime(LocalTime(9, 15))
        val before = model.state.scrollDaysHomeRequest

        model.resetSelection()

        assertNull(model.state.filterDate)
        assertNull(model.state.composerDate)
        assertNull(model.state.composerTime)
        assertEquals(before + 1, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun everyPressIsANewRequest() = runBlocking(Dispatchers.Main) {
        // Nothing acknowledges the request, so pressing Esc twice in a row has to be two of them.
        val model = viewModel()
        val before = model.state.scrollDaysHomeRequest

        repeat(3) { model.resetSelection() }

        assertEquals(before + 3, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun allDaysAsksTheDayListsHomeAndKeepsTheTime() = runBlocking(Dispatchers.Main) {
        val model = viewModel()
        model.selectDate(model.state.today.daysBack(3))
        model.selectTime(LocalTime(9, 15))
        val before = model.state.scrollDaysHomeRequest

        model.selectAllDays()

        assertNull(model.state.filterDate)
        assertNull(model.state.composerDate)
        // A chosen time survives: "All days" is about days.
        assertEquals(LocalTime(9, 15), model.state.composerTime)
        assertEquals(before + 1, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun todayMovesTheDateAndAsksTheDayListsHomeAtOnce() = runBlocking(Dispatchers.Main) {
        val model = viewModel()
        model.shiftDate(-5)
        assertEquals(model.state.today.daysBack(5), model.state.composerDate)
        val before = model.state.scrollDaysHomeRequest

        model.selectToday()

        // Null rather than today spelled out, so the date goes on following the clock over midnight.
        assertNull(model.state.composerDate)
        assertEquals(before + 1, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun todayScrollsTheDayListsHomeWithoutShorteningThem() = runBlocking(Dispatchers.Main) {
        // Coming home is a scroll, not an undo: the days a list has counted back to stay counted,
        // so scrolling back down finds them where they were left.
        val model = viewModel(bitsOn = listOf(TODAY_BACK_100))
        awaitTrue { model.state.bitsByDate.isNotEmpty() }
        model.skipToDateWithBits(-1)
        val grown = model.state.listedDayCount
        assertTrue(grown > DAY_LIST_PAGE)

        model.selectToday()

        assertEquals(grown, model.state.listedDayCount)
    }

    @Test
    fun lettingGoOfADayLeavesTheDayListsWhereTheyAre() = runBlocking(Dispatchers.Main) {
        // Tapping the selected chip a second time drops the filter, and does it from a gesture
        // aimed at a day in view: taking those days off the screen is no part of what was asked.
        val model = viewModel()
        val day = model.state.today.daysBack(3)
        model.selectDate(day)
        val before = model.state.scrollDaysHomeRequest

        model.selectDate(day)

        assertNull(model.state.filterDate)
        assertNull(model.state.composerDate)
        assertEquals(before, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun aDayStepLeavesTheDayListsWhereTheyAre() = runBlocking(Dispatchers.Main) {
        // Stepping the date is done mid-sentence; having the days jump home underneath would be no
        // help at all.
        val model = viewModel()
        val before = model.state.scrollDaysHomeRequest

        model.shiftDate(-1)

        assertEquals(model.state.today.daysBack(1), model.state.composerDate)
        assertEquals(DAY_LIST_PAGE, model.state.listedDayCount)
        assertEquals(before, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun aStepPastTheOldestListedDayGrowsTheDayListsToReachIt() = runBlocking(Dispatchers.Main) {
        // A day the lists do not count back to is a day the user would be moved to with nothing on
        // screen saying so. Whole pages, so a list grown by a hotkey ends where a scrolled one does.
        val model = viewModel(bitsOn = listOf(TODAY_BACK_100))
        awaitTrue { model.state.bitsByDate.isNotEmpty() }
        val before = model.state.scrollDaysHomeRequest

        model.shiftDate(-40)

        assertEquals(model.state.today.daysBack(40), model.state.composerDate)
        // 41 days back, counted in pages of DAY_LIST_PAGE.
        assertEquals(2 * DAY_LIST_PAGE, model.state.listedDayCount)
        assertEquals(before, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun aSkipGrowsTheDayListsToTheDayItLandsOn() = runBlocking(Dispatchers.Main) {
        val model = viewModel(bitsOn = listOf(TODAY_BACK_100))
        awaitTrue { model.state.bitsByDate.isNotEmpty() }
        val before = model.state.scrollDaysHomeRequest

        model.skipToDateWithBits(-1)

        assertEquals(model.state.today.daysBack(100), model.state.composerDate)
        // 101 days back, which is four pages.
        assertEquals(4 * DAY_LIST_PAGE, model.state.listedDayCount)
        assertEquals(before, model.state.scrollDaysHomeRequest)
    }

    @Test
    fun aSkipWithNowhereToGoChangesNothing() = runBlocking(Dispatchers.Main) {
        val model = viewModel()
        val before = model.state

        model.skipToDateWithBits(-1)

        assertEquals(before.composerDate, model.state.composerDate)
        assertEquals(before.filterDate, model.state.filterDate)
        assertEquals(before.listedDayCount, model.state.listedDayCount)
        assertEquals(before.scrollDaysHomeRequest, model.state.scrollDaysHomeRequest)
    }

    private fun LocalDate.daysBack(days: Int) = LocalDate.fromEpochDays(toEpochDays() - days)

    private suspend fun viewModel(bitsOn: List<Int> = emptyList()): BitsViewModel {
        val repository = repository()
        for (daysBack in bitsOn) {
            val date = LocalDate.fromEpochDays(now().date.toEpochDays() - daysBack)
            repository.add(Bit(text = "on $date", date = LocalDateTime(date, LocalTime(12, 0))))
        }
        return track(
            BitsViewModel(
                repository = repository,
                dateTimeFormats = IsoDateTimeFormats,
                draftPrefs = DraftPrefs(InMemoryPreferencesStore())
            )
        )
    }

    private fun repository(): BitsRepository {
        val dbFile = Files.createTempDirectory("conifer-days-home-test").resolve("test.db")
        val database = Room.databaseBuilder<AppDatabase>(name = dbFile.toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val controller = DatabaseController(object : DatabaseInitializer {
            override fun createDatabase() = database
        })
        return BitsRepository(controller, SyncPrefs(InMemoryPreferencesStore()))
    }

    /** As [BitsViewModelDraftTest]'s: whether [condition] holds within a few seconds. */
    private suspend fun awaitTrue(condition: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(5.seconds) {
            while (!condition()) {
                Snapshot.sendApplyNotifications()
                delay(10.milliseconds)
            }
            true
        } ?: false

    private companion object {
        /** Far enough back that reaching it grows the lists by several pages. */
        const val TODAY_BACK_100 = 100
    }
}
