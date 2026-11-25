package com.example.sande.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Local sync state persisted in database.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: String = "default", // Single row
    val businessId: String,
    val deviceCode: String,
    val cursor: Long = 0, // Server-side sequence number
    val lastPullAt: Long = 0,
    val lastPushAt: Long = 0,
    val pendingPushCount: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * In-memory sync state for UI.
 */
class SyncState(
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), SyncStatus.IDLE)

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), 0)

    private val _lastPullAt = MutableStateFlow(0L)
    val lastPullAt: StateFlow<Long> = _lastPullAt
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), 0L)

    private val _lastPushAt = MutableStateFlow(0L)
    val lastPushAt: StateFlow<Long> = _lastPushAt
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), 0L)

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), null)

    fun setStatus(status: SyncStatus) { _status.value = status }
    fun setPendingCount(count: Int) { _pendingCount.value = count }
    fun setLastPullAt(time: Long) { _lastPullAt.value = time }
    fun setLastPushAt(time: Long) { _lastPushAt.value = time }
    fun setLastError(error: String?) { _lastError.value = error }
}

enum class SyncStatus {
    IDLE,
    PUSHING,
    PULLING,
    SYNCING, // Both
    OFFLINE,
    ERROR,
    NEEDS_ATTENTION, // Has exceptions
}

data class SyncResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val errors: Int = 0,
    val exceptions: Int = 0,
    val durationMs: Long = 0,
)

/**
 * Sync configuration.
 */
data class SyncConfig(
    val pushBatchSize: Int = 50,
    val pullBatchSize: Int = 100,
    val maxRetries: Int = 3,
    val baseRetryDelayMs: Long = 1000,
    val maxRetryDelayMs: Long = 60_000,
    val syncIntervalMs: Long = 5 * 60 * 1000, // 5 minutes
    val pullIntervalMs: Long = 30 * 1000, // 30 seconds
)
