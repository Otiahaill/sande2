package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class Member(
    @PrimaryKey val uid: String,
    val businessId: String,
    val role: String,
    val status: String,
    val email: String?,
    val createdAt: Long = System.currentTimeMillis()
)
