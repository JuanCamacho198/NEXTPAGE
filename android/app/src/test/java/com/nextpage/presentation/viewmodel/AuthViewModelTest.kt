package com.nextpage.presentation.viewmodel

import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.GoogleSignInOutcome
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
            isSupabaseConfigured = false,
            hasSupabaseWiringIssue = false
        )

        advanceUntilIdle()

        assertEquals(AuthFailureKind.CONFIG_ERROR, viewModel.uiState.value.failureKind)
        assertEquals("Missing URL", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun startGoogleSignIn_setsPendingUrl_whenRepositoryReturnsUrl() = runTest {
        val repository = FakeAuthRepository(startGoogleResult = Result.success("https://example/auth"))
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isSupabaseConfigured = true,
            hasSupabaseWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.startGoogleSignIn()
        advanceUntilIdle()

        assertEquals("https://example/auth", viewModel.uiState.value.pendingGoogleSignInUrl)
    }

    @Test
    fun onGoogleAuthCallback_setsSession_onSuccess() = runTest {
        val session = AuthSession(userId = "u1", email = "u1@test.com")
        val repository = FakeAuthRepository(completeGoogleOutcome = GoogleSignInOutcome.Success(session))
        val syncService = FakeSyncService()
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = syncService,
            isSupabaseConfigured = true,
            hasSupabaseWiringIssue = false
        )
        advanceUntilIdle()

        viewModel.onGoogleAuthCallback("nextpage://auth/callback?access_token=t")
        advanceUntilIdle()

        assertEquals(session, viewModel.uiState.value.currentSession)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf("bootstrap:u1", "pull", "push"), syncService.events)
    }

    @Test
    fun init_restoresSession_andTriggersSync_whenSessionExists() = runTest {
        val repository = FakeAuthRepository(
            currentSessionResult = Result.success(AuthSession(userId = "u2", email = "u2@test.com"))
        )
        val syncService = FakeSyncService()

        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = syncService,
            isSupabaseConfigured = true,
            hasSupabaseWiringIssue = false
        )

        advanceUntilIdle()

        assertEquals("u2", viewModel.uiState.value.currentSession?.userId)
        assertEquals(listOf("bootstrap:u2", "pull", "push"), syncService.events)
    }

    @Test
    fun onGoogleAuthCallback_setsWiringFailureKind_onFailure() = runTest {
        val repository = FakeAuthRepository(
            completeGoogleOutcome = GoogleSignInOutcome.Failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "GOOGLE_AUTH_CALLBACK_MISMATCH",
                    message = "Mismatch",
                    component = "Auth"
                )
            )
        )
        val viewModel = AuthViewModel(
            authRepository = repository,
            syncService = FakeSyncService(),
            isSupabaseConfigured = true,
            hasSupabaseWiringIssue = true
        )
        advanceUntilIdle()

        viewModel.onGoogleAuthCallback("nextpage://auth/wrong")
        advanceUntilIdle()

        assertEquals(AuthFailureKind.WIRING_ERROR, viewModel.uiState.value.failureKind)
        assertEquals("Mismatch", viewModel.uiState.value.errorMessage)
    }

    private class FakeAuthRepository(
        private val startGoogleResult: Result<String> = Result.failure(IllegalStateException("not set")),
        private val completeGoogleOutcome: GoogleSignInOutcome = GoogleSignInOutcome.Cancelled,
        private val currentSessionResult: Result<AuthSession?> = Result.success(null),
        private val signOutResult: Result<Unit> = Result.success(Unit)
    ) : AuthRepository {
        override suspend fun startGoogleSignIn(): Result<String> = startGoogleResult

        override suspend fun completeGoogleSignIn(callbackUri: String): GoogleSignInOutcome = completeGoogleOutcome

        override suspend fun signIn(email: String, password: String): Result<AuthSession> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun signUp(email: String, password: String): Result<AuthSession> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun signOut(): Result<Unit> = signOutResult

        override suspend fun getCurrentSession(): Result<AuthSession?> = currentSessionResult
    }

    private class FakeSyncService : SyncService {
        override val syncState: Flow<SyncState> = MutableStateFlow(SyncState.Idle)
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
    }
}
