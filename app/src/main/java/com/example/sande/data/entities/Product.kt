package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index("businessId"),
        Index("categoryId"),
        Index("isActive"),
        Index("archivedAt"),
        Index("deletedAt")
    ]
)
data class Product(
    @PrimaryKey val id: String,
    val businessId: String,
    val name: String,
    val sku: String?,
    val categoryId: String?,
    val category: String?,
    val priceCents: Long,
    val unitOfMeasure: String?,   // e.g. "piece", "kg", "liter"
    val conversionRatio: Long?,   // e.g. 1000 means 1 unitOfMeasure = 1000 base units
    val isActive: Boolean = true,
    val archivedAt: Long = 0,     // 0 = not archived, timestamp when archived
    val deletedAt: Long = 0,      // 0 = not deleted, timestamp when deleted (soft delete)
    val originalPriceCents: Long = 0, // Price at creation for historical reference
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
