package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_adjustments",
    indices = [
        androidx.room.Index("productId"),
        androidx.room.Index("businessId"),
        androidx.room.Index("createdAt")
    ]
)
data class StockAdjustment(
    @PrimaryKey val id: String,
    val productId: String,
    val businessId: String,
    val adjustmentType: String, // "RECEIVE", "RETURN", "COUNT", "DAMAGE", "TRANSFER"
    val quantityChange: Long, // positive for increase, negative for decrease
    val reason: String? = null,
    val referenceId: String? = null, // e.g., purchase order ID, transfer ID
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String // user ID who made the adjustment
)
