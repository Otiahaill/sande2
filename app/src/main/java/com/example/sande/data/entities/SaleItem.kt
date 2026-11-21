package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SaleSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId"), Index("sessionId"), Index("businessId")]
)
data class SaleItem(
    @PrimaryKey val id: String,
    val sessionId: String,
    val productId: String,
    val productName: String,
    val categoryName: String,
    val quantity: Long,
    val unitPriceCents: Long,
    val totalCents: Long,
    val businessId: String,
    val createdAt: Long = System.currentTimeMillis()
)
