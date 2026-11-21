package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "counters",
    indices = [Index(value = ["businessId", "deviceCode", "counterType"], unique = true)]
)
data class Counter(
    @PrimaryKey val id: String, // businessId:deviceCode:counterType
    val businessId: String,
    val deviceCode: String,
    val counterType: String, // receipt, sale_session, stock_movement
    val currentValue: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
