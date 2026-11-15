package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movements",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId"), Index("createdAt"), Index("synced"), Index("businessId")]
)
data class Movement(
    @PrimaryKey val id: String,
    val productId: String,
    val type: MovementType,
    val quantity: Long,
    val unitPriceCents: Long?,
    val referenceId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val synced: Boolean = false,
    val businessId: String = ""
)
