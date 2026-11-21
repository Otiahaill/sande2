package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_exceptions",
    indices = [Index("businessId"), Index("deviceCode"), Index("status"), Index("createdAt")]
)
data class SyncException(
    @PrimaryKey val id: String,
    val businessId: String,
    val deviceCode: String,
    val exceptionType: String, // STOCK_NEGATIVE, PRICE_MISMATCH, CLOCK_SKEW, REVOKED_DEVICE, VALIDATION, CONFLICT
    val entityType: String,
    val entityId: String,
    val localVersion: Long,
    val serverVersion: Long,
    val description: String,
    val localDataJson: String,
    val serverDataJson: String?,
    val resolution: String?, // local_wins, server_wins, manual, pending
    val resolvedBy: String?,
    val resolvedAt: Long = 0,
    val status: String = "open", // open, resolved, ignored
    val createdAt: Long = System.currentTimeMillis()
)
