package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_headers",
    indices = [
        Index("receiptNumber"),
        Index("createdAt"),
        Index("synced"),
        Index("businessId"),
        Index("salespersonId"),
        Index("status")
    ]
)
data class SaleHeader(
    @PrimaryKey val id: String,
    val businessId: String,
    val receiptNumber: String,
    val totalCents: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val synced: Boolean = false,
    val salespersonId: String = "",   // User ID who completed the sale
    val salespersonRole: String = "", // "OWNER" or "SALESPERSON"
    val status: String = "completed"  // "completed", "voided", "draft"
)
