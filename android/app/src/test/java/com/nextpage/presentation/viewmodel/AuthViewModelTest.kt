package com.nextpage.presentation.viewmodel

import com.nextpage.data.remote.sync.DriveSyncState
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_mapsConfigErrorKind_whenCurrentSessionFailsWithConfigError() = runTest {
        val repository = FakeAuthRepository(
            currentSessionResult = Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "SUPABASE_CONFIG_MISSING_URL",
                    message = "Missing URL",
                    component = "SupabaseConfig"
                )
            )
        )

        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = false,
            hasAuthWiringIssue = false
        )

        advanceUntilIdle()

        assertEquals(AuthFailureKind.CONFIG_ERROR, viewModel.uiState.value.failureKind)
        assertEquals("Missing URL", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun handleGoogleIdToken_setsSession_whenSignInWithGoogleSucceeds() = runTest {
        val session = AuthSession(userId = "u1", email = "u1@test.com")
        val repository = FakeAuthRepository(
            signInWithGoogleResult = Result.success(session)
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.handleGoogleIdToken("test-id-token")
        advanceUntilIdle()

        assertEquals(session, viewModel.uiState.value.currentSession)
    }

    @Test
    fun handleGoogleIdToken_setsError_whenSignInWithGoogleFails() = runTest {
        val repository = FakeAuthRepository(
            signInWithGoogleResult = Result.failure(
                AppError(
                    category = ErrorCategory.AUTH,
                    code = "GOOGLE_AUTH_CANCELLED",
                    message = "User cancelled",
                    component = "SupabaseAuthRepository"
                )
            )
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.handleGoogleIdToken("test-id-token")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentSession)
        assertEquals("User cancelled", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun signOut_clearsSession() = runTest {
        val session = AuthSession(userId = "u1", email = "u1@test.com")
        val repository = FakeAuthRepository(
            currentSessionResult = Result.success(session)
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = true
        )
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentSession)
    }

    @Test
    fun updateAtomicity_rapidMutationsNoLostState() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        // Simulate rapid consecutive state mutations via clearError (uses .update {})
        viewModel.clearError()
        viewModel.clearError()
        viewModel.clearError()

        assertEquals(
            "rapid clearError calls should produce correct final state",
            AuthFailureKind.NONE, viewModel.uiState.value.failureKind
        )
        assertEquals(
            "rapid clearError calls should clear errorMessage",
            null, viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun signUp_forwardsFullNameAndSetsSession_whenSucceeds() = runTest {
        val session = AuthSession(userId = "new-u1", email = "new@test.com", displayName = "Ana García")
        val repository = FakeAuthRepository(
            signUpResult = Result.success(session)
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.signUp("new@test.com", "password123", "Ana García")
        advanceUntilIdle()

        assertEquals("Ana García", repository.lastSignUpFullName)
        assertEquals("new@test.com", repository.lastSignUpEmail)
        assertEquals(session, viewModel.uiState.value.currentSession)
        assertEquals(AuthFailureKind.NONE, viewModel.uiState.value.failureKind)
    }

    @Test
    fun signUp_setsFailureKind_whenFails() = runTest {
        val repository = FakeAuthRepository(
            signUpResult = Result.failure(
                AppError(
                    category = ErrorCategory.AUTH,
                    code = "SIGNUP_STALE_SESSION",
                    message = "already registered",
                    component = "SupabaseAuthRepository"
                )
            )
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.signUp("taken@test.com", "password123", "Ana García")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentSession)
        assertEquals("already registered", viewModel.uiState.value.errorMessage)
        // Stale-session fix: a failure must classify (UNKNOWN here — AppError AUTH
        // is not CONFIG/WIRING), never leak a previous kind.
        assertEquals(AuthFailureKind.UNKNOWN, viewModel.uiState.value.failureKind)
    }

    @Test
    fun signIn_setsFailureKind_whenFails() = runTest {
        val repository = FakeAuthRepository(
            signInResult = Result.failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "AUTH_WIRING",
                    message = "wiring broken",
                    component = "SupabaseAuthRepository"
                )
            )
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.signIn("user@test.com", "password123")
        advanceUntilIdle()

        assertEquals(AuthFailureKind.WIRING_ERROR, viewModel.uiState.value.failureKind)
    }

    @Test
    fun resetPassword_sendsEmailAndClearsError_whenSucceeds() = runTest {
        val repository = FakeAuthRepository(
            resetPasswordResult = Result.success(Unit)
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.resetPassword("user@test.com")
        advanceUntilIdle()

        assertEquals("user@test.com", repository.lastResetPasswordEmail)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(AuthFailureKind.NONE, viewModel.uiState.value.failureKind)
    }

    @Test
    fun resetPassword_setsError_whenFails() = runTest {
        val repository = FakeAuthRepository(
            resetPasswordResult = Result.failure(
                AppError(
                    category = ErrorCategory.AUTH,
                    code = "RESET_PASSWORD_FAILED",
                    message = "no account",
                    component = "SupabaseAuthRepository"
                )
            )
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isAuthConfigured = true,
            hasAuthWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.resetPassword("missing@test.com")
        advanceUntilIdle()

        assertEquals("no account", viewModel.uiState.value.errorMessage)
        assertEquals(AuthFailureKind.UNKNOWN, viewModel.uiState.value.failureKind)
    }

    private class FakeAuthRepository(
        private val startGoogleResult: Result<String> = Result.failure(IllegalStateException("not set")),
        private val completeGoogleResult: Result<AuthSession?> = Result.success(null),
        private val signInWithGoogleResult: Result<AuthSession> = Result.failure(IllegalStateException("not set")),
        private val currentSessionResult: Result<AuthSession?> = Result.success(null),
        private val signOutResult: Result<Unit> = Result.success(Unit),
        private val signInResult: Result<AuthSession> = Result.failure(UnsupportedOperationException()),
        private val signUpResult: Result<AuthSession> = Result.failure(UnsupportedOperationException()),
        private val resetPasswordResult: Result<Unit> = Result.failure(UnsupportedOperationException())
    ) : AuthRepository {
        var lastSignInEmail: String? = null
        var lastSignUpEmail: String? = null
        var lastSignUpFullName: String? = null
        var lastResetPasswordEmail: String? = null

        override suspend fun startGoogleSignIn(): Result<String> = startGoogleResult

        override suspend fun completeGoogleSignIn(callbackUri: String): Result<AuthSession?> = completeGoogleResult

        override suspend fun signInWithGoogle(): Result<AuthSession> = signInWithGoogleResult

        override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthSession> = signInWithGoogleResult

        override suspend fun signIn(email: String, password: String): Result<AuthSession> {
            lastSignInEmail = email
            return signInResult
        }

        override suspend fun signUp(email: String, password: String, fullName: String): Result<AuthSession> {
            lastSignUpEmail = email
            lastSignUpFullName = fullName
            return signUpResult
        }

        override suspend fun resetPassword(email: String): Result<Unit> {
            lastResetPasswordEmail = email
            return resetPasswordResult
        }

        override suspend fun signOut(): Result<Unit> = signOutResult

        override suspend fun signInLocally(): Result<AuthSession> = Result.failure(UnsupportedOperationException())

        override suspend fun getCurrentSession(): Result<AuthSession?> = currentSessionResult
    }

    private class FakeSyncService : SyncService {
        override val syncState: Flow<DriveSyncState> = MutableStateFlow(DriveSyncState.Idle)
        override val pendingCount: Flow<Int> = emptyFlow()
        val events = mutableListOf<String>()

        override suspend fun bootstrap(userId: String): Result<Unit> {
            events += "bootstrap:$userId"
            return Result.success(Unit)
        }

        override suspend fun schedulePush(): Result<Unit> {
            events += "push"
            return Result.success(Unit)
        }

        override suspend fun schedulePull(): Result<Unit> {
            events += "pull"
            return Result.success(Unit)
        }

        override suspend fun stop() {
            events += "stop"
        }
    }
}
