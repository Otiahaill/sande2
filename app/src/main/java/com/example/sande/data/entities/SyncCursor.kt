package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_cursors")
data class SyncCursor(
    @PrimaryKey val id: String, // businessId:deviceCode
    val businessId: String,
    val deviceCode: String,
    val cursor: Long, // Server-side sequence number
    val lastPullAt: Long,
    val lastPushAt: Long,
    val pendingPushCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
