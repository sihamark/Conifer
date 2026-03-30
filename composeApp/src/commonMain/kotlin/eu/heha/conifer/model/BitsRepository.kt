package eu.heha.conifer.model

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class BitsRepository {

    private suspend fun dao() = DatabaseController.bitDao()

    suspend fun getBits() = dao().getAllBits()

    suspend fun add(bit: Bit): Unit = withContext(Dispatchers.IO) {
        Napier.d { "add new bit $bit" }
        dao().upsert(bit)
    }

    suspend fun getTextsOfBits(bitIds: List<String>) = withContext(Dispatchers.IO) {
        Napier.d { "get texts of bits with ids $bitIds" }
        dao().getBitsByIds(bitIds).map { it.text }
    }
}