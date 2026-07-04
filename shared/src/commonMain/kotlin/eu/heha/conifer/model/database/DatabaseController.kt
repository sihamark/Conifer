package eu.heha.conifer.model.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.heha.conifer.DatabaseInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class DatabaseController(
    private val databaseInitializer: DatabaseInitializer
) {
    private val database: AppDatabase by lazy { getRoomDatabase() }

    private fun databaseFlow(): Flow<AppDatabase> =
        flow { emit(database) }
            .flowOn(Dispatchers.IO)

    suspend fun bitDao() = databaseFlow().first().bitDao()

    fun getRoomDatabase(): AppDatabase =
        databaseInitializer.createBuilder()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}