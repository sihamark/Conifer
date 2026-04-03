package eu.heha.conifer.model.database

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@DeleteColumn(
    tableName = "bits",
    columnName = "concerned_at"
)
class Migration1to2 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        //added date column to bit, which is initialized with created_at
        connection.execSQL("UPDATE bits SET date = created_at")
    }
}