package eu.heha.conifer.model.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BitDao {

    @Query("SELECT * FROM bits ORDER BY concerned_at DESC, created_at DESC")
    fun getAllBits(): Flow<List<Bit>>

    @Upsert
    suspend fun upsert(bit: Bit)

    @Query("SELECT * FROM bits WHERE id IN (:bitIds) ORDER BY concerned_at DESC, created_at DESC")
    fun getBitsByIds(bitIds: List<String>): List<Bit>
}