package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.sande.data.repository.AuthRepository
import com.example.sande.domain.PermissionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val permissionPolicy: PermissionPolicy
) : ViewModel() {

    val currentUser: String? get() = authRepository.currentUser
    val isSignedIn: Boolean get() = authRepository.isSignedIn

    private val _negativeStockPolicy = MutableStateFlow("WARN")
    val negativeStockPolicy = _negativeStockPolicy.asStateFlow()

    fun setNegativeStockPolicy(policy: String) {
        _negativeStockPolicy.value = policy
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
