package eu.heha.conifer.model

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class BitsRepository {

    private suspend fun dao() = DatabaseController.bitDao()

    suspend fun getBits() = dao().getAllBits()

    suspend fun add(bit: Bit): Unit = withContext(Dispatchers.IO) {
        Napier.d { "add new bit $bit" }
        var date = bit.date
        while (dao().hasBitAtDate(date)) {
            // this is to ensure the order inserted bits is the same when the date is the same,
            // this simply adds 1 millisecond so the order remains the same
            date = date.plus(1.milliseconds)
        }
        dao().upsert(
            bit.copy(
                text = bit.text.trim(),
                date = date
            )
        )
    }

    suspend fun getTextsOfBits(bitIds: List<String>) = withContext(Dispatchers.IO) {
        Napier.d { "get texts of bits with ids $bitIds" }
        dao().getBitsByIds(bitIds).map { it.text }
    }
}