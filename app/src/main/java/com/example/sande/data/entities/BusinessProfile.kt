package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_profiles")
data class BusinessProfile(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // hardware, retail, wholesale, services, other
    val phone: String?,
    val countryCode: String, // ISO 3166-1 alpha-2, e.g. "KE"
    val currencyCode: String, // ISO 4217, e.g. "KES"
    val timezone: String, // IANA timezone, e.g. "Africa/Nairobi"
    val address: String?,
    val logoUri: String?,
    val negativeStockPolicy: String = "warn", // allow, warn, block
    val receiptFooter: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0,
    val schemaVersion: Int = 1
)
