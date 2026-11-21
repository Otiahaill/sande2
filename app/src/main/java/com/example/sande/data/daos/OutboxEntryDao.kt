package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.OutboxEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: OutboxEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<OutboxEntry>)

    @Update
    suspend fun update(entry: OutboxEntry)

    @Query("SELECT * FROM outbox_entries WHERE id = :id")
    suspend fun getById(id: String): OutboxEntry?

    @Query("SELECT * FROM outbox_entries WHERE businessId = :businessId ORDER BY createdAt ASC")
    fun getByBusiness(businessId: String): Flow<List<OutboxEntry>>

    @Query("SELECT * FROM outbox_entries WHERE businessId = :businessId AND synced = 0 ORDER BY createdAt ASC")
    fun getUnsyncedByBusiness(businessId: String): Flow<List<OutboxEntry>>

    @Query("SELECT * FROM outbox_entries WHERE synced = 0 ORDER BY createdAt ASC")
    fun getAllUnsynced(): Flow<List<OutboxEntry>>

    @Query("SELECT * FROM outbox_entries WHERE batchId = :batchId")
    suspend fun getByBatchId(batchId: String): List<OutboxEntry>

    @Query("SELECT * FROM outbox_entries WHERE businessId = :businessId AND entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForEntity(businessId: String, entityType: String, entityId: String): OutboxEntry?

    @Query("UPDATE outbox_entries SET synced = 1, syncedAt = :syncedAt, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, syncedAt: Long, updatedAt: Long)

    @Query("UPDATE outbox_entries SET attemptCount = attemptCount + 1, lastError = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun recordAttempt(id: String, error: String, updatedAt: Long)

    @Query("DELETE FROM outbox_entries WHERE businessId = :businessId AND synced = 1 AND syncedAt < :threshold")
    suspend fun cleanupSynced(businessId: String, threshold: Long): Int
}
