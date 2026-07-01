package eu.heha.conifer.model.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface BitDao {

    @Query("SELECT * FROM bits ORDER BY date DESC, created_at DESC")
    fun getAllBits(): Flow<List<Bit>>

    @Query("SELECT EXISTS(SELECT 1 FROM bits WHERE date = :date)")
    suspend fun hasBitAtDate(date: Instant): Boolean

    @Upsert
    suspend fun upsert(bit: Bit)

    @Delete
    suspend fun delete(bit: Bit)

    @Query("SELECT * FROM bits WHERE id IN (:bitIds) ORDER BY date DESC, created_at DESC")
    suspend fun getBitsByIds(bitIds: List<String>): List<Bit>
}