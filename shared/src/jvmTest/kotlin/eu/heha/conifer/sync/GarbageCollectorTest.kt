package eu.heha.conifer.sync

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * [GarbageCollector] tombstone retention/physical-deletion behavior (Nextcloud sync spec §8).
 */
class GarbageCollectorTest {

    @Test
    fun physicallyDeletesATombstoneOlderThanTheRetentionWindow() = runTest {
        val server = FakeRemoteStore()
        val controller = databaseController()
        server.mkdirs("Conifer")
        server.mkdirs("Conifer/2025-07")
        val path = "Conifer/2025-07/old-tombstone.json"
        server.put(path, "{}".encodeToByteArray(), ifNoneMatchAll = true)
        controller.syncDao().upsert(tombstone(modifiedAt = OLD_TIME))

        GarbageCollector(server, controller).collect("Conifer")

        assertNull(controller.syncDao().bit(TOMBSTONE_ID))
        assertNull(server.etag(path))
    }

    @Test
    fun leavesARecentTombstoneUntouched() = runTest {
        val server = FakeRemoteStore()
        val controller = databaseController()
        server.mkdirs("Conifer")
        server.mkdirs("Conifer/2025-07")
        val path = "Conifer/2025-07/old-tombstone.json"
        server.put(path, "{}".encodeToByteArray(), ifNoneMatchAll = true)
        controller.syncDao().upsert(tombstone(modifiedAt = Clock.System.now() - 1.days))

        GarbageCollector(server, controller).collect("Conifer")

        assertEquals(TOMBSTONE_ID, controller.syncDao().bit(TOMBSTONE_ID)?.id)
        assertEquals(true, server.etag(path) != null)
    }

    @Test
    fun leavesAnUnpushedTombstoneAlone() = runTest {
        val server = FakeRemoteStore()
        val controller = databaseController()
        // dirty = true: the deletion itself hasn't reached the server yet, so there is nothing
        // safe to garbage-collect - physically deleting now could lose the tombstone entirely
        // before any other device ever learns about it.
        controller.syncDao().upsert(tombstone(modifiedAt = OLD_TIME, dirty = true))

        GarbageCollector(server, controller).collect("Conifer")

        assertEquals(TOMBSTONE_ID, controller.syncDao().bit(TOMBSTONE_ID)?.id)
    }

    private fun tombstone(modifiedAt: Instant, dirty: Boolean = false) = Bit(
        id = TOMBSTONE_ID,
        text = "",
        createdAt = BASE_TIME,
        date = BASE_DATE,
        deleted = true,
        dirty = dirty,
        modifiedAt = modifiedAt,
        modifiedBy = "device-a",
        remoteEtag = "etag-0",
        bucket = "2025-07",
    )

    private fun databaseController(): DatabaseController {
        val dbFile = Files.createTempDirectory("conifer-gc-test").resolve("test.db")
        val database = Room.databaseBuilder<AppDatabase>(name = dbFile.toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        return DatabaseController(object : DatabaseInitializer {
            override fun createDatabase() = database
        })
    }
}

private const val TOMBSTONE_ID = "old-tombstone"
private val BASE_TIME = Instant.fromEpochMilliseconds(1_752_408_000_000) // 2025-07-13T12:00:00Z
private val BASE_DATE = LocalDateTime(2025, 7, 13, 8, 0)
private val OLD_TIME = Clock.System.now() - 91.days
