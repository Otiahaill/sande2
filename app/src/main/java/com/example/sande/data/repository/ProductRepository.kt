package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.AuditEventDao
import com.example.sande.data.daos.OutboxEntryDao
import com.example.sande.data.daos.ProductDao
import com.example.sande.data.entities.AuditEvent
import com.example.sande.data.entities.OutboxEntry
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.ProductPriceHistory
import com.example.sande.data.sync.OutboxPayloadSerializer
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProductRepository constructor(
    private val database: AppDatabase
) {
    private val dao: ProductDao = database.productDao()
    private val priceHistoryDao = database.productPriceHistoryDao()
    private val auditEventDao: AuditEventDao = database.auditEventDao()
    private val outboxEntryDao: OutboxEntryDao = database.outboxEntryDao()

    val getAllActive: Flow<List<Product>> = dao.getAllActive()

    val getAllActiveByBusiness: (String) -> Flow<List<Product>> = { businessId ->
        dao.getAllActiveByBusiness(businessId)
    }

    /**
     * Inserts a product and queues it for sync.
     *
     * The operation is `create` for a brand-new product and `update` when the
     * product already existed — the form screen uses this one method for both.
     */
    suspend fun insert(product: Product) {
        val isNew = dao.getById(product.id) == null
        dao.insert(product)
        enqueueProductSync(product, if (isNew) "create" else "update")
    }

    suspend fun insertAll(products: List<Product>) = dao.insertAll(products)

    /**
     * Queues a product change for the sync outbox.
     *
     * Without this the backend never learns about product edits made on this
     * device: the outbox is the only channel the sync worker uses.
     */
    private suspend fun enqueueProductSync(
        product: Product,
        operation: String,
        businessId: String = product.businessId
    ) {
        val now = System.currentTimeMillis()
        outboxEntryDao.insert(
            OutboxEntry(
                id = "ob_${UUID.randomUUID()}",
                businessId = businessId,
                entityType = "product",
                entityId = product.id,
                operation = operation,
                payloadJson = OutboxPayloadSerializer.serialize(product),
                batchId = null,
                attemptCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
                synced = false,
                syncedAt = 0
            )
        )
    }

    suspend fun getById(id: String): Product? = dao.getById(id)

    suspend fun getByIdNotDeleted(id: String): Product? = dao.getByIdNotDeleted(id)

    suspend fun update(product: Product) {
        dao.update(product)
        enqueueProductSync(product, "update")
    }

    /**
     * Updates product price and records the change in price history and audit log.
     */
    suspend fun updatePrice(
        productId: String,
        newPriceCents: Long,
        changedBy: String,
        actorRole: String,
        deviceCode: String,
        businessId: String,
        reason: String? = null
    ): Product? {
        val existing = dao.getById(productId) ?: return null
        if (existing.priceCents == newPriceCents) return existing

        val oldPrice = existing.priceCents
        val updated = existing.copy(
            priceCents = newPriceCents,
            originalPriceCents = if (existing.originalPriceCents == 0L) oldPrice else existing.originalPriceCents
        )
        dao.update(updated)

        // Record price history
        priceHistoryDao.insert(ProductPriceHistory(
            id = UUID.randomUUID().toString(),
            productId = productId,
            oldPriceCents = oldPrice,
            newPriceCents = newPriceCents,
            changedBy = changedBy,
            reason = reason,
            createdAt = System.currentTimeMillis()
        ))

        // Record audit event
        auditEventDao.insert(AuditEvent(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            entityType = "product",
            entityId = productId,
            action = "updated",
            actorId = changedBy,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "info",
            beforeJson = "{\"priceCents\":$oldPrice}",
            afterJson = "{\"priceCents\":$newPriceCents}",
            metadataJson = if (reason != null) "{\"reason\":\"$reason\"}" else null,
            createdAt = System.currentTimeMillis(),
            synced = false
        ))

        enqueueProductSync(updated, "update", businessId)

        return updated
    }

    suspend fun archive(id: String, actorId: String, actorRole: String, deviceCode: String, businessId: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getById(id) ?: return
        dao.archive(id, now, now)
        auditEventDao.insert(AuditEvent(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            entityType = "product",
            entityId = id,
            action = "archived",
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "info",
            beforeJson = OutboxPayloadSerializer.serialize(existing),
            afterJson = "archived",
            metadataJson = null,
            createdAt = now,
            synced = false
        ))
        enqueueProductSync(existing.copy(archivedAt = now, updatedAt = now), "update", businessId)
    }

    suspend fun softDelete(id: String, actorId: String, actorRole: String, deviceCode: String, businessId: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getById(id) ?: return
        dao.softDelete(id, now, now)
        auditEventDao.insert(AuditEvent(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            entityType = "product",
            entityId = id,
            action = "deleted",
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "warning",
            beforeJson = OutboxPayloadSerializer.serialize(existing),
            afterJson = "deleted",
            metadataJson = null,
            createdAt = now,
            synced = false
        ))
        // The backend maps a "delete" operation onto a soft delete as well.
        enqueueProductSync(existing.copy(deletedAt = now, updatedAt = now), "delete", businessId)
    }

    suspend fun setActive(id: String, isActive: Boolean, actorId: String, actorRole: String, deviceCode: String, businessId: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getById(id) ?: return
        dao.setActive(id, isActive, now)
        auditEventDao.insert(AuditEvent(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            entityType = "product",
            entityId = id,
            action = if (isActive) "activated" else "deactivated",
            actorId = actorId,
            actorRole = actorRole,
            deviceCode = deviceCode,
            severity = "info",
            beforeJson = OutboxPayloadSerializer.serialize(existing),
            afterJson = "isActive=$isActive",
            metadataJson = null,
            createdAt = now,
            synced = false
        ))
        enqueueProductSync(existing.copy(isActive = isActive, updatedAt = now), "update", businessId)
    }

    fun getByCategory(category: String): Flow<List<Product>> = dao.getByCategory(category)

    suspend fun searchProducts(businessId: String, query: String): Flow<List<Product>> = dao.searchProducts(businessId, "%$query%")

    fun getArchived(): Flow<List<Product>> = dao.getArchived()

    fun getDeleted(): Flow<List<Product>> = dao.getDeleted()

    suspend fun seedSampleData() {
        val existing = dao.getById("prod-sample-1")
        if (existing != null) return
        insertAll(listOf(
            Product(
                id = "prod-sample-1",
                businessId = "default-business",
                name = "Maize Flour 2kg",
                sku = "MF-001",
                categoryId = "cat-grains",
                category = "Grains",
                priceCents = 22000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 22000,
                isActive = true
            ),
            Product(
                id = "prod-sample-2",
                businessId = "default-business",
                name = "Rice 5kg",
                sku = "RC-002",
                categoryId = "cat-grains",
                category = "Grains",
                priceCents = 65000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 65000,
                isActive = true
            ),
            Product(
                id = "prod-sample-3",
                businessId = "default-business",
                name = "Cooking Oil 2L",
                sku = "OIL-003",
                categoryId = "cat-oils",
                category = "Oils",
                priceCents = 85000,
                unitOfMeasure = "liter",
                conversionRatio = 1,
                originalPriceCents = 85000,
                isActive = true
            ),
            Product(
                id = "prod-sample-4",
                businessId = "default-business",
                name = "Sugar 2kg",
                sku = "SUG-004",
                categoryId = "cat-staples",
                category = "Staples",
                priceCents = 32000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 32000,
                isActive = true
            ),
            Product(
                id = "prod-sample-5",
                businessId = "default-business",
                name = "Salt 1kg",
                sku = "SAL-005",
                categoryId = "cat-staples",
                category = "Staples",
                priceCents = 15000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 15000,
                isActive = true
            )
        ))
    }
}
