package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_registrations",
    indices = [Index("businessId"), Index("accountId"), Index("deviceCode"), Index("status")]
)
data class DeviceRegistration(
    @PrimaryKey val id: String, // deviceCode
    val businessId: String,
    val accountId: String,
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String, // android, ios, web
    val status: String, // active, pending_approval, revoked, replaced
    val approvedBy: String?,
    val approvedAt: Long?,
    val revokedAt: Long?,
    val revokedBy: String?,
    val lastSeenAt: Long,
    val lastSyncAt: Long?,
    val pushToken: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
