package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.supabase.AuthResult
import com.nextpage.data.remote.supabase.AuthService
import com.nextpage.data.remote.supabase.AuthState
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val authState: AuthState = AuthState.Unauthenticated,
    val syncState: SyncState = SyncState.Idle,
    val pendingSyncCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val authService: AuthService,
    private val syncService: SyncService?
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authService.authState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    authState = state,
                    errorMessage = if (state is AuthState.Error) state.message else null
                )

                if (state is AuthState.Authenticated) {
                    syncService?.startPeriodicSync()
                }
            }
        }

        syncService?.let { service ->
            viewModelScope.launch {
                service.syncState.collect { state ->
                    _uiState.value = _uiState.value.copy(syncState = state)
                }
            }
            viewModelScope.launch {
                service.pendingCount.collect { count ->
                    _uiState.value = _uiState.value.copy(pendingSyncCount = count)
                }
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = authService.signUp(email, password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = authService.signIn(email, password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            _uiState.value = _uiState.value.copy(syncState = SyncState.Idle)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun triggerSync() {
        viewModelScope.launch {
            syncService?.pullRemoteChanges()
            syncService?.pushLocalChanges()
        }
    }

    class Factory(
        private val authService: AuthService,
        private val syncService: SyncService?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authService, syncService) as T
        }
    }
}