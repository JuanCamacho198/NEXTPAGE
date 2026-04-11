package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.GoogleSignInOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthFailureKind {
    NONE,
    CONFIG_ERROR,
    WIRING_ERROR,
    UNKNOWN
}

data class AuthUiState(
    val currentSession: AuthSession? = null,
    val isConfigured: Boolean = true,
    val hasWiringIssue: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val failureKind: AuthFailureKind = AuthFailureKind.NONE,
    val pendingGoogleSignInUrl: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
    private val isSupabaseConfigured: Boolean,
    private val hasSupabaseWiringIssue: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            isConfigured = isSupabaseConfigured,
            hasWiringIssue = hasSupabaseWiringIssue
        )
        restoreSessionOnStart()
    }

    private fun restoreSessionOnStart() {
        viewModelScope.launch {
            val sessionResult = authRepository.getCurrentSession()
            val session = sessionResult.getOrNull()
            session?.let { triggerSyncForSession(it) }
            _uiState.value = _uiState.value.copy(
                currentSession = session,
                errorMessage = sessionResult.exceptionOrNull()?.message,
                failureKind = classifyFailure(sessionResult.exceptionOrNull())
            )
        }
    }

    fun startGoogleSignIn() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                failureKind = AuthFailureKind.NONE,
                pendingGoogleSignInUrl = null
            )
            val result = authRepository.startGoogleSignIn()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                pendingGoogleSignInUrl = result.getOrNull(),
                errorMessage = result.exceptionOrNull()?.message,
                failureKind = classifyFailure(result.exceptionOrNull())
            )
        }
    }

    fun onGoogleAuthCallback(callbackUri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val outcome = authRepository.completeGoogleSignIn(callbackUri)) {
                is GoogleSignInOutcome.Success -> {
                    triggerSyncForSession(outcome.session)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentSession = outcome.session,
                        errorMessage = null,
                        failureKind = AuthFailureKind.NONE,
                        pendingGoogleSignInUrl = null
                    )
                }

                GoogleSignInOutcome.Cancelled -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Google sign-in was cancelled.",
                        failureKind = AuthFailureKind.NONE,
                        pendingGoogleSignInUrl = null
                    )
                }

                is GoogleSignInOutcome.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = outcome.error.message,
                        failureKind = classifyFailure(outcome.error),
                        pendingGoogleSignInUrl = null
                    )
                }
            }
        }
    }

    fun consumePendingGoogleSignInUrl() {
        _uiState.value = _uiState.value.copy(pendingGoogleSignInUrl = null)
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
                errorMessage = result.exceptionOrNull()?.message,
                failureKind = classifyFailure(result.exceptionOrNull()),
                pendingGoogleSignInUrl = null
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, failureKind = AuthFailureKind.NONE)
    }

    private fun classifyFailure(error: Throwable?): AuthFailureKind {
        return when ((error as? AppError)?.category) {
            ErrorCategory.CONFIG_ERROR -> AuthFailureKind.CONFIG_ERROR
            ErrorCategory.WIRING_ERROR -> AuthFailureKind.WIRING_ERROR
            null -> if (error == null) AuthFailureKind.NONE else AuthFailureKind.UNKNOWN
        }
    }

    private suspend fun triggerSyncForSession(session: AuthSession) {
        val bootstrap = syncService.bootstrap(session.userId)
        if (bootstrap.isFailure) {
            return
        }
        syncService.schedulePull()
        syncService.schedulePush()
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val syncService: SyncService,
        private val isSupabaseConfigured: Boolean,
        private val hasSupabaseWiringIssue: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(
                authRepository = authRepository,
                syncService = syncService,
                isSupabaseConfigured = isSupabaseConfigured,
                hasSupabaseWiringIssue = hasSupabaseWiringIssue
            ) as T
        }
    }
}
