package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "membership_mirrors",
    indices = [Index("businessId"), Index("accountId"), Index("deviceCode"), Index("lastSyncedAt")]
)
data class MembershipMirror(
    @PrimaryKey val id: String, // businessId:accountId
    val businessId: String,
    val accountId: String,
    val email: String,
    val role: String, // owner, staff
    val status: String, // active, invited, suspended, revoked
    val invitedBy: String?,
    val invitedAt: Long?,
    val acceptedAt: Long?,
    val deviceCode: String?, // Primary device code if registered
    val lastSyncedAt: Long,
    val serverVersion: Long, // For conflict detection
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
