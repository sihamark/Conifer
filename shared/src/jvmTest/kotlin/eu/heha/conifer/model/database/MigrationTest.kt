package eu.heha.conifer.model.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MigrationTest {

    private fun helper() = MigrationTestHelper(
        schemaDirectoryPath = schemasDirectory(),
        databasePath = Files.createTempDirectory("conifer-migration-test").resolve("test.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class
    )

    // The test usually runs with the module directory as working directory, but also resolve
    // from the repository root so the test does not depend on how Gradle is invoked.
    private fun schemasDirectory(): Path =
        listOf("schemas", "shared/schemas").map(Paths::get).first(Files::exists)

    @Test
    fun `migrate from 2 to 3 converts the date to an ISO local date-time`() = runTest {
        val helper = helper()
        helper.createDatabase(2).use { connection ->
            connection.execSQL(
                "INSERT INTO bits (id, text, created_at, date) " +
                        "VALUES ('bit-1', 'hello', 1000, $DATE_MILLIS)"
            )
        }

        val storedDate = helper.runMigrationsAndValidate(3).use { it.dateOfFirstBit() }

        assertEquals(expectedLocalDateTime(), storedDate)
    }

    @Test
    fun `migrate from 1 to 3 initializes the date from created_at`() = runTest {
        val helper = helper()
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO bits (id, text, created_at, concerned_at) " +
                        "VALUES ('bit-1', 'hello', $DATE_MILLIS, NULL)"
            )
        }

        val storedDate = helper.runMigrationsAndValidate(3).use { it.dateOfFirstBit() }

        assertEquals(expectedLocalDateTime(), storedDate)
    }

    private fun SQLiteConnection.dateOfFirstBit(): String =
        prepare("SELECT date FROM bits WHERE id = 'bit-1'").use { statement ->
            assertTrue(statement.step(), "expected the bit to survive the migration")
            statement.getText(0)
        }

    // The expected value is derived with the same time zone the migration uses, so the test is
    // independent of where it runs.
    private fun expectedLocalDateTime(): String =
        Instant.fromEpochMilliseconds(DATE_MILLIS)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LocalDateTime.Formats.ISO)
}

// 2025-07-13T12:00:00Z
private const val DATE_MILLIS = 1_752_408_000_000L