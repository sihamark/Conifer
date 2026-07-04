package eu.heha.conifer.model.database

import androidx.room3.DeleteColumn
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

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