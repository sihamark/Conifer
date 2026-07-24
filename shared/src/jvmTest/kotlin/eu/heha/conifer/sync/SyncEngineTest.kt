package eu.heha.conifer.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.model.database.ReadablePending
import eu.heha.conifer.prefs.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Integration tests for [SyncEngine] (Nextcloud sync spec §5/§11) against a real, temp-file
 * backed [AppDatabase] per simulated device and an in-memory [FakeRemoteStore] standing in for
 * the shared Nextcloud server.
 */
class SyncEngineTest {

    @Test
    fun roundtripSyncsANewBitFromOneDeviceToAnother() = runTest {
        val server = FakeRemoteStore()
        val deviceA = device(server)
        val deviceB = device(server)

        val bit = Bit(text = "hello from A", createdAt = BASE_TIME, date = BASE_DATE)
        deviceA.dao().upsert(bit)

        deviceA.engine.sync()
        deviceB.engine.sync()

        val onB = deviceB.dao().bit(bit.id)
        assertEquals("hello from A", onB?.text)
        assertEquals(false, onB?.dirty)
    }

    @Test
    fun secondSyncWithNothingChangedTakesTheFastPath() = runTest {
        val server = FakeRemoteStore()
        val device = device(server)
        device.dao().upsert(Bit(text = "only bit", createdAt = BASE_TIME, date = BASE_DATE))
        device.engine.sync() // also drains readable_pending via the readable module (spec §7)

        val counting = CountingRemoteStore(server)
        SyncEngine(counting, device.databaseController, device.syncPrefs).sync()

        assertEquals(1, counting.callCount)
    }

    @Test
    fun sequentialConflictingEditsConvergeOnTheNewerModification() = runTest {
        val server = FakeRemoteStore()
        val deviceA = device(server)
        val deviceB = device(server)

        val original =
            Bit(text = "original", createdAt = BASE_TIME, date = BASE_DATE, modifiedAt = BASE_TIME)
        deviceA.dao().upsert(original)
        deviceA.engine.sync()
        deviceB.engine.sync() // B now has a clean copy too

        // both devices edit their own copy offline, A first, B later
        val onA = deviceA.dao().bit(original.id)!!
        deviceA.dao().upsert(
            onA.copy(
                text = "edited on A",
                dirty = true,
                modifiedAt = BASE_TIME + 1.minutes,
                modifiedBy = "device-a"
            )
        )
        val onB = deviceB.dao().bit(original.id)!!
        deviceB.dao().upsert(
            onB.copy(
                text = "edited on B",
                dirty = true,
                modifiedAt = BASE_TIME + 5.minutes,
                modifiedBy = "device-b"
            )
        )

        deviceA.engine.sync() // pushes A's edit
        deviceB.engine.sync() // pulls A's edit, but B's own edit is newer -> B keeps it and pushes it

        val resultOnB = deviceB.dao().bit(original.id)
        assertEquals("edited on B", resultOnB?.text)
        assertEquals(false, resultOnB?.dirty)

        deviceA.engine.sync() // A pulls B's winning edit
        assertEquals("edited on B", deviceA.dao().bit(original.id)?.text)
    }

    @Test
    fun aRaceBetweenThisDevicesPullAndPushIsResolvedByA412RetryAndMerge() = runTest {
        val server = FakeRemoteStore()
        val deviceA = device(server) // authors the edit that races in
        val deviceB = device(server) // its own push races against that edit

        val original =
            Bit(text = "original", createdAt = BASE_TIME, date = BASE_DATE, modifiedAt = BASE_TIME)
        deviceB.dao().upsert(original)
        deviceB.engine.sync()
        deviceA.engine.sync() // A has a clean copy too, ready to race in an edit

        val onB = deviceB.dao().bit(original.id)!!
        deviceB.dao().upsert(
            onB.copy(
                text = "B's edit",
                dirty = true,
                modifiedAt = BASE_TIME + 5.minutes,
                modifiedBy = "device-b"
            )
        )

        // Fires the instant deviceB's own pull step finishes listing buckets - i.e. exactly the
        // race window the spec's 412 handling exists for: a write that lands strictly between
        // this device's pull and its own push within the same sync run.
        val racedEngine = SyncEngine(
            remoteStore = RaceInjectingRemoteStore(server) {
                val onA = deviceA.dao().bit(original.id)!!
                deviceA.dao().upsert(
                    onA.copy(
                        text = "A's racing edit",
                        dirty = true,
                        modifiedAt = BASE_TIME + 10.minutes,
                        modifiedBy = "device-a"
                    )
                )
                deviceA.engine.sync()
            },
            databaseController = deviceB.databaseController,
            syncPrefs = deviceB.syncPrefs,
        )
        racedEngine.sync()

        // A's racing edit is newer -> it wins; B's conflicting push gets refetched and merged,
        // settling clean on A's content instead of endlessly retrying or corrupting anything.
        val resultOnB = deviceB.dao().bit(original.id)
        assertEquals("A's racing edit", resultOnB?.text)
        assertEquals(false, resultOnB?.dirty)
    }

    @Test
    fun aSyncThatFailsPartwayLeavesConsistentStateForTheNextRun() = runTest {
        val server = FakeRemoteStore()
        val device = device(server)
        val first = Bit(text = "first", createdAt = BASE_TIME, date = BASE_DATE)
        val second = Bit(text = "second", createdAt = BASE_TIME, date = BASE_DATE2)
        device.dao().upsert(first)
        device.dao().upsert(second)

        val flakyEngine = SyncEngine(
            FailFirstMkdirsRemoteStore(server),
            device.databaseController,
            device.syncPrefs
        )
        assertFailsWith<RuntimeException> { flakyEngine.sync() }

        // aborted before anything reached the server - both bits are still dirty, nothing lost
        assertEquals(setOf(first.id, second.id), device.dao().dirtyNew().map { it.id }.toSet())

        // a clean retry converges without duplicating or losing anything
        SyncEngine(server, device.databaseController, device.syncPrefs).sync()
        val otherDevice = device(server)
        otherDevice.engine.sync()

        val bitsOnOtherDevice = otherDevice.dao().bitsForDay(BASE_DATE.date)
        assertEquals(setOf("first", "second"), bitsOnOtherDevice.map { it.text }.toSet())
    }

    @Test
    fun syncRendersAReadableMarkdownFileForTheBitsDay() = runTest {
        val server = FakeRemoteStore()
        val device = device(server)
        device.dao().upsert(Bit(text = "hello readable", createdAt = BASE_TIME, date = BASE_DATE))

        device.engine.sync()

        val markdown = server.get("Conifer/2025-07/2025-07-13.md").decodeToString()
        assertEquals(true, markdown.contains("# 2025-07-13"))
        assertEquals(true, markdown.contains("hello readable"))
        assertEquals(false, device.dao().hasPendingReadableDays())
    }

    @Test
    fun aReDatedBitDisappearsFromItsOldDayFileAndAppearsInTheNewOneOnAPullingDevice() = runTest {
        val server = FakeRemoteStore()
        val deviceA = device(server)
        val deviceB = device(server)

        val original = Bit(text = "movable", createdAt = BASE_TIME, date = BASE_DATE)
        deviceA.dao().upsert(original)
        deviceA.engine.sync()
        deviceB.engine.sync() // B has a clean copy, day 2025-07-13, and has rendered it too

        val onA = deviceA.dao().bit(original.id)!!
        deviceA.dao().upsert(
            onA.copy(date = OTHER_DAY_DATE, dirty = true, modifiedAt = BASE_TIME + 1.minutes)
        )
        deviceA.engine.sync() // pushes the re-date

        deviceB.engine.sync() // pulls it - must re-render both the old and new day on B

        val oldDayFile = server.get("Conifer/2025-07/2025-07-13.md").decodeToString()
        val newDayFile = server.get("Conifer/2025-07/${OTHER_DAY_DATE.date}.md").decodeToString()
        assertEquals(false, oldDayFile.contains("movable"))
        assertEquals(true, newDayFile.contains("movable"))
        // re-dating never changes the immutable bucket (spec invariant I1)
        assertEquals(original.bucket, deviceB.dao().bit(original.id)?.bucket)
    }

    @Test
    fun aBitReDatedAndPushedByTheSameDeviceInvalidatesItsOwnOldDayFile() = runTest {
        val server = FakeRemoteStore()
        val device = device(server)
        val original = Bit(text = "movable", createdAt = BASE_TIME, date = BASE_DATE)
        device.dao().upsert(original)
        device.engine.sync() // pushed and rendered under 2025-07-13

        val stored = device.dao().bit(original.id)!!
        device.dao().upsert(
            stored.copy(date = OTHER_DAY_DATE, dirty = true, modifiedAt = BASE_TIME + 1.minutes)
        )
        device.engine.sync() // this device both pushes AND re-renders its own old day

        val oldDayFile = server.get("Conifer/2025-07/2025-07-13.md").decodeToString()
        val newDayFile = server.get("Conifer/2025-07/${OTHER_DAY_DATE.date}.md").decodeToString()
        assertEquals(false, oldDayFile.contains("movable"))
        assertEquals(true, newDayFile.contains("movable"))
    }

    @Test
    fun aSecondSyncSkipsReUploadingAnUnchangedReadableRendering() = runTest {
        val server = FakeRemoteStore()
        val device = device(server)
        device.dao().upsert(Bit(text = "stable", createdAt = BASE_TIME, date = BASE_DATE))
        device.engine.sync()
        val etagAfterFirstRender = server.etag("Conifer/2025-07/2025-07-13.md")

        // Re-queue the day without touching its content - simulates it being pulled back into
        // readable_pending as a side effect unrelated to this day's own content, e.g. another
        // day's push. The readable module must recognize the rendering is unchanged and skip it.
        device.dao().markReadablePending(ReadablePending(BASE_DATE.date))
        device.engine.sync()

        assertEquals(etagAfterFirstRender, server.etag("Conifer/2025-07/2025-07-13.md"))
        assertEquals(false, device.dao().hasPendingReadableDays())
    }

    private fun device(remoteStore: RemoteStore): TestDevice {
        val dbFile = Files.createTempDirectory("conifer-sync-engine-test").resolve("test.db")
        val database = Room.databaseBuilder<AppDatabase>(name = dbFile.toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        val databaseController = DatabaseController(object : DatabaseInitializer {
            override fun createDatabase() = database
        })
        val syncPrefs = SyncPrefs(InMemoryPreferencesStore())
        return TestDevice(
            databaseController,
            syncPrefs,
            SyncEngine(remoteStore, databaseController, syncPrefs)
        )
    }
}

private val BASE_TIME = Instant.fromEpochMilliseconds(1_752_408_000_000) // 2025-07-13T12:00:00Z
private val BASE_DATE = LocalDateTime(2025, 7, 13, 8, 0)
private val BASE_DATE2 = LocalDateTime(2025, 7, 13, 8, 1)
private val OTHER_DAY_DATE = LocalDateTime(2025, 7, 20, 9, 0)

private class TestDevice(
    val databaseController: DatabaseController,
    val syncPrefs: SyncPrefs,
    val engine: SyncEngine,
) {
    fun dao() = databaseController.syncDao()
}

private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private class CountingRemoteStore(private val delegate: RemoteStore) : RemoteStore {
    var callCount = 0
        private set

    override suspend fun etag(path: String): String? {
        callCount++
        return delegate.etag(path)
    }

    override suspend fun list(path: String): List<RemoteStore.Entry> {
        callCount++
        return delegate.list(path)
    }

    override suspend fun get(path: String): ByteArray {
        callCount++
        return delegate.get(path)
    }

    override suspend fun put(
        path: String,
        body: ByteArray,
        ifMatch: String?,
        ifNoneMatchAll: Boolean
    ): String {
        callCount++
        return delegate.put(path, body, ifMatch, ifNoneMatchAll)
    }

    override suspend fun mkdirs(path: String) {
        callCount++
        delegate.mkdirs(path)
    }

    override suspend fun bulkPut(files: List<RemoteStore.BulkFile>): List<String> {
        callCount++
        return delegate.bulkPut(files)
    }
}

/** Runs [onFirstList] once, right after [delegate]'s first [list] call returns. */
private class RaceInjectingRemoteStore(
    private val delegate: RemoteStore,
    private val onFirstList: suspend () -> Unit,
) : RemoteStore by delegate {
    private var injected = false

    override suspend fun list(path: String): List<RemoteStore.Entry> {
        val result = delegate.list(path)
        if (!injected) {
            injected = true
            onFirstList()
        }
        return result
    }
}

/** Fails the first [mkdirs] call unconditionally, simulating a network failure mid-push. */
private class FailFirstMkdirsRemoteStore(private val delegate: RemoteStore) :
    RemoteStore by delegate {
    private var failed = false

    override suspend fun mkdirs(path: String) {
        if (!failed) {
            failed = true
            throw RuntimeException("simulated network failure")
        }
        delegate.mkdirs(path)
    }
}
