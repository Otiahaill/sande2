package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.Outbox
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(outbox: Outbox)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(outbox: List<Outbox>)

    @Query("SELECT * FROM outbox WHERE synced = 0 ORDER BY createdAt ASC LIMIT :limit")
    fun getUnsynced(limit: Int): Flow<List<Outbox>>

    @Update
    suspend fun update(outbox: Outbox)

    @Query("UPDATE outbox SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM outbox WHERE synced = 1")
    suspend fun deleteSynced()
}
