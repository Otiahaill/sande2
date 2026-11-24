package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.CounterDao
import com.example.sande.data.daos.OutboxEntryDao
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.OutboxEntry
import com.example.sande.data.sync.OutboxPayloadSerializer
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CounterRepository constructor(
    private val database: AppDatabase
) {
    private val dao: CounterDao = database.counterDao()
    private val outboxEntryDao: OutboxEntryDao = database.outboxEntryDao()

    suspend fun insert(counter: Counter) {
        dao.insert(counter)
        enqueueCounterSync(counter)
    }

    suspend fun insertAll(counters: List<Counter>) = dao.insertAll(counters)

    /**
     * Queues the counter value for sync so the backend keeps the same
     * receipt numbering sequence as this device.
     */
    private suspend fun enqueueCounterSync(counter: Counter) {
        val now = System.currentTimeMillis()
        outboxEntryDao.insert(
            OutboxEntry(
                id = "ob_${UUID.randomUUID()}",
                businessId = counter.businessId,
                entityType = "counter",
                entityId = counter.id,
                operation = "update",
                payloadJson = OutboxPayloadSerializer.serialize(counter),
                batchId = null,
                attemptCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
                synced = false,
                syncedAt = 0
            )
        )
    }

    suspend fun getById(id: String): Counter? = dao.getById(id)

    suspend fun getByBusinessDeviceAndType(businessId: String, deviceCode: String, counterType: String): Counter? = dao.getByBusinessDeviceAndType(businessId, deviceCode, counterType)

    fun getByBusiness(businessId: String): Flow<List<Counter>> = dao.getByBusiness(businessId)

    // Atomically get and increment the counter, returning the new value
    suspend fun getNextValue(businessId: String, deviceCode: String, counterType: String): Long {
        return dao.getNextValue(businessId, deviceCode, counterType, System.currentTimeMillis())
    }

    suspend fun getCurrentValue(businessId: String, deviceCode: String, counterType: String): Long = dao.getCurrentValue(businessId, deviceCode, counterType) ?: 0L
}
