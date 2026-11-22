package com.example.sande.data.repository

import androidx.room.withTransaction
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.MovementDao
import com.example.sande.data.daos.SaleHeaderDao
import com.example.sande.data.daos.StockMovementDao
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.MovementType
import com.example.sande.data.entities.SaleHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Legacy MovementRepository - kept for backwards compatibility with existing code.
 * New code should use StockMovementRepository, CheckoutRepository, SaleSessionRepository, etc.
 * This class now reads from stock_movements for balance calculations.
 */
class MovementRepository constructor(
    private val database: AppDatabase,
    private val productRepository: ProductRepository,
    private val saleHeaderRepository: SaleHeaderRepository
) {
    private val movementDao: MovementDao = database.movementDao()
    private val saleHeaderDao: SaleHeaderDao = database.saleHeaderDao()
    private val stockMovementDao: StockMovementDao = database.stockMovementDao()

    // Legacy read flows (for migration period)
    val getAll: Flow<List<Movement>> = movementDao.getAll()

    fun getByProduct(productId: String): Flow<List<Movement>> = movementDao.getByProduct(productId)

    // Use new stock_movements table for balance
    suspend fun getStockBalance(productId: String): Long = stockMovementDao.getStockBalance(productId)

    // Observe real-time balances for all products in a business
    fun observeBalances(businessId: String): Flow<Map<String, Long>> = stockMovementDao.observeBalances(businessId)

    fun getUnsynced(): Flow<List<Movement>> = movementDao.getUnsynced()

    // Legacy write methods - kept for any remaining legacy code paths
    // NEW CODE SHOULD USE CheckoutRepository.recordSaleAtomic() INSTEAD
    @Deprecated("Use CheckoutRepository.recordSaleAtomic()", ReplaceWith("checkoutRepository.recordSaleAtomic(...)"))
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
     * @deprecated Use CheckoutRepository.recordSaleAtomic() instead.
     * This legacy method writes to both movements (legacy) and sale_headers.
     */
    @Deprecated("Use CheckoutRepository.recordSaleAtomic()", ReplaceWith("checkoutRepository.recordSaleAtomic(...)"))
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

    @Deprecated("Use CheckoutRepository.recordReceiveAtomic()")
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

    @Deprecated("Use CheckoutRepository.recordAdjustmentAtomic()")
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

    @Deprecated("Use CheckoutRepository.voidSaleAtomic()")
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
