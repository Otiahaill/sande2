package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.Movement
import com.example.sande.data.repository.MovementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AuditViewerViewModel(
    private val movementRepository: MovementRepository
) : ViewModel() {

    val movements: StateFlow<List<Movement>> =
        movementRepository.getAll.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
}
