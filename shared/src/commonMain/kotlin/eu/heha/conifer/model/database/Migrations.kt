package eu.heha.conifer.model.database

import androidx.room3.DeleteColumn
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.async.executeSQL
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@DeleteColumn(
    tableName = "bits",
    columnName = "concerned_at"
)
class Migration1to2 : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        //added date column to bit, which is initialized with created_at
        connection.executeSQL("UPDATE bits SET date = created_at")
    }
}

/**
 * `date` changes from an epoch-millis instant to a zone-less local date-time so a bit keeps the
 * wall-clock date and time it was entered with, even when the device's time zone changes later.
 *
 * The generated migration recreates the table for the INTEGER → TEXT affinity change but copies
 * the old epoch-millis values verbatim, so they are converted here afterwards. Each value is
 * interpreted once, in the time zone active during the migration — the entry zone was never
 * recorded, so this matches what the user currently sees.
 */
class Migration2to3 : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        val timeZone = TimeZone.currentSystemDefault()
        val datesById = mutableMapOf<String, String>()
        connection.prepare("SELECT id, date FROM bits").use { select ->
            while (select.step()) {
                val date = Instant.fromEpochMilliseconds(select.getLong(1))
                    .toLocalDateTime(timeZone)
                datesById[select.getText(0)] = date.format(LocalDateTime.Formats.ISO)
            }
        }
        datesById.forEach { (id, date) ->
            connection.prepare("UPDATE bits SET date = ? WHERE id = ?").use { update ->
                update.bindText(1, date)
                update.bindText(2, id)
                update.step()
            }
        }
    }
}

private inline fun <R> SQLiteStatement.use(block: (SQLiteStatement) -> R): R =
    try {
        block(this)
    } finally {
        close()
    }