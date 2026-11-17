package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_sales")
data class DraftSale(
    @PrimaryKey val id: String,
    val businessId: String,
    val salespersonId: String,
    val salespersonRole: String,
    val cartItems: String, // JSON string of cart items
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
