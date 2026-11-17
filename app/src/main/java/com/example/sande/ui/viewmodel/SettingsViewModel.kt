package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.AppSettings
import com.example.sande.data.repository.AuthRepository
import com.example.sande.domain.PermissionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val permissionPolicy: PermissionPolicy,
    private val settings: AppSettings
) : ViewModel() {

    val currentUser: StateFlow<String?> = authRepository.session.map { it?.uid }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val currentUserEmail: StateFlow<String?> = authRepository.session.map { it?.email }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val currentUserRole: StateFlow<String?> = authRepository.session.map { it?.role }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    val isSignedIn: Boolean get() = authRepository.isSignedIn

    private val _negativeStockPolicy = MutableStateFlow(settings.negativeStockPolicy)
    val negativeStockPolicy = _negativeStockPolicy.asStateFlow()

    fun setNegativeStockPolicy(policy: String) {
        settings.negativeStockPolicy = policy
        _negativeStockPolicy.value = policy
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
