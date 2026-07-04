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
        val jarFilePath = ConiferApp::class.java.protectionDomain.codeSource.location.toURI()

        val rootFile = File(jarFilePath)
            .parentFile //app folder
            .parentFile //root folder

        val dataFolder = File(rootFile, "data")
            .also { it.mkdirs() }
        val dbFile = File(dataFolder, DATABASE_NAME)
        Napier.i("Database file path: ${dbFile.absolutePath}")
        return Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}