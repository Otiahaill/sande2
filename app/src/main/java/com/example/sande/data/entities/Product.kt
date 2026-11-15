package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index("businessId"), Index("categoryId"), Index("isActive")]
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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
