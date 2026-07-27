package eu.heha.conifer.sync

import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.model.database.ReadableState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

/**
 * Uploads the human-readable Markdown rendering of every day queued in `readable_pending`
 * (Nextcloud sync spec §7.2). Runs as the sync engine's step 4, after a successful pull+push.
 *
 * Strictly derived and write-only (I4): never reads or merges the remote Markdown files. A
 * day whose freshly rendered content hashes the same as [ReadableState.contentHash] is skipped
 * without a request; a failed upload simply stays in `readable_pending` for the next sync
 * instead of aborting the run (every other day still gets its chance this time around).
 */
class ReadableModule(
    private val remoteStore: RemoteStore,
    private val databaseController: DatabaseController,
) {
    // Month folders confirmed/created on the server during this instance's lifetime, so a
    // normal run doesn't re-issue the same redundant MKCOLs for every pending day. mkdirs()
    // tolerates an already-existing folder regardless, so forgetting this is harmless.
    private val knownMonths = mutableSetOf<String>()

    private fun dao() = databaseController.syncDao()

    suspend fun render(appRoot: String) {
        val days = dao().pendingReadableDays()
        if (days.isEmpty()) return
        Napier.i { "readable: ${days.size} day(s) queued for re-rendering" }
        for (day in days) {
            renderDay(appRoot, day)
        }
    }

    private suspend fun renderDay(appRoot: String, day: LocalDate) {
        val markdown = ReadableRenderer.render(day, dao().bitsForDay(day))
        val hash = markdown.encodeToByteArray().sha256Hex()
        if (hash == dao().readableContentHash(day)) {
            dao().clearReadablePending(day)
            return
        }

        try {
            val month = day.toString().substring(0, 7) // 'yyyy-MM' prefix of 'yyyy-MM-dd'
            ensureMonthExists(appRoot, month)
            remoteStore.put("$appRoot/$month/$day.md", markdown.encodeToByteArray())
            dao().upsertReadableState(ReadableState(day, hash))
            dao().clearReadablePending(day)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w(e) { "failed to upload the readable rendering for $day, retrying next sync" }
        }
    }

    private suspend fun ensureMonthExists(appRoot: String, month: String) {
        if (month in knownMonths) return
        remoteStore.mkdirs(appRoot)
        remoteStore.mkdirs("$appRoot/$month")
        knownMonths.add(month)
    }
}
