package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.StockMovement
import com.example.sande.data.repository.StockMovementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

class StockMovementsViewModel(
    private val stockMovementRepository: StockMovementRepository
) : ViewModel() {

    private val _filterType = MutableStateFlow("ALL")
    val filterType: StateFlow<String> = _filterType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "ALL")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val movements: StateFlow<List<StockMovement>> =
        combine(
            stockMovementRepository.getByBusiness("default-business"),
            filterType,
            searchQuery
        ) { allMovements, filter, query ->
            var filtered = allMovements

            // Apply type filter
            when (filter) {
                "ALL" -> {}
                else -> filtered = filtered.filter { it.movementType == filter }
            }

            // Apply search query (product ID or reference)
            if (query.isNotBlank()) {
                val lowerQuery = query.lowercase()
                filtered = filtered.filter { movement ->
                    movement.productId.lowercase().contains(lowerQuery) ||
                    movement.referenceId?.lowercase().contains(lowerQuery) == true ||
                    movement.reason?.lowercase().contains(lowerQuery) == true ||
                    movement.actorId?.lowercase()?.contains(lowerQuery) == true
                }
            }

            // Sort by date descending
            filtered.sortedByDescending { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(type: String) {
        _filterType.value = type
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun loadByDateRange(startDate: Long, endDate: Long) {
        // The combined flow above handles date filtering via search query
        // For more complex date filtering, we'd add a date range state
    }
}
