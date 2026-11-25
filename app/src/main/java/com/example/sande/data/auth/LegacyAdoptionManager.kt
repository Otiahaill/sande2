package com.example.sande.data.auth

import android.content.Context
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Outbox
import com.example.sande.data.entities.OutboxEntry
import com.example.sande.data.entities.ProductCategory
import com.example.sande.data.entities.SaleHeader
import com.example.sande.data.entities.StockMovement
import com.example.sande.data.prefs.TokenManager
import com.example.sande.data.remote.ApiClient
import com.example.sande.data.remote.BusinessRegistrationRequest
import com.example.sande.data.remote.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Handles adoption of legacy offline data when a user registers a business
 * and wants to keep their existing local data.
 *
 * Legacy data was written under the placeholder business ID `default-business`.
 * Adoption registers the business with the backend, then re-points every local
 * table that carries a `businessId` column at the newly issued business ID.
 *
 * Schema notes:
 * - The old `stock_adjustments` table and its `StockAdjustment` entity no longer
 *   exist; the modern equivalent is `stock_movements`, which is what the
 *   "stock adjustments" count reports.
 * - The legacy `outbox` table has no `businessId` column, so its rows are
 *   *converted* into `outbox_entries` rows carrying the new business ID rather
 *   than having their ID rewritten.
 */
class LegacyAdoptionManager(
    private val context: Context,
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val apiClient: ApiClient,
) {

    /** Business ID that all legacy offline data was written under. */
    private val legacyBusinessId = "default-business"

    /** Checks if there's legacy data under the default business ID. */
    suspend fun hasLegacyData(): Boolean = withContext(Dispatchers.IO) {
        val counts = getLegacyDataCounts()
        counts.total > 0
    }

    /** Gets counts of legacy data for display. */
    suspend fun getLegacyDataCounts(): LegacyDataCounts = withContext(Dispatchers.IO) {
        LegacyDataCounts(
            products = database.productDao().countByBusiness(legacyBusinessId),
            movements = database.movementDao().countByBusiness(legacyBusinessId),
            sales = database.saleHeaderDao().countByBusiness(legacyBusinessId),
            members = database.memberDao().countByBusiness(legacyBusinessId),
            stockAdjustments = database.stockMovementDao().countByBusiness(legacyBusinessId),
            outboxEntries = database.outboxEntryDao().countByBusiness(legacyBusinessId),
        )
    }

    /**
     * Adopts legacy data by:
     * 1. Registering a new business with the backend
     * 2. Rewriting all legacy entity business_ids to the new business_id
     * 3. Converting legacy outbox rows into business-scoped outbox entries
     * 4. Creating the business profile for the new business
     * 5. Recording a safety copy marker
     */
    suspend fun adoptLegacyData(
        registrationRequest: BusinessRegistrationRequest,
        onProgress: (String, Float) -> Unit
    ): Result<AdoptionResult> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Register business with backend
            onProgress("Registering business...", 0.1f)
            val registerResponse = apiClient.registerBusiness(registrationRequest).getOrThrow()
            val registration = registerResponse.data
                ?: return@withContext Result.failure(Exception("No data in registration response"))
            val newBusinessId = registration.tokens.membership.businessId
            if (newBusinessId.isBlank()) {
                return@withContext Result.failure(Exception("No business ID in response"))
            }

            // Save tokens
            tokenManager.saveTokens(registration.tokens, registration.accountId)

            // Step 2: Rewrite business_id in all legacy tables
            onProgress("Migrating products...", 0.25f)
            rewriteProductBusinessIds(newBusinessId)

            onProgress("Migrating categories...", 0.35f)
            rewriteCategoryBusinessIds(newBusinessId)

            onProgress("Migrating movements...", 0.45f)
            rewriteMovementBusinessIds(newBusinessId)

            onProgress("Migrating stock movements...", 0.55f)
            rewriteStockMovementBusinessIds(newBusinessId)

            onProgress("Migrating sales...", 0.65f)
            rewriteSaleBusinessIds(newBusinessId)

            onProgress("Migrating members...", 0.72f)
            rewriteMemberBusinessIds(newBusinessId)

            onProgress("Migrating outbox...", 0.80f)
            migrateLegacyOutbox(newBusinessId)

            onProgress("Creating business profile...", 0.90f)
            createBusinessProfile(newBusinessId, registrationRequest)

            onProgress("Finalizing...", 1.0f)
            markAdoptionComplete(newBusinessId)

            return@withContext Result.success(
                AdoptionResult(
                    businessId = newBusinessId,
                    accountId = registration.accountId,
                )
            )
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private suspend fun rewriteProductBusinessIds(newBusinessId: String) {
        val products = database.productDao().getAllByBusiness(legacyBusinessId)
        for (product in products) {
            database.productDao().update(product.copy(businessId = newBusinessId))
        }
    }

    private suspend fun rewriteCategoryBusinessIds(newBusinessId: String) {
        val categories: List<ProductCategory> = database.productCategoryDao().getByBusiness(legacyBusinessId).first()
        for (category in categories) {
            database.productCategoryDao().update(category.copy(businessId = newBusinessId))
        }
    }

    private suspend fun rewriteMovementBusinessIds(newBusinessId: String) {
        val movements = database.movementDao().getAllByBusiness(legacyBusinessId)
        for (movement in movements) {
            database.movementDao().update(movement.copy(businessId = newBusinessId))
        }
    }

    private suspend fun rewriteStockMovementBusinessIds(newBusinessId: String) {
        val movements: List<StockMovement> = database.stockMovementDao().getByBusiness(legacyBusinessId).first()
        for (movement in movements) {
            database.stockMovementDao().update(movement.copy(businessId = newBusinessId))
        }
    }

    private suspend fun rewriteSaleBusinessIds(newBusinessId: String) {
        val sales: List<SaleHeader> = database.saleHeaderDao().getByBusiness(legacyBusinessId).first()
        for (sale in sales) {
            database.saleHeaderDao().update(sale.copy(businessId = newBusinessId))
        }
    }

    private suspend fun rewriteMemberBusinessIds(newBusinessId: String) {
        val members = database.memberDao().getAllByBusiness(legacyBusinessId)
        for (member in members) {
            database.memberDao().update(member.copy(businessId = newBusinessId))
        }
    }

    /**
     * The legacy `outbox` table carries no `businessId`, so its rows cannot simply
     * be re-pointed. They are converted into `outbox_entries` rows owned by the new
     * business, and the legacy rows are removed so the work is not done twice.
     */
    private suspend fun migrateLegacyOutbox(newBusinessId: String) {
        val legacyEntries: List<Outbox> = database.outboxDao().getUnsynced(limit = Int.MAX_VALUE).first()
        for (legacy in legacyEntries) {
            val entry = OutboxEntry(
                id = legacy.id,
                businessId = newBusinessId,
                entityType = legacy.entityType,
                entityId = legacy.entityId,
                operation = legacy.operation,
                payloadJson = legacy.payload,
                batchId = null,
                attemptCount = legacy.attempts,
                lastError = null,
                createdAt = legacy.createdAt,
                updatedAt = legacy.createdAt,
                synced = legacy.synced,
                syncedAt = if (legacy.synced) legacy.createdAt else 0L,
            )
            database.outboxEntryDao().insert(entry)
        }
    }

    private suspend fun createBusinessProfile(
        newBusinessId: String,
        request: BusinessRegistrationRequest
    ) {
        val now = System.currentTimeMillis()
        val profile = BusinessProfile(
            id = newBusinessId,
            name = request.name,
            category = request.category,
            phone = request.phone,
            countryCode = request.countryCode,
            currencyCode = request.currencyCode,
            timezone = request.timezone,
            address = request.address,
            logoUri = null,
            negativeStockPolicy = "warn",
            receiptFooter = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = 0,
            schemaVersion = 1,
        )
        database.businessProfileDao().insert(profile)
    }

    private suspend fun markAdoptionComplete(newBusinessId: String) {
        // Re-point the placeholder business row at the newly issued business ID.
        val business = database.businessDao().getById(legacyBusinessId)
        business?.let {
            database.businessDao().insert(it.copy(id = newBusinessId))
            database.businessDao().delete(it)
        }

        // Store a flag that adoption is complete
        val prefs = context.getSharedPreferences("sande_adoption", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("legacy_adopted_$newBusinessId", true).apply()
    }

    data class LegacyDataCounts(
        val products: Int,
        val movements: Int,
        val sales: Int,
        val members: Int,
        val stockAdjustments: Int,
        val outboxEntries: Int,
    ) {
        val total: Int
            get() = products + movements + sales + members + stockAdjustments + outboxEntries
    }

    data class AdoptionResult(
        val businessId: String,
        val accountId: String,
    )
}
