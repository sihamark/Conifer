package eu.heha.conifer.model

import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.prefs.SyncPrefs
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Clock

class BitsRepository(
    private val databaseController: DatabaseController,
    private val syncPrefs: SyncPrefs
) {

    private fun dao() = databaseController.bitDao()

    fun getBits() = dao().getAllBits()

    suspend fun add(bit: Bit) {
        Napier.d { "add new bit $bit" }
        var date = bit.date
        while (dao().hasBitAtDate(date)) {
            // this is to ensure the order inserted bits is the same when the date is the same,
            // this simply adds 1 millisecond so the order remains the same
            date = date.plusOneMillisecond()
        }
        dao().upsert(
            bit.copy(
                text = bit.text.trim(),
                date = date,
                modifiedBy = syncPrefs.deviceId()
            )
        )
    }

    suspend fun update(bit: Bit) {
        Napier.d { "update bit $bit" }
        dao().upsert(
            bit.copy(
                text = bit.text.trim(),
                dirty = true,
                modifiedAt = Clock.System.now(),
                modifiedBy = syncPrefs.deviceId()
            )
        )
    }

    suspend fun delete(bit: Bit) {
        Napier.d { "delete bit $bit" }
        if (bit.remoteEtag == null) {
            // never reached the server, so no other device can know it — no tombstone needed
            dao().delete(bit)
        } else {
            dao().upsert(
                bit.copy(
                    text = "",
                    deleted = true,
                    dirty = true,
                    modifiedAt = Clock.System.now(),
                    modifiedBy = syncPrefs.deviceId()
                )
            )
        }
    }

    suspend fun getTextsOfBits(bitIds: List<String>): List<String> {
        Napier.d { "get texts of bits with ids $bitIds" }
        return dao().getBitsByIds(bitIds).map { it.text }
    }
}

private fun LocalDateTime.plusOneMillisecond(): LocalDateTime {
    val nanosOfDay = time.toNanosecondOfDay() + 1_000_000
    return if (nanosOfDay < NANOSECONDS_PER_DAY) {
        LocalDateTime(date, LocalTime.fromNanosecondOfDay(nanosOfDay))
    } else {
        // Only reachable when a day is completely full of same-dated bits; roll over anyway
        // instead of producing an invalid time.
        LocalDateTime(
            LocalDate.fromEpochDays(date.toEpochDays() + 1),
            LocalTime.fromNanosecondOfDay(nanosOfDay - NANOSECONDS_PER_DAY)
        )
    }
}

private const val NANOSECONDS_PER_DAY = 24L * 60 * 60 * 1_000_000_000