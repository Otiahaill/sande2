package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_categories",
    indices = [Index("businessId"), Index("isActive")]
)
data class ProductCategory(
    @PrimaryKey val id: String,
    val businessId: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
