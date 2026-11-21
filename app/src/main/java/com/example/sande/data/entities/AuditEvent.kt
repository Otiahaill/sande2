package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_events",
    indices = [Index("businessId"), Index("entityType"), Index("entityId"), Index("actorId"), Index("createdAt"), Index("severity")]
)
data class AuditEvent(
    @PrimaryKey val id: String,
    val businessId: String,
    val entityType: String, // product, sale_session, stock_movement, membership, device, settings
    val entityId: String,
    val action: String, // created, updated, deleted, voided, received, adjusted, synced, imported, restored
    val actorId: String,
    val actorRole: String,
    val deviceCode: String,
    val severity: String = "info", // info, warning, error, critical
    val beforeJson: String?,
    val afterJson: String?,
    val metadataJson: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
