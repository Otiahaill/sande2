package com.example.sande.data.repository

import androidx.room.withTransaction
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.AuditEventDao
import com.example.sande.data.daos.CounterDao
import com.example.sande.data.daos.OutboxEntryDao
import com.example.sande.data.daos.ProductDao
import com.example.sande.data.daos.ReceiptDao
import com.example.sande.data.daos.SaleItemDao
import com.example.sande.data.daos.SaleSessionDao
import com.example.sande.data.daos.StockMovementDao
import com.example.sande.data.entities.AuditEvent
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.OutboxEntry
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.entities.StockMovement
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Simple data class for sale line items in the repository layer (no UI dependencies).
 */
data class SaleLineItem(
    val productId: String,
    val quantity: Long,
    val unitPriceCents: Long
)

/**
 * Result of a completed checkout.
 */
data class CheckoutResult(
    val sessionId: String,
    val receiptNumber: String,
    val receiptId: String,
    val totalCents: Long
)

/**
 * Handles the complete atomic checkout transaction:
 * - Creates SaleSession
 * - Creates SaleItems (snapshots)
 * - Creates StockMovements (signed deltas)
 * - Creates Receipt
 * - Increments Counter
 * - Creates AuditEvent
 * - Creates OutboxEntry for sync
 */
class CheckoutRepository constructor(
    private val database: AppDatabase,
    private val productRepository: ProductRepository
) {
    private val saleSessionDao: SaleSessionDao = database.saleSessionDao()
    private val saleItemDao: SaleItemDao = database.saleItemDao()
    private val stockMovementDao: StockMovementDao = database.stockMovementDao()
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val counterDao: CounterDao = database.counterDao()
    private val auditEventDao: AuditEventDao = database.auditEventDao()
    private val outboxEntryDao: OutboxEntryDao = database.outboxEntryDao()
    private val productDao: ProductDao = database.productDao()

    /**
     * Atomically records a complete sale in one Room transaction:
     * 1. Creates a SaleSession with receipt number
     * 2. Creates SaleItems (product snapshots)
     * 3. Creates StockMovements (signed deltas for SALE)
     * 4. Creates Receipt
     * 5. Increments receipt counter
     * 6. Creates AuditEvent
     * 7. Creates OutboxEntry for sync
     */
    suspend fun recordSaleAtomic(
        items: List<SaleLineItem>,
        receiptNumber: String,
        businessId: String,
        deviceId: String,
        deviceCode: String,
        salespersonId: String,
        salespersonRole: String,
        actorId: String,
        actorRole: String
    ): CheckoutResult = database.withTransaction {
        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        val subtotalCents = items.sumOf { it.quantity * it.unitPriceCents }
        val taxCents = 0L // TODO: tax calculation per business settings
        val totalCents = subtotalCents + taxCents

        // 1. Create SaleSession
        val saleSession = SaleSession(
            id = sessionId,
            businessId = businessId,
            receiptNumber = receiptNumber,
            deviceId = deviceId,
            deviceCode = deviceCode,
            salespersonId = salespersonId,
            salespersonRole = salespersonRole,
            subtotalCents = subtotalCents,
            taxCents = taxCents,
            totalCents = totalCents,
            status = "completed",
            createdAt = now,
            updatedAt = now,
            synced = false
        )
        saleSessionDao.insert(saleSession)

        // 2. Create SaleItems (product snapshots) and 3. StockMovements
        items.forEach { line ->
            val product = productDao.getById(line.productId)
                ?: throw IllegalArgumentException("Product not found: ${line.productId}")

            val categoryName = product.categoryId?.let { catId ->
                // We'd need category DAO for this; for now use empty string
                ""
            } ?: ""

            val saleItem = SaleItem(
                id = "si_${UUID.randomUUID()}",
                sessionId = sessionId,
                productId = line.productId,
                productName = product.name,
                categoryName = categoryName,
                quantity = line.quantity,
                unitPriceCents = line.unitPriceCents,
                totalCents = line.quantity * line.unitPriceCents,
                businessId = businessId,
                createdAt = now
            )
            saleItemDao.insert(saleItem)

            val stockMovement = StockMovement(
                id = "sm_${UUID.randomUUID()}",
                productId = line.productId,
                businessId = businessId,
                movementType = "SALE",
                quantityDelta = -line.quantity, // Negative for sale
                unitPriceCents = line.unitPriceCents,
                actorId = actorId,
                actorRole = actorRole,
                deviceCode = deviceCode,
                reason = "Sale #$receiptNumber",
                referenceId = sessionId,
                reversalOf = null,
                metadataJson = null,
                createdAt = now,
                synced = false
            )
            stockMovementDao.insert(stockMovement)
        }

        // 4. Create Receipt
        val receiptCounter = counterDao.getNextValue(businessId, deviceCode, "receipt", now)
        val receiptId = "rcpt_${UUID.randomUUID()}"
        val receiptContent = buildReceiptContent(saleSession, items)
        val contentHash = sha256Hex(receiptContent)

        val receipt = Receipt(
            id = receiptId,
            sessionId = sessionId,
            businessId = businessId,
            deviceCode = deviceCode,
            counter = receiptCounter,
            receiptNumber = receiptNumber,
            format = "thermal_58mm",
            contentHash = contentHash,
            createdAt = now
        )
        receiptDao.insert(receipt)

        // 5. Create AuditEvent
        val auditEvent = AuditEvent(
            id = "audit_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "sale_session",
            entityId = sessionId,
            action = "created",
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "info",
            beforeJson = null,
            afterJson = saleSession.toString(),
            metadataJson = "{\"receiptNumber\":\"$receiptNumber\",\"itemCount\":${items.size}}",
            createdAt = now,
            synced = false
        )
        auditEventDao.insert(auditEvent)

        // 6. Create OutboxEntry for sync
        val outboxEntry = OutboxEntry(
            id = "ob_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "sale_session",
            entityId = sessionId,
            operation = "create",
            payloadJson = saleSession.toString(), // TODO: proper JSON serialization
            batchId = null,
            attemptCount = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
            synced = false,
            syncedAt = 0
        )
        outboxEntryDao.insert(outboxEntry)

        CheckoutResult(
            sessionId = sessionId,
            receiptNumber = receiptNumber,
            receiptId = receiptId,
            totalCents = totalCents
        )
    }

    private fun buildReceiptContent(session: SaleSession, items: List<SaleLineItem>): String {
        return """
            SANDE Receipt
            $session.receiptNumber
            Session: $session.id
            Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt))}
            Salesperson: ${session.salespersonId} (${session.salespersonRole})
            Device: ${session.deviceCode}
            ---
            ${items.map { "${it.productId} x${it.quantity} @ ${it.unitPriceCents/100} = ${it.quantity * it.unitPriceCents/100}" }.joinToString("\n")}
            ---
            Subtotal: ${session.subtotalCents/100}
            Tax: ${session.taxCents/100}
            Total: ${session.totalCents/100}
            Status: ${session.status}
        """.trimIndent()
    }

    /**
     * SHA-256 of [text], as a lowercase hex string.
     *
     * Guava's `Hashing` is not available on Android, so the JDK digest is used.
     */
    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Atomically records a stock receipt in one transaction:
     * 1. Creates StockMovement (RECEIVE)
     * 2. Creates AuditEvent
     * 3. Creates OutboxEntry for sync
     */
    suspend fun recordReceiveAtomic(
        productId: String,
        quantity: Long,
        businessId: String,
        deviceCode: String,
        actorId: String,
        actorRole: String,
        unitPriceCents: Long? = null,
        reason: String = "Stock received"
    ): String = database.withTransaction {
        val now = System.currentTimeMillis()
        val movementId = "sm_${UUID.randomUUID()}"

        val stockMovement = StockMovement(
            id = movementId,
            productId = productId,
            businessId = businessId,
            movementType = "RECEIVE",
            quantityDelta = quantity,
            unitPriceCents = unitPriceCents,
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            reason = reason,
            referenceId = null,
            reversalOf = null,
            metadataJson = null,
            createdAt = now,
            synced = false
        )
        stockMovementDao.insert(stockMovement)

        val auditEvent = AuditEvent(
            id = "audit_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "stock_movement",
            entityId = movementId,
            action = "created",
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "info",
            beforeJson = null,
            afterJson = stockMovement.toString(),
            metadataJson = "{\"movementType\":\"RECEIVE\",\"quantity\":$quantity}",
            createdAt = now,
            synced = false
        )
        auditEventDao.insert(auditEvent)

        val outboxEntry = OutboxEntry(
            id = "ob_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "stock_movement",
            entityId = movementId,
            operation = "create",
            payloadJson = stockMovement.toString(),
            batchId = null,
            attemptCount = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
            synced = false,
            syncedAt = 0
        )
        outboxEntryDao.insert(outboxEntry)

        movementId
    }

    /**
     * Atomically records a stock adjustment in one transaction.
     */
    suspend fun recordAdjustmentAtomic(
        productId: String,
        quantityDelta: Long, // Signed: + for increase, - for decrease
        businessId: String,
        deviceCode: String,
        actorId: String,
        actorRole: String,
        reason: String,
        referenceId: String? = null
    ): String = database.withTransaction {
        val now = System.currentTimeMillis()
        val movementId = "sm_${UUID.randomUUID()}"
        val movementType = if (quantityDelta >= 0) "ADJUST" else "ADJUST" // Could use separate types

        val stockMovement = StockMovement(
            id = movementId,
            productId = productId,
            businessId = businessId,
            movementType = movementType,
            quantityDelta = quantityDelta,
            unitPriceCents = null,
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            reason = reason,
            referenceId = referenceId,
            reversalOf = null,
            metadataJson = null,
            createdAt = now,
            synced = false
        )
        stockMovementDao.insert(stockMovement)

        val auditEvent = AuditEvent(
            id = "audit_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "stock_movement",
            entityId = movementId,
            action = "created",
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "info",
            beforeJson = null,
            afterJson = stockMovement.toString(),
            metadataJson = "{\"movementType\":\"$movementType\",\"quantityDelta\":$quantityDelta}",
            createdAt = now,
            synced = false
        )
        auditEventDao.insert(auditEvent)

        val outboxEntry = OutboxEntry(
            id = "ob_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "stock_movement",
            entityId = movementId,
            operation = "create",
            payloadJson = stockMovement.toString(),
            batchId = null,
            attemptCount = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
            synced = false,
            syncedAt = 0
        )
        outboxEntryDao.insert(outboxEntry)

        movementId
    }

    /**
     * Atomically voids a sale session.
     */
    suspend fun voidSaleAtomic(
        sessionId: String,
        businessId: String,
        deviceCode: String,
        actorId: String,
        actorRole: String,
        reason: String = "Voided by user"
    ): Boolean = database.withTransaction {
        val session = saleSessionDao.getById(sessionId)
        if (session == null || session.status == "voided") return@withTransaction false

        val now = System.currentTimeMillis()

        // Update session status
        saleSessionDao.updateStatus(sessionId, "voided", now)

        // Create reversal StockMovements for each SaleItem
        val saleItems = saleItemDao.getBySession(sessionId).first()
        saleItems.forEach { item ->
            val stockMovement = StockMovement(
                id = "sm_${UUID.randomUUID()}",
                productId = item.productId,
                businessId = businessId,
                movementType = "VOID",
                quantityDelta = item.quantity, // Positive to reverse the sale
                unitPriceCents = item.unitPriceCents,
                actorId = actorId,
                actorRole = actorRole,
                deviceCode = deviceCode,
                reason = "Void of sale #${session.receiptNumber}: $reason",
                referenceId = sessionId,
                reversalOf = null, // Could link to original movement IDs
                metadataJson = null,
                createdAt = now,
                synced = false
            )
            stockMovementDao.insert(stockMovement)
        }

        // Create AuditEvent
        val auditEvent = AuditEvent(
            id = "audit_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "sale_session",
            entityId = sessionId,
            action = "voided",
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "warning",
            beforeJson = session.toString(),
            afterJson = session.copy(status = "voided").toString(),
            metadataJson = "{\"reason\":\"$reason\"}",
            createdAt = now,
            synced = false
        )
        auditEventDao.insert(auditEvent)

        // Create OutboxEntry for the void
        val outboxEntry = OutboxEntry(
            id = "ob_${UUID.randomUUID()}",
            businessId = businessId,
            entityType = "sale_session",
            entityId = sessionId,
            operation = "update",
            payloadJson = "voided",
            batchId = null,
            attemptCount = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
            synced = false,
            syncedAt = 0
        )
        outboxEntryDao.insert(outboxEntry)

        true
    }
}
