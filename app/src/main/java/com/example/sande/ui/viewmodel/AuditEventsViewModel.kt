package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.AuditEvent
import com.example.sande.data.repository.AuditEventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

class AuditEventsViewModel(
    private val auditEventRepository: AuditEventRepository
) : ViewModel() {

    private val _filterSeverity = MutableStateFlow("ALL")
    val filterSeverity: StateFlow<String> = _filterSeverity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "ALL")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val events: StateFlow<List<AuditEvent>> =
        combine(
            auditEventRepository.getByBusiness("default-business"),
            filterSeverity,
            searchQuery
        ) { allEvents, severity, query ->
            var filtered = allEvents

            // Apply severity filter
            when (severity) {
                "ALL" -> {}
                else -> filtered = filtered.filter { it.severity == severity }
            }

            // Apply search query
            if (query.isNotBlank()) {
                val lowerQuery = query.lowercase()
                filtered = filtered.filter { event ->
                    event.entityType.lowercase().contains(lowerQuery) ||
                    event.entityId.lowercase().contains(lowerQuery) ||
                    event.action.lowercase().contains(lowerQuery) ||
                    event.actorId?.lowercase()?.contains(lowerQuery) == true ||
                    event.metadataJson?.lowercase()?.contains(lowerQuery) == true
                }
            }

            // Sort by date descending
            filtered.sortedByDescending { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSeverityFilter(severity: String) {
        _filterSeverity.value = severity
    }

    fun search(query: String) {
        _searchQuery.value = query
    }
}
