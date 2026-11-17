package com.example.sande.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AuthRepository {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    data class Session(
        val uid: String,
        val email: String?,
        val idToken: String?,
        val role: String = "OWNER",  // "OWNER" or "SALESPERSON"
        val businessId: String = "default-business"
    )

    suspend fun signIn(email: String, password: String): Session {
        // Local-first auth - in production would validate against local user store
        // For demo, accept any email/password and assign role based on email
        val role = if (email.contains("owner", ignoreCase = true)) "OWNER" else "SALESPERSON"
        val session = Session(
            uid = "dev-uid-001",
            email = email,
            idToken = "mock-id-token",
            role = role,
            businessId = "default-business"
        )
        _session.update { session }
        return session
    }

    suspend fun signOut() {
        _session.update { null }
    }

    val currentUser: String? get() = _session.value?.uid
    val currentUserRole: String? get() = _session.value?.role
    val currentUserEmail: String? get() = _session.value?.email
    val isSignedIn: Boolean get() = _session.value != null
}
