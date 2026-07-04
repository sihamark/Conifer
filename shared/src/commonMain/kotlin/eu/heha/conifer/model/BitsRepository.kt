package eu.heha.conifer.model

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.milliseconds

class BitsRepository(
    private val databaseController: DatabaseController
) {

    private fun dao() = databaseController.bitDao()

    fun getBits() = dao().getAllBits()

    suspend fun add(bit: Bit) {
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

    suspend fun update(bit: Bit) {
        Napier.d { "update bit $bit" }
        dao().upsert(bit.copy(text = bit.text.trim()))
    }

    suspend fun delete(bit: Bit) {
        Napier.d { "delete bit $bit" }
        dao().delete(bit)
    }

    suspend fun getTextsOfBits(bitIds: List<String>): List<String> {
        Napier.d { "get texts of bits with ids $bitIds" }
        return dao().getBitsByIds(bitIds).map { it.text }
    }
}