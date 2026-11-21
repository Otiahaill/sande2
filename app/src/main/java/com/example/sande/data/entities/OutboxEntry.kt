package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_entries",
    indices = [Index("businessId"), Index("entityType"), Index("entityId"), Index("operation"), Index("createdAt"), Index("synced"), Index("batchId")]
)
data class OutboxEntry(
    @PrimaryKey val id: String,
    val businessId: String,
    val entityType: String, // product, sale_session, stock_movement, membership_mirror, device_registration, business_profile, counter
    val entityId: String,
    val operation: String, // create, update, delete
    val payloadJson: String,
    val batchId: String?,
    val attemptCount: Int = 0,
    val lastError: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val syncedAt: Long = 0
)
