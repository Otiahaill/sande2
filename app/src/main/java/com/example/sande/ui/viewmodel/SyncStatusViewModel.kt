package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.prefs.TokenManager
import com.example.sande.data.repository.OutboxEntryRepository
import com.example.sande.data.repository.SyncCursorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncStatusViewModel(
    private val tokenManager: TokenManager,
    private val outboxEntryRepository: OutboxEntryRepository,
    private val syncCursorRepository: SyncCursorRepository,
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _unsyncedCount = MutableStateFlow(0)
    val unsyncedCount: StateFlow<Int> = _unsyncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0L)

    private val _cursor = MutableStateFlow(0L)
    val cursor: StateFlow<Long> = _cursor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0L)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun load() {
        viewModelScope.launch {
            val deviceCode = tokenManager.getDeviceCode().await() ?: "unknown"
            val businessId = tokenManager.getBusinessId().await() ?: "default-business"

            // Load unsynced count
            outboxEntryRepository.getUnsyncedByBusiness(businessId).first().let {
                _unsyncedCount.value = it.size
            }

            // Load cursor
            syncCursorRepository.getByBusinessAndDevice(businessId, deviceCode).firstOrNull()?.let {
                _cursor.value = it.cursor
                _lastSyncAt.value = it.lastPushAt
            }
        }
    }

    fun refresh() {
        load()
    }

    fun setStatusMessage(message: String?) {
        _statusMessage.value = message
    }

    fun setIsSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }
}
