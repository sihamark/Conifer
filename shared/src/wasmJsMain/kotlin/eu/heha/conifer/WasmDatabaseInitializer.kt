package eu.heha.conifer

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import eu.heha.conifer.model.database.AppDatabase
import eu.heha.conifer.model.database.DATABASE_NAME
import org.w3c.dom.Worker

object WasmDatabaseInitializer : DatabaseInitializer {
    override fun createDatabase(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = DATABASE_NAME)
            .setDriver(WebWorkerSQLiteDriver(createWorker()))
            .build()
}

/**
 * Builds the SQLite Web Worker. It is instantiated entirely in JS so the bundler can detect the
 * `new Worker(new URL(...))` pattern and emit the worker (together with its
 * `@sqlite.org/sqlite-wasm` dependency, resolved from the local `sqlite-web-worker` npm package)
 * as a module worker chunk. The worker persists the database in the Origin Private File System.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun createWorker(): Worker =
    js("new Worker(new URL('sqlite-web-worker/worker.js', import.meta.url), { type: 'module' })")