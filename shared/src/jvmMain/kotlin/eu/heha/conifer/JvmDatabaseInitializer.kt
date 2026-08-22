package eu.heha.conifer

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.DATABASE_NAME
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import java.io.File

object JvmDatabaseInitializer : DatabaseInitializer {
    override fun createDatabase(): AppDatabase {
        // The database is opened once the app is running and so once the log is, which is why the
        // folder says its piece here rather than where it is resolved - see [logDataFolder].
        logDataFolder()
        val dbFile = File(jvmDataFolder, DATABASE_NAME)
        Napier.i("Database file path: ${dbFile.absolutePath}")
        return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
