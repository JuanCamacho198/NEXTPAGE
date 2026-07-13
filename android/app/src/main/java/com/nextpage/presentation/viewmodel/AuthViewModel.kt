package com.nextpage.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.supabase.SupabaseBookCatalogSync
import com.nextpage.data.remote.supabase.SupabaseProgressSync
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

/**
 * Classifies the source of an auth failure so the UI can react differently
 * (e.g. show a "fix config" CTA for [CONFIG_ERROR] vs. a generic error toast
 * for [UNKNOWN]).
 */
enum class AuthFailureKind {
    /** No failure — session is healthy or has not yet been attempted. */
    NONE,
    /** Failure caused by missing/malformed auth configuration (build flag, keys). */
    CONFIG_ERROR,
    /** Failure caused by a wiring/dependency problem in the auth chain. */
    WIRING_ERROR,
    /** Failure that does not match the known categories. */
    UNKNOWN
}

/**
 * AuthUiState — UI state for the authentication flow (sign-in / sign-up / sign-out).
 *
 * **Used by**: AuthScreen
 * **Mutated by**: [AuthViewModel.handleGoogleIdToken], [AuthViewModel.signUp],
 *                 [AuthViewModel.signIn], [AuthViewModel.signOut],
 *                 [AuthViewModel.continueLocally], [AuthViewModel.clearError],
 *                 and the init-block session restoration.
 *
 * @property currentSession The active auth session, or `null` if signed out / not yet known.
 * @property isConfigured `true` if the auth subsystem is configured at runtime (build keys present).
 * @property hasWiringIssue `true` if a DI/dependency wiring problem was detected at startup.
 * @property isLoading `true` while an auth call is in flight.
 * @property errorMessage Human-readable error string from the last failed call (or `null`).
 * @property failureKind Coarse classification of [errorMessage] (see [AuthFailureKind]).
 */
data class AuthUiState(
    val currentSession: AuthSession? = null,
    val isCheckingSession: Boolean = true,
    val isConfigured: Boolean = true,
    val hasWiringIssue: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val failureKind: AuthFailureKind = AuthFailureKind.NONE
)

/**
 * AuthViewModel — Owns the user's authentication state and exposes it as
 * [uiState] for [com.nextpage.presentation.screen.AuthScreen]. Handles
 * Google One Tap sign-in, email/password sign-up and sign-in, anonymous
 * local continuation, and sign-out. On successful sign-in it bootstraps
 * remote sync via [SyncService].
 *
 * @param authRepository Remote auth operations (Google, email, local session).
 * @param syncService Sync bootstrap and pull/push scheduler.
 * @param supabaseProgressSync Outbox-to-Supabase processor for reading progress.
 * @param supabaseBookCatalogSync Catalog sync for pushing local books to Supabase.
 * @param isAuthConfigured Build-time flag indicating auth keys are wired in.
 * @param hasAuthWiringIssue Build-time flag indicating a DI wiring problem.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val syncService: SyncService,
    private val supabaseProgressSync: SupabaseProgressSync? = null,
    private val supabaseBookCatalogSync: SupabaseBookCatalogSync? = null,
    private val isAuthConfigured: Boolean,
    private val hasAuthWiringIssue: Boolean
) : ViewModel() {

    companion object {
        private const val AUTH_VM_TAG = "AuthViewModel"
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    /**
     * Current authentication state for the AuthScreen.
     *
     * **Emits when**: init (configuration flags + restored session), any auth
     *                action (sign-in, sign-up, sign-out, continue-locally),
     *                or [clearError] is called.
     * **Initial value**: [AuthUiState] with `isConfigured = true`, `hasWiringIssue = false`,
     *                    no session, not loading, no error.
     * **Lifecycle**: hot, lifetime-scoped to the ViewModel.
     */
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    /**
     * One-shot UI events for snackbars (errors from failed auth calls).
     *
     * **Emits when**: a sign-in / sign-up / sign-out / continue-locally call fails.
     * **Backpressure**: SharedFlow with default buffer; no replay for past events.
     */
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
                    failureKind = classifyFailure(sessionResult.exceptionOrNull()),
                    isCheckingSession = false
                ) }
            } else {
                _uiState.update { it.copy(isCheckingSession = false) }
            }
        }
    }

    /**
     * Completes Google sign-in with an ID token obtained from Credential Manager.
     * The UI layer is responsible for token acquisition; the token is exchanged
     * with Supabase via [AuthRepository.signInWithGoogleIdToken].
     *
     * Side effects:
     * 1. Sets `isLoading = true` and clears any prior `errorMessage`/`failureKind`.
     * 2. Calls [AuthRepository.signInWithGoogleIdToken] — token exchange with Supabase.
     * 3. On success: triggers sync for the new session and updates `currentSession`.
     * 4. On failure: sets `errorMessage`/`failureKind` and emits a `ShowSnackbar` event.
     */
    fun handleGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = true,
                errorMessage = null,
                failureKind = AuthFailureKind.NONE
            ) }
            logDiagnostics("handleGoogleIdToken:loading")
            val result = authRepository.signInWithGoogleIdToken(idToken)
            result.fold(
                onSuccess = { session ->
                    triggerSyncForSession(session)
                    _uiState.update { it.copy(
                        isLoading = false,
                        currentSession = session,
                        errorMessage = null,
                        failureKind = AuthFailureKind.NONE
                    ) }
                    logDiagnostics("handleGoogleIdToken:success")
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = error.message,
                        failureKind = classifyFailure(error)
                    ) }
                    _uiEvent.emit(UiEvent.ShowSnackbar(error.message ?: "Authentication failed"))
                    logDiagnostics("handleGoogleIdToken:failure")
                }
            )
        }
    }

    /**
     * @deprecated Use [handleGoogleIdToken] instead. Credential Manager is now
     * launched from the UI layer; this method throws to prevent accidental use.
     */
    @Deprecated("Use handleGoogleIdToken instead")
    fun startGoogleSignIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = true,
                errorMessage = "Google sign-in flow has changed. Please restart the app.",
                failureKind = AuthFailureKind.WIRING_ERROR
            ) }
            _uiEvent.emit(UiEvent.ShowSnackbar("Sign-in method updated. Please try again."))
        }
    }

    @Deprecated("One Tap handles auth directly; OAuth callback flow is no longer used.")
    fun onGoogleAuthCallback(callbackUri: String) {
        // No-op: One Tap no longer uses browser callback
    }

    /**
     * Signs the user up with email and password.
     *
     * Side effects:
     * 1. Sets `isLoading = true`, clears `errorMessage`.
     * 2. Calls [AuthRepository.signUp].
     * 3. On result: clears loading, sets `currentSession` (or leaves null on failure).
     * 4. On failure: emits a `ShowSnackbar` event with the error message.
     *
     * @param email User email address.
     * @param password Account password.
     */
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

    /**
     * Signs the user in with email and password.
     *
     * Side effects:
     * 1. Sets `isLoading = true`, clears `errorMessage`.
     * 2. Calls [AuthRepository.signIn].
     * 3. On result: clears loading, sets `currentSession` (or leaves null on failure).
     * 4. On failure: emits a `ShowSnackbar` event with the error message.
     *
     * @param email User email address.
     * @param password Account password.
     */
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

    /**
     * Signs the user out and clears the local session.
     *
     * Side effects:
     * 1. Calls [AuthRepository.signOut].
     * 2. On success: `currentSession` becomes `null`.
     * 3. On failure: `currentSession` is preserved, `errorMessage` is set,
     *    and a `ShowSnackbar` event is emitted.
     */
    fun signOut() {
        viewModelScope.launch {
            supabaseProgressSync?.stop()
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

    /**
     * Continues without a remote account — creates / restores a local-only session.
     *
     * Side effects:
     * 1. Sets `isLoading = true`.
     * 2. Calls [AuthRepository.signInLocally].
     * 3. On result: clears loading, sets `currentSession` (or leaves null on failure).
     * 4. On failure: emits a `ShowSnackbar` event with the error message.
     */
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

    /**
     * Clears the current error message and failure kind from [uiState].
     *
     * Side effects:
     * 1. Sets `errorMessage = null` and `failureKind = AuthFailureKind.NONE`.
     * 2. Does not emit a `UiEvent` — purely a local state reset (e.g. when the
     *    user dismisses the error banner in the UI).
     */
    /**
     * Sets a user-facing error message and emits a snackbar.
     * Used by the UI layer for errors that happen before the auth call (e.g.,
     * unexpected credential type from Credential Manager).
     */
    fun setError(message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = false,
                errorMessage = message,
                failureKind = AuthFailureKind.UNKNOWN
            ) }
            _uiEvent.emit(UiEvent.ShowSnackbar(message))
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

        // Start Supabase outbox processing and Realtime subscription
        supabaseProgressSync?.startProcessing()
        supabaseProgressSync?.subscribeToRealtimeChanges()

        // Push local Android books to Supabase catalog so Desktop discovers them
        supabaseBookCatalogSync?.bootstrap()
    }

    /**
     * ViewModelProvider.Factory for [AuthViewModel].
     *
     * Use when the ViewModel cannot be constructor-injected by the DI container
     * (e.g. legacy `viewModels()` call sites).
     */
    class Factory(
        private val authRepository: AuthRepository,
        private val syncService: SyncService,
        private val supabaseProgressSync: SupabaseProgressSync?,
        private val supabaseBookCatalogSync: SupabaseBookCatalogSync?,
        private val isAuthConfigured: Boolean,
        private val hasAuthWiringIssue: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(
                authRepository = authRepository,
                syncService = syncService,
                supabaseProgressSync = supabaseProgressSync,
                supabaseBookCatalogSync = supabaseBookCatalogSync,
                isAuthConfigured = isAuthConfigured,
                hasAuthWiringIssue = hasAuthWiringIssue
            ) as T
        }
    }
}
