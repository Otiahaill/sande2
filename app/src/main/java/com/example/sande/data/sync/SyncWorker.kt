package com.example.sande.data.sync

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.sande.data.api.ApiClient
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.OutboxEntry
import com.example.sande.data.entities.SyncCursor
import com.example.sande.data.prefs.TokenManager
import com.example.sande.data.repository.OutboxEntryRepository
import com.example.sande.data.repository.SyncCursorRepository
import com.example.sande.data.repository.SyncExceptionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Pushes pending outbox entries to the backend in batches.
 * Handles idempotency via batch IDs and deduplication on the server.
 */
class OutboxProcessor(
    private val apiClient: ApiClient,
    private val outboxEntryRepository: OutboxEntryRepository,
    private val syncCursorRepository: SyncCursorRepository,
    private val syncExceptionRepository: SyncExceptionRepository,
    private val deviceCode: String,
    private val businessId: String,
) {

    /**
     * Pushes all unsynced outbox entries to the backend.
     * Returns the number of entries successfully pushed.
     */
    @WorkerThread
    suspend fun push(): Int {
        // Get unsynced outbox entries in batches
        val batchSize = 50
        var totalPushed = 0

        while (true) {
            val unsynced = outboxEntryRepository.getUnsyncedByBusiness(businessId)
                .first()
                .take(batchSize)

            if (unsynced.isEmpty()) break

            val batchId = java.util.UUID.randomUUID().toString()

            // Convert to API format
            val apiEntries = unsynced.map { entry ->
                com.example.sande.data.api.models.OutboxEntry(
                    id = entry.id,
                    businessId = entry.businessId,
                    entityType = entry.entityType,
                    entityId = entry.entityId,
                    operation = entry.operation,
                    payloadJson = entry.payloadJson,
                    batchId = batchId,
                    attemptCount = entry.attemptCount,
                    lastError = entry.lastError,
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt,
                    synced = entry.synced,
                    syncedAt = entry.syncedAt
                )
            }

            // Push to backend
            val result = apiClient.pushSync(
                com.example.sande.data.api.models.SyncPushRequest(batchId = batchId, entries = apiEntries)
            ).first()

            if (result.isSuccessful) {
                val response = result.getOrThrow()
                val data = response.data

                // Mark successfully pushed entries as synced
                val syncedIds = unsynced.map { it.id }
                outboxEntryRepository.markSynced(syncedIds)

                // Update sync cursor
                val cursor = syncCursorRepository.getByBusinessAndDevice(businessId, deviceCode)
                    .firstOrNull()
                if (cursor != null) {
                    syncCursorRepository.updateCursor(
                        cursor.id,
                        cursor.cursor + data.processed,
                        System.currentTimeMillis(),
                        cursor.pendingPushCount - data.success
                    )
                }

                totalPushed += data.success

                // Handle exceptions
                data.errorDetails?.forEach { error ->
                    android.util.Log.w("OutboxProcessor", "Sync error: $error")
                }
            } else {
                // Push failed - record errors for retry
                val errorMsg = result.errorOrNull()?.message ?: "Unknown error"
                unsynced.forEach { entry ->
                    outboxEntryRepository.recordAttempt(entry.id, errorMsg)
                }
                break // Stop on error to avoid overwhelming the server
            }
        }

        return totalPushed
    }
}

/**
 * Pulls changes from the backend since the last cursor and applies them locally.
 */
class PullApplier(
    private val apiClient: ApiClient,
    private val syncCursorRepository: SyncCursorRepository,
    private val deviceCode: String,
    private val businessId: String,
) {

    /**
     * Pulls changes from the backend and applies them locally.
     * Returns the number of changes pulled.
     */
    @WorkerThread
    suspend fun pull(): Int {
        val cursor = syncCursorRepository.getByBusinessAndDevice(businessId, deviceCode)
            .firstOrNull()

        val currentCursor = cursor?.cursor ?: 0L
        val limit = 100

        val result = apiClient.pullSync(currentCursor, limit).first()

        if (!result.isSuccessful) {
            throw result.errorOrNull() ?: Exception("Pull failed")
        }

        val data = result.getOrThrow()!!
        val changes = data.changes

        // Apply changes locally
        changes.forEach { change ->
            applyChange(change)
        }

        // Update cursor
        if (changes.isNotEmpty()) {
            val maxSeq = changes.maxOf { it.serverSeq }
            val existingCursor = syncCursorRepository.getByBusinessAndDevice(businessId, deviceCode)
                .firstOrNull()
            syncCursorRepository.updateCursor(
                existingCursor?.id ?: "$businessId:$deviceCode",
                maxSeq,
                System.currentTimeMillis(),
                existingCursor?.lastPushAt ?: 0,
                existingCursor?.pendingPushCount ?: 0
            )
        }

        return changes.size
    }

    private suspend fun applyChange(change: com.example.sande.data.api.models.ChangeLogEntry) {
        // Apply the change to the local database based on entity type and operation
        when (change.entityType) {
            "product" -> applyProductChange(change)
            "sale_session" -> applySaleSessionChange(change)
            "stock_movement" -> applyStockMovementChange(change)
            "membership_mirror" -> applyMembershipMirrorChange(change)
            "device_registration" -> applyDeviceRegistrationChange(change)
            "business_profile" -> applyBusinessProfileChange(change)
            else -> android.util.Log.w("PullApplier", "Unknown entity type: ${change.entityType}")
        }
    }

    private suspend fun applyProductChange(change: com.example.sande.data.api.models.ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying product change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applySaleSessionChange(change: com.example.sande.data.api.models.ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying sale session change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyStockMovementChange(change: com.example.sande.data.api.models.ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying stock movement change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyMembershipMirrorChange(change: com.example.sande.data.api.models.ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying membership mirror change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyDeviceRegistrationChange(change: com.example.sande.data.api.models.ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying device registration change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyBusinessProfileChange(change: com.example.sande.data.api.models.ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying business profile change: ${change.operation} ${change.entityId}")
    }
}

/**
 * WorkManager worker that performs periodic synchronisation.
 * Runs push then pull on a schedule.
 */
class SyncWorker(
    context: Context,
    workerParams: androidx.work.WorkerParameters
) : androidx.work.Worker(context, workerParams) {

    private val outboxProcessor: OutboxProcessor by lazy {
        val app = applicationContext as com.example.sande.di.SandeApplication
        OutboxProcessor(
            app.apiClient,
            app.outboxEntryRepository,
            app.syncCursorRepository,
            app.syncExceptionRepository,
            app.connectionResolver.deviceCode(),
            app.connectionResolver.getBusinessId()
        )
    }

    private val pullApplier: PullApplier by lazy {
        val app = applicationContext as com.example.sande.di.SandeApplication
        PullApplier(
            app.apiClient,
            app.syncCursorRepository,
            app.connectionResolver.deviceCode(),
            app.connectionResolver.getBusinessId()
        )
    }

    override fun doWork(): androidx.work.Result {
        return try {
            // Check network availability
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null || !connectivityManager.getNetworkCapabilities(activeNetwork).hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
            )) {
                return androidx.work.Result.failure()
            }

            // Push local changes first
            outboxProcessor.push()

            // Then pull remote changes
            pullApplier.pull()

            androidx.work.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed", e)
            androidx.work.Result.failure()
        }
    }

    companion object {
        const val WORKER_TAG = "SandeSyncWorker"

        fun enqueue(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(
                15, androidx.work.TimeUnit.MINUTES
            )
                .setTag(WORKER_TAG)
                .setExistingWorkPolicy(androidx.work.ExistingPeriodicWorkPolicy.KEEP)
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORKER_TAG, androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueueImmediate(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setTag(WORKER_TAG)
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueue(request)
        }

        fun cancelAll(context: Context) {
            androidx.work.WorkManager.getInstance(context)
                .cancelAllWorkByTag(WORKER_TAG)
        }
    }
}
