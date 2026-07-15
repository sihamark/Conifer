package eu.heha.conifer.model.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

const val DATABASE_NAME = "conifer_database.db"

@Database(
    entities = [Bit::class],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migration1to2::class),
        AutoMigration(from = 2, to = 3, spec = Migration2to3::class)
    ]
)
@ColumnTypeConverters(DatabaseConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bitDao(): BitDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}