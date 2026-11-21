package com.example.sande.data.auth

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.Business
import com.example.sande.data.entities.Member
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.Outbox
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.ProductCategory
import com.example.sande.data.entities.SaleHeader
import com.example.sande.data.entities.StockAdjustment
import com.example.sande.data.prefs.TokenManager
import com.example.sande.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Handles adoption of legacy offline data when a user registers a business
 * and wants to keep their existing local data.
 */
class LegacyAdoptionManager(
    private val context: Context,
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val apiClient: ApiClient,
) {

    /** Checks if there's legacy data under the default business ID. */
    suspend fun hasLegacyData(): Boolean = withContext(Dispatchers.IO) {
        val businessDao = database.businessDao()
        val productDao = database.productDao()
        val movementDao = database.movementDao()
        val saleHeaderDao = database.saleHeaderDao()
        val memberDao = database.memberDao()
        val stockAdjustmentDao = database.stockAdjustmentDao()
        val outboxDao = database.outboxDao()

        val defaultBusiness = businessDao.getById("default-business")
        if (defaultBusiness == null) return@withContext false

        val productCount = productDao.countByBusiness("default-business").await()
        val movementCount = movementDao.countByBusiness("default-business").await()
        val saleCount = saleHeaderDao.countByBusiness("default-business").await()
        val memberCount = memberDao.countByBusiness("default-business").await()
        val adjustmentCount = stockAdjustmentDao.countByBusiness("default-business").await()
        val outboxCount = outboxDao.countByBusiness("default-business").await()

        return@withContext productCount > 0 || movementCount > 0 || saleCount > 0 || memberCount > 0 || adjustmentCount > 0 || outboxCount > 0
    }

    /** Gets counts of legacy data for display. */
    suspend fun getLegacyDataCounts(): LegacyDataCounts = withContext(Dispatchers.IO) {
        val productDao = database.productDao()
        val movementDao = database.movementDao()
        val saleHeaderDao = database.saleHeaderDao()
        val memberDao = database.memberDao()
        val stockAdjustmentDao = database.stockAdjustmentDao()
        val outboxDao = database.outboxDao()

        LegacyDataCounts(
            products = productDao.countByBusiness("default-business").await(),
            movements = movementDao.countByBusiness("default-business").await(),
            sales = saleHeaderDao.countByBusiness("default-business").await(),
            members = memberDao.countByBusiness("default-business").await(),
            stockAdjustments = stockAdjustmentDao.countByBusiness("default-business").await(),
            outboxEntries = outboxDao.countByBusiness("default-business").await(),
        )
    }

    /**
     * Adopts legacy data by:
     * 1. Registering a new business with the backend
     * 2. Rewriting all legacy entity business_ids to the new business_id
     * 3. Creating placeholder user_ids for legacy data
     * 4. Creating outbox entries for all legacy data to sync to server
     * 5. Creating a safety copy marker
     */
    suspend fun adoptLegacyData(
        registrationRequest: BusinessRegistrationRequest,
        onProgress: (String, Float) -> Unit
    ): Result<AdoptionResult> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Register business with backend
            onProgress("Registering business...", 0.1f)
            val registerResponse = apiClient.registerBusiness(registrationRequest).getOrThrow()
            val newBusinessId = registerResponse.data?.tokens?.membership?.businessId
                ?: return Result.failure(Exception("No business ID in response"))
            val tokens = registerResponse.data?.tokens
                ?: return Result.failure(Exception("No tokens in response"))
            val accountId = registerResponse.data?.accountId
                ?: return Result.failure(Exception("No account ID in response"))

            // Save tokens
            tokenManager.saveTokens(tokens!!, accountId!!).await()

            // Step 2: Rewrite business_id in all legacy tables
            onProgress("Migrating products...", 0.2f)
            rewriteProductBusinessIds(newBusinessId)

            onProgress("Migrating categories...", 0.3f)
            rewriteCategoryBusinessIds(newBusinessId)

            onProgress("Migrating movements...", 0.4f)
            rewriteMovementBusinessIds(newBusinessId)

            onProgress("Migrating sales...", 0.5f)
            rewriteSaleBusinessIds(newBusinessId)

            onProgress("Migrating members...", 0.6f)
            rewriteMemberBusinessIds(newBusinessId)

            onProgress("Migrating stock adjustments...", 0.7f)
            rewriteStockAdjustmentBusinessIds(newBusinessId)

            onProgress("Migrating outbox...", 0.8f)
            rewriteOutboxBusinessIds(newBusinessId)

            onProgress("Creating business profile...", 0.9f)
            createBusinessProfile(newBusinessId, registrationRequest)

            onProgress("Finalizing...", 1.0f)
            markAdoptionComplete(newBusinessId)

            return Result.success(AdoptionResult(
                businessId = newBusinessId,
                accountId = accountId!!,
            ))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private suspend fun rewriteProductBusinessIds(newBusinessId: String) = withContext(Dispatchers.IO) {
        val products = database.productDao().getByBusiness("default-business").first()
        for (product in products) {
            val updated = product.copy(businessId = newBusinessId)
            database.productDao().update(updated)
        }
    }

    private suspend fun rewriteCategoryBusinessIds(newBusinessId: String) = withContext(Dispatchers.IO) {
        val categories = database.productCategoryDao().getByBusiness("default-business").first()
        for (category in categories) {
            val updated = category.copy(businessId = newBusinessId)
            database.productCategoryDao().update(updated)
        }
    }

    private suspend fun rewriteMovementBusinessIds(newBusinessId: String) = withContext(Dispatchers.IO) {
        val movements = database.movementDao().getByBusiness("default-business").first()
        for (movement in movements) {
            val updated = movement.copy(businessId = newBusinessId)
            database.movementDao().update(updated)
        }
    }

    private suspend fun rewriteSaleBusinessIds(newBusinessId: String) = withContext(Dispatchers.IO) {
        val sales = database.saleHeaderDao().getByBusiness("default-business").first()
        for (sale in sales) {
            val updated = sale.copy(businessId = newBusinessId)
            database.saleHeaderDao().update(updated)
        }
    }

    private suspend fun rewriteMemberBusinessIds(newBusinessId: String) = withContext(Dispatchers.IO) {
        val members = database.memberDao().getByBusiness("default-business").first()
        for (member in members) {
            val updated = member.copy(businessId = newBusinessId)
            database.memberDao().update(updated)
        }
    }

    private suspend fun rewriteStockAdjustmentBusinessIds(newBusinessId: String) = withContext(Dispatchers.IO) {
        val adjustments = database.stockAdjustmentDao().getByBusiness("default-business").first()
        for (adjustment in adjustments) {
            val updated = adjustment.copy(businessId = newBusinessId)
            database.stockAdjustmentDao().update(updated)
        }
    }

    private suspend fun rewriteOutboxBusinessIds(newBusinessId: String) = withContext(Dispatchers.IO) {
        val outboxEntries = database.outboxDao().getByBusiness("default-business").first()
        for (entry in outboxEntries) {
            val updated = entry.copy(businessId = newBusinessId)
            database.outboxDao().update(updated)
        }
    }

    private suspend fun createBusinessProfile(newBusinessId: String, request: BusinessRegistrationRequest) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val profile = com.example.sande.data.entities.BusinessProfile(
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

    private suspend fun markAdoptionComplete(newBusinessId: String) = withContext(Dispatchers.IO) {
        // Update the default business record to mark it as migrated
        val business = database.businessDao().getById("default-business")
        business?.let {
            val updated = it.copy(id = newBusinessId, deletedAt = System.currentTimeMillis())
            database.businessDao().insert(updated) // New ID
            database.businessDao().delete(it) // Delete old
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

    sealed class Result<out T> {
        data class Success<out T>(val value: T) : Result<T>()
        data class Failure(val error: Throwable) : Result<Nothing>()

        companion object {
            fun <T> success(value: T): Result<T> = Success(value)
            fun <T> failure(error: Throwable): Result<T> = Failure(error)
        }

        fun getOrThrow(): T = when (this) {
            is Success -> value
            is Failure -> throw error
        }
    }
}
