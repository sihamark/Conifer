package eu.heha.conifer.model.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

@Dao
interface BitDao {

    @Query("SELECT * FROM bits ORDER BY date DESC, created_at DESC")
    fun getAllBits(): Flow<List<Bit>>

    @Query("SELECT EXISTS(SELECT 1 FROM bits WHERE date = :date)")
    suspend fun hasBitAtDate(date: LocalDateTime): Boolean

    @Upsert
    suspend fun upsert(bit: Bit)

    @Delete
    suspend fun delete(bit: Bit)

    @Query("SELECT * FROM bits WHERE id IN (:bitIds) ORDER BY date DESC, created_at DESC")
    suspend fun getBitsByIds(bitIds: List<String>): List<Bit>
}