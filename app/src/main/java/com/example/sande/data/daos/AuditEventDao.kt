package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.sande.data.entities.AuditEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(auditEvent: AuditEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(auditEvents: List<AuditEvent>)

    @Query("SELECT * FROM audit_events WHERE id = :id")
    suspend fun getById(id: String): AuditEvent?

    @Query("SELECT * FROM audit_events WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<AuditEvent>>

    @Query("SELECT * FROM audit_events WHERE businessId = :businessId AND entityType = :entityType ORDER BY createdAt DESC")
    fun getByBusinessAndEntityType(businessId: String, entityType: String): Flow<List<AuditEvent>>

    @Query("SELECT * FROM audit_events WHERE businessId = :businessId AND entityId = :entityId ORDER BY createdAt DESC")
    fun getByEntity(businessId: String, entityId: String): Flow<List<AuditEvent>>

    @Query("SELECT * FROM audit_events WHERE businessId = :businessId AND actorId = :actorId ORDER BY createdAt DESC")
    fun getByActor(businessId: String, actorId: String): Flow<List<AuditEvent>>

    @Query("SELECT * FROM audit_events WHERE businessId = :businessId AND severity IN (:severities) ORDER BY createdAt DESC")
    fun getBySeverity(businessId: String, severities: List<String>): Flow<List<AuditEvent>>

    @Query("SELECT * FROM audit_events WHERE businessId = :businessId AND createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<AuditEvent>>

    @Query("SELECT * FROM audit_events WHERE synced = 0 ORDER BY createdAt ASC")
    fun getUnsynced(): Flow<List<AuditEvent>>

    @Query("UPDATE audit_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM audit_events WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int
}
