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

    data class Session(val uid: String, val email: String?, val idToken: String?)

    suspend fun signIn(email: String, password: String): Session {
        Thread.sleep(600)
        val session = Session(uid = "dev-uid-001", email = email, idToken = "mock-id-token")
        _session.update { session }
        return session
    }

    suspend fun signOut() {
        _session.update { null }
    }

    val currentUser: String? get() = _session.value?.uid
    val isSignedIn: Boolean get() = _session.value != null
}
