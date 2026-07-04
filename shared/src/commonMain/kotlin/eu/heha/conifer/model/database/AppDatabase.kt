package eu.heha.conifer.model.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

const val DATABASE_NAME = "conifer_database.db"

@Database(
    entities = [Bit::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migration1to2::class)
    ]
)
@TypeConverters(DatabaseConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bitDao(): BitDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}