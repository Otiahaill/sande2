package com.example.sande.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("productId"), Index("businessId"), Index("actorId"), Index("createdAt"), Index("synced"), Index("referenceId")]
)
data class StockMovement(
    @PrimaryKey val id: String,
    val productId: String,
    val businessId: String,
    val movementType: String, // RECEIVE, SALE, ADJUST, VOID, RETURN
    val quantityDelta: Long, // Signed: + for receive/adjust/void, - for sale/return
    val unitPriceCents: Long?, // For SALE/RECEIVE with cost
    val actorId: String,
    val actorRole: String,
    val deviceCode: String,
    val reason: String?, // e.g. "Stock received", "Sale #SAL-ABC-00001", "Damaged goods"
    val referenceId: String?, // Links to sale_sessions.id, receipts.id, etc.
    val reversalOf: String?, // ID of movement this reverses (for VOID)
    val metadataJson: String?, // Extensible: batch #, supplier, etc.
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
