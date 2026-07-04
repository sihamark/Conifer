package eu.heha.conifer

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.DATABASE_NAME

class AndroidDatabaseInitializer(
    private val context: Context
) : DatabaseInitializer {
    override fun createBuilder(): RoomDatabase.Builder<AppDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(DATABASE_NAME)
        return Room.databaseBuilder<AppDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        )
    }

}