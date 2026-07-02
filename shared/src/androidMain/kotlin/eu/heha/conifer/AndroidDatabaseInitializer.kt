package eu.heha.conifer

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.DATABASE_NAME
import kotlinx.coroutines.Dispatchers

class AndroidDatabaseInitializer(
    private val context: Context
) : DatabaseInitializer {
    override fun createDatabase(): AppDatabase {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(DATABASE_NAME)
        return Room.databaseBuilder<AppDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}