package com.example.sande.data.repository

import androidx.room.withTransaction
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.MovementDao
import com.example.sande.data.daos.SaleHeaderDao
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.MovementType
import com.example.sande.data.entities.SaleHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Simple data class for sale line items in the repository layer (no UI dependencies).
 */
data class SaleLineItem(
    val productId: String,
    val quantity: Long,
    val unitPriceCents: Long
)

class MovementRepository constructor(
    private val database: AppDatabase,
    private val productRepository: ProductRepository,
    private val saleHeaderRepository: SaleHeaderRepository
) {
    private val movementDao: MovementDao = database.movementDao()
    private val saleHeaderDao: SaleHeaderDao = database.saleHeaderDao()

    val getAll: Flow<List<Movement>> = movementDao.getAll()

    fun getByProduct(productId: String): Flow<List<Movement>> = movementDao.getByProduct(productId)

    suspend fun getStockBalance(productId: String): Long = movementDao.getStockBalance(productId)

    fun getUnsynced(): Flow<List<Movement>> = movementDao.getUnsynced()

    suspend fun recordSale(productId: String, quantity: Long, unitPriceCents: Long?, referenceId: String?, deviceId: String): String {
        val id = UUID.randomUUID().toString()
        val movement = Movement(
            id = id,
            productId = productId,
            type = MovementType.SALE,
            quantity = quantity,
            unitPriceCents = unitPriceCents,
            referenceId = referenceId,
            deviceId = deviceId,
            synced = false
        )
        movementDao.insert(movement)
        return id
    }

    /**
     * Atomically records a complete sale in one Room transaction:
     * 1. Creates a SaleHeader with receipt number
     * 2. Inserts all SALE movements with shared referenceId
     */
    suspend fun recordSaleAtomic(
        items: List<SaleLineItem>,
        receiptNumber: String,
        businessId: String,
        deviceId: String,
        salespersonId: String,
        salespersonRole: String
    ): String = database.withTransaction {
        val totalCents = items.sumOf { it.quantity * it.unitPriceCents }
        val saleHeaderId = UUID.randomUUID().toString()
        val saleHeader = SaleHeader(
            id = saleHeaderId,
            businessId = businessId,
            receiptNumber = receiptNumber,
            totalCents = totalCents,
            deviceId = deviceId,
            synced = false,
            salespersonId = salespersonId,
            salespersonRole = salespersonRole,
            status = "completed"
        )
        saleHeaderDao.insert(saleHeader)

        val referenceId = saleHeaderId
        items.forEach { line ->
            movementDao.insert(
                Movement(
                    id = UUID.randomUUID().toString(),
                    productId = line.productId,
                    type = MovementType.SALE,
                    quantity = line.quantity,
                    unitPriceCents = line.unitPriceCents,
                    referenceId = referenceId,
                    deviceId = deviceId,
                    synced = false,
                    businessId = businessId
                )
            )
        }

        saleHeaderId
    }

    suspend fun recordReceive(productId: String, quantity: Long, unitPriceCents: Long?, referenceId: String?, deviceId: String): String {
        val id = UUID.randomUUID().toString()
        val movement = Movement(
            id = id,
            productId = productId,
            type = MovementType.RECEIVE,
            quantity = quantity,
            unitPriceCents = unitPriceCents,
            referenceId = referenceId,
            deviceId = deviceId,
            synced = false
        )
        movementDao.insert(movement)
        return id
    }

    suspend fun recordAdjustment(productId: String, quantity: Long, referenceId: String?, deviceId: String): String {
        val id = UUID.randomUUID().toString()
        val movement = Movement(
            id = id,
            productId = productId,
            type = MovementType.ADJUST,
            quantity = quantity,
            unitPriceCents = null,
            referenceId = referenceId,
            deviceId = deviceId,
            synced = false
        )
        movementDao.insert(movement)
        return id
    }

    suspend fun recordVoid(originalMovementId: String, productId: String, quantity: Long, referenceId: String?, deviceId: String): String {
        val id = UUID.randomUUID().toString()
        val movement = Movement(
            id = id,
            productId = productId,
            type = MovementType.VOID,
            quantity = quantity,
            unitPriceCents = null,
            referenceId = referenceId,
            deviceId = deviceId,
            synced = false
        )
        movementDao.insert(movement)
        return id
    }

    suspend fun markSynced(ids: List<String>) = movementDao.markSynced(ids)
}
