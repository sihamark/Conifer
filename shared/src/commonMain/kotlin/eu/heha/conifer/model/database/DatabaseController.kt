package eu.heha.conifer.model.database

import eu.heha.conifer.DatabaseInitializer

class DatabaseController(
    private val databaseInitializer: DatabaseInitializer
) {
    // Built lazily on first access. Room 3 defers opening the connection to its query context, so
    // constructing the database off a background dispatcher is no longer required here (and lets
    // this stay free of Dispatchers.IO, which does not exist on the web target).
    private val database: AppDatabase by lazy { databaseInitializer.createDatabase() }

    fun bitDao() = database.bitDao()

    fun syncDao() = database.syncDao()
}