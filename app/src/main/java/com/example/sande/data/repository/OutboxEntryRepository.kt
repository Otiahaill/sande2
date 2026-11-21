package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.OutboxEntryDao
import com.example.sande.data.entities.OutboxEntry
import kotlinx.coroutines.flow.Flow

class OutboxEntryRepository constructor(
    private val database: AppDatabase
) {
    private val dao: OutboxEntryDao = database.outboxEntryDao()

    suspend fun insert(entry: OutboxEntry) = dao.insert(entry)

    suspend fun insertAll(entries: List<OutboxEntry>) = dao.insertAll(entries)

    suspend fun update(entry: OutboxEntry) = dao.update(entry)

    suspend fun getById(id: String): OutboxEntry? = dao.getById(id)

    fun getByBusiness(businessId: String): Flow<List<OutboxEntry>> = dao.getByBusiness(businessId)

    fun getUnsyncedByBusiness(businessId: String): Flow<List<OutboxEntry>> = dao.getUnsyncedByBusiness(businessId)

    fun getAllUnsynced(): Flow<List<OutboxEntry>> = dao.getAllUnsynced()

    suspend fun getByBatchId(batchId: String): List<OutboxEntry> = dao.getByBatchId(batchId)

    suspend fun getLatestForEntity(businessId: String, entityType: String, entityId: String): OutboxEntry? = dao.getLatestForEntity(businessId, entityType, entityId)

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids, System.currentTimeMillis(), System.currentTimeMillis())

    suspend fun recordAttempt(id: String, error: String) = dao.recordAttempt(id, error, System.currentTimeMillis())

    suspend fun cleanupSynced(businessId: String, threshold: Long): Int = dao.cleanupSynced(businessId, threshold)
}
