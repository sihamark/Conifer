package eu.heha.conifer

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.DATABASE_NAME
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

object IosDatabaseInitializer : DatabaseInitializer {
    override fun createDatabase(): AppDatabase {
        val dbFilePath = documentDirectory() + "/" + DATABASE_NAME
        return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun documentDirectory(): String {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(documentDirectory?.path)
    }
}
