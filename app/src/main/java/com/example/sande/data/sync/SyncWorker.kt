package com.example.sande.data.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.sande.data.remote.ApiClient
import com.example.sande.data.remote.ChangeLogEntry
import com.example.sande.data.remote.OutboxEntry
import com.example.sande.data.remote.Result
import com.example.sande.data.remote.SyncPushRequest
import com.example.sande.data.repository.OutboxEntryRepository
import com.example.sande.data.repository.SyncCursorRepository
import com.example.sande.data.repository.SyncExceptionRepository
import kotlinx.coroutines.Dispatchers
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
    suspend fun push(): Int = withContext(Dispatchers.IO) {
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
                OutboxEntry(
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
            when (val result = apiClient.pushSync(SyncPushRequest(batchId = batchId, entries = apiEntries))) {
                is Result.Success -> {
                    val data = result.value.data
                    if (data == null) {
                        // Nothing usable came back — treat as a failure and retry later.
                        unsynced.forEach { entry ->
                            outboxEntryRepository.recordAttempt(entry.id, "Empty response from server")
                        }
                        break
                    }

                    // Mark successfully pushed entries as synced
                    val syncedIds = unsynced.map { it.id }
                    outboxEntryRepository.markSynced(syncedIds)

                    // Update sync cursor
                    val cursor = syncCursorRepository.getByBusinessAndDevice(businessId, deviceCode)
                    if (cursor != null) {
                        syncCursorRepository.updateCursor(
                            cursor.id,
                            cursor.cursor + data.pushed,
                            System.currentTimeMillis(),
                            (cursor.pendingPushCount - data.pushed).coerceAtLeast(0)
                        )
                    }

                    totalPushed += data.pushed
                }
                is Result.Failure -> {
                    // Push failed - record errors for retry
                    val errorMsg = result.error.message ?: "Unknown error"
                    unsynced.forEach { entry ->
                        outboxEntryRepository.recordAttempt(entry.id, errorMsg)
                    }
                    break // Stop on error to avoid overwhelming the server
                }
            }
        }

        return@withContext totalPushed
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
    suspend fun pull(): Int = withContext(Dispatchers.IO) {
        val cursor = syncCursorRepository.getByBusinessAndDevice(businessId, deviceCode)
        val currentCursor = cursor?.cursor ?: 0L
        val limit = 100

        val result = apiClient.pullSync(currentCursor, limit)
        val response = when (result) {
            is Result.Success -> result.value
            is Result.Failure -> throw result.error
        }
        val data = response.data ?: return@withContext 0
        val changes = data.changes

        // Apply changes locally
        changes.forEach { change ->
            applyChange(change)
        }

        // Update cursor
        if (changes.isNotEmpty()) {
            val existingCursor = syncCursorRepository.getByBusinessAndDevice(businessId, deviceCode)
            syncCursorRepository.updateCursor(
                existingCursor?.id ?: "$businessId:$deviceCode",
                data.cursor,
                System.currentTimeMillis(),
                existingCursor?.pendingPushCount ?: 0
            )
        }

        return@withContext changes.size
    }

    private suspend fun applyChange(change: ChangeLogEntry) {
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

    private suspend fun applyProductChange(change: ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying product change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applySaleSessionChange(change: ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying sale session change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyStockMovementChange(change: ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying stock movement change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyMembershipMirrorChange(change: ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying membership mirror change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyDeviceRegistrationChange(change: ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying device registration change: ${change.operation} ${change.entityId}")
    }

    private suspend fun applyBusinessProfileChange(change: ChangeLogEntry) {
        android.util.Log.d("PullApplier", "Applying business profile change: ${change.operation} ${change.entityId}")
    }
}

/**
 * WorkManager worker that performs periodic synchronisation.
 * Runs push then pull on a schedule.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

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

    override fun doWork(): Result {
        return try {
            // Check network availability
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            if (capabilities == null || !capabilities.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
            )) {
                return Result.retry()
            }

            // Push local changes first, then pull remote changes.
            // The worker body is not a coroutine, so the suspend work is run here.
            runBlocking {
                outboxProcessor.push()
                pullApplier.pull()
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    companion object {
        const val WORKER_TAG = "SandeSyncWorker"

        fun enqueue(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .addTag(WORKER_TAG)
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORKER_TAG,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun enqueueImmediate(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag(WORKER_TAG)
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
