package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_sessions",
    indices = [Index("businessId"), Index("salespersonId"), Index("deviceId"), Index("createdAt"), Index("status")]
)
data class SaleSession(
    @PrimaryKey val id: String,
    val businessId: String,
    val receiptNumber: String,
    val deviceId: String,
    val deviceCode: String,
    val salespersonId: String,
    val salespersonRole: String,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val status: String = "completed", // completed, voided, pending
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
