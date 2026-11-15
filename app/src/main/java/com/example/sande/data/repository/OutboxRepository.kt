package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.OutboxDao
import com.example.sande.data.entities.Outbox
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class OutboxRepository constructor(
    private val database: AppDatabase
) {
    private val dao: OutboxDao = database.outboxDao()

    suspend fun insert(entityType: String, entityId: String, operation: String, payload: String): String {
        val id = UUID.randomUUID().toString()
        val outbox = Outbox(
            id = id,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payload = payload,
            synced = false
        )
        dao.insert(outbox)
        return id
    }

    fun getUnsynced(): Flow<List<Outbox>> = dao.getUnsynced()

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids)

    suspend fun deleteSynced() = dao.deleteSynced()
}
