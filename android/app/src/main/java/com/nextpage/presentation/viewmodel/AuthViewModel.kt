package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val currentSession: AuthSession? = null,
    val isConfigured: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val isSupabaseConfigured: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(isConfigured = isSupabaseConfigured)
        viewModelScope.launch {
            val sessionResult = authRepository.getCurrentSession()
            _uiState.value = _uiState.value.copy(
                currentSession = sessionResult.getOrNull(),
                errorMessage = sessionResult.exceptionOrNull()?.message
            )
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signUp(email, password)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentSession = result.getOrNull(),
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signIn(email, password)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentSession = result.getOrNull(),
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val result = authRepository.signOut()
            _uiState.value = _uiState.value.copy(
                currentSession = if (result.isSuccess) null else _uiState.value.currentSession,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val isSupabaseConfigured: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository, isSupabaseConfigured) as T
        }
    }
}
