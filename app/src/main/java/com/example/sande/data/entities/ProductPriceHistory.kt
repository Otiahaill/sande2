package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_price_history",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId"), Index("createdAt")]
)
data class ProductPriceHistory(
    @PrimaryKey val id: String,
    val productId: String,
    val oldPriceCents: Long,
    val newPriceCents: Long,
    val changedBy: String,
    val reason: String?,
    val createdAt: Long = System.currentTimeMillis()
)
