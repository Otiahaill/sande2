package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipts",
    indices = [Index("sessionId"), Index("businessId"), Index("deviceCode"), Index("counter"), Index("createdAt")]
)
data class Receipt(
    @PrimaryKey val id: String,
    val sessionId: String,
    val businessId: String,
    val deviceCode: String,
    val counter: Long,
    val receiptNumber: String,
    val format: String = "thermal_58mm", // thermal_58mm, thermal_80mm, pdf_a4, pdf_a5
    val contentHash: String,
    val createdAt: Long = System.currentTimeMillis()
)
