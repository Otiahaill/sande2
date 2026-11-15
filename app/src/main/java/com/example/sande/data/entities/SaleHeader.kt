package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_headers",
    indices = [Index("receiptNumber"), Index("createdAt"), Index("synced"), Index("businessId")]
)
data class SaleHeader(
    @PrimaryKey val id: String,
    val businessId: String,
    val receiptNumber: String,
    val totalCents: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val synced: Boolean = false
)
