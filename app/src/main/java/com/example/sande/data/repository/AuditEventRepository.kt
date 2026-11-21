package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.AuditEventDao
import com.example.sande.data.entities.AuditEvent
import kotlinx.coroutines.flow.Flow

class AuditEventRepository constructor(
    private val database: AppDatabase
) {
    private val dao: AuditEventDao = database.auditEventDao()

    suspend fun insert(auditEvent: AuditEvent) = dao.insert(auditEvent)

    suspend fun insertAll(auditEvents: List<AuditEvent>) = dao.insertAll(auditEvents)

    fun getByBusiness(businessId: String): Flow<List<AuditEvent>> = dao.getByBusiness(businessId)

    fun getByBusinessAndEntityType(businessId: String, entityType: String): Flow<List<AuditEvent>> = dao.getByBusinessAndEntityType(businessId, entityType)

    fun getByEntity(businessId: String, entityId: String): Flow<List<AuditEvent>> = dao.getByEntity(businessId, entityId)

    fun getByActor(businessId: String, actorId: String): Flow<List<AuditEvent>> = dao.getByActor(businessId, actorId)

    fun getBySeverity(businessId: String, severities: List<String>): Flow<List<AuditEvent>> = dao.getBySeverity(businessId, severities)

    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<AuditEvent>> = dao.getByBusinessAndDateRange(businessId, startDate, endDate)

    fun getUnsynced(): Flow<List<AuditEvent>> = dao.getUnsynced()

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids)

    suspend fun countByBusiness(businessId: String): Int = dao.countByBusiness(businessId)
}
