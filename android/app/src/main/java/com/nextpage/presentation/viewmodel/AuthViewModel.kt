package com.nextpage.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.presentation.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val failureKind: AuthFailureKind = AuthFailureKind.NONE
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
    private val isAuthConfigured: Boolean,
    private val hasAuthWiringIssue: Boolean
) : ViewModel() {

    companion object {
        private const val AUTH_VM_TAG = "AuthViewModel"
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    init {
        _uiState.update { it.copy(
            isConfigured = isAuthConfigured,
            hasWiringIssue = hasAuthWiringIssue
        ) }
        logDiagnostics("init")
        restoreSessionOnStart()
    }

    private fun restoreSessionOnStart() {
        viewModelScope.launch {
            val sessionResult = authRepository.getCurrentSession()
            val session = sessionResult.getOrNull()
            session?.let { triggerSyncForSession(it) }
            if (_uiState.value.currentSession == null) {
                _uiState.update { it.copy(
                    currentSession = session,
                    errorMessage = sessionResult.exceptionOrNull()?.message,
                    failureKind = classifyFailure(sessionResult.exceptionOrNull())
                ) }
            }
        }
    }

    /**
     * Initiates Google sign-in via Credential Manager One Tap (native bottom sheet).
     * No browser redirect — the One Tap result is handled directly.
     */
    fun startGoogleSignIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = true,
                errorMessage = null,
                failureKind = AuthFailureKind.NONE
            ) }
            logDiagnostics("startGoogleSignIn:loading")
            val result = authRepository.signInWithGoogle()
            result.fold(
                onSuccess = { session ->
                    triggerSyncForSession(session)
                    _uiState.update { it.copy(
                        isLoading = false,
                        currentSession = session,
                        errorMessage = null,
                        failureKind = AuthFailureKind.NONE
                    ) }
                    logDiagnostics("startGoogleSignIn:success")
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = error.message,
                        failureKind = classifyFailure(error)
                    ) }
                    _uiEvent.emit(UiEvent.ShowSnackbar(error.message ?: "Authentication failed"))
                    logDiagnostics("startGoogleSignIn:failure")
                }
            )
        }
    }

    @Deprecated("One Tap handles auth directly; OAuth callback flow is no longer used.")
    fun onGoogleAuthCallback(callbackUri: String) {
        // No-op: One Tap no longer uses browser callback
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signUp(email, password)
            _uiState.update { it.copy(
                isLoading = false,
                currentSession = result.getOrNull(),
                errorMessage = result.exceptionOrNull()?.message
            ) }
            result.exceptionOrNull()?.let {
                _uiEvent.emit(UiEvent.ShowSnackbar(it.message ?: "Sign up failed"))
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signIn(email, password)
            _uiState.update { it.copy(
                isLoading = false,
                currentSession = result.getOrNull(),
                errorMessage = result.exceptionOrNull()?.message
            ) }
            result.exceptionOrNull()?.let {
                _uiEvent.emit(UiEvent.ShowSnackbar(it.message ?: "Sign in failed"))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val result = authRepository.signOut()
            _uiState.update { it.copy(
                currentSession = if (result.isSuccess) null else it.currentSession,
                errorMessage = result.exceptionOrNull()?.message,
                failureKind = classifyFailure(result.exceptionOrNull())
            ) }
            result.exceptionOrNull()?.let {
                _uiEvent.emit(UiEvent.ShowSnackbar(it.message ?: "Sign out failed"))
            }
        }
    }

    fun continueLocally() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.signInLocally()
            _uiState.update { it.copy(
                isLoading = false,
                currentSession = result.getOrNull(),
                errorMessage = result.exceptionOrNull()?.message
            ) }
            result.exceptionOrNull()?.let {
                _uiEvent.emit(UiEvent.ShowSnackbar(it.message ?: "Failed to continue locally"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, failureKind = AuthFailureKind.NONE) }
    }

    private fun logDiagnostics(context: String) {
        val state = _uiState.value
        runCatching {
            Log.d(
                AUTH_VM_TAG,
                "Auth diagnostics [$context]: configured=${state.isConfigured}, wiring=${state.hasWiringIssue}, loading=${state.isLoading}, failure=${state.failureKind}"
            )
        }
    }

    private fun classifyFailure(error: Throwable?): AuthFailureKind {
        return when ((error as? AppError)?.category) {
            ErrorCategory.CONFIG_ERROR -> AuthFailureKind.CONFIG_ERROR
            ErrorCategory.WIRING_ERROR -> AuthFailureKind.WIRING_ERROR
            else -> if (error == null) AuthFailureKind.NONE else AuthFailureKind.UNKNOWN
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
        private val isAuthConfigured: Boolean,
        private val hasAuthWiringIssue: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(
                authRepository = authRepository,
                syncService = syncService,
                isAuthConfigured = isAuthConfigured,
                hasAuthWiringIssue = hasAuthWiringIssue
            ) as T
        }
    }
}
