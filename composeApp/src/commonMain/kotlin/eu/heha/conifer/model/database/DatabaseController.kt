package eu.heha.conifer.model.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.ConiferApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object DatabaseController {
    private val database: AppDatabase by lazy { getRoomDatabase() }

    private fun databaseFlow(): Flow<AppDatabase> =
        flow { emit(database) }
            .flowOn(Dispatchers.IO)

    suspend fun bitDao() = databaseFlow().first().bitDao()

    fun getRoomDatabase(): AppDatabase {
        val initializer = ConiferApp.databaseInitializer
            ?: error("DatabaseInitializer is not set. Make sure ConiferApp.initialize() has been called.")
        return initializer.createBuilder()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}