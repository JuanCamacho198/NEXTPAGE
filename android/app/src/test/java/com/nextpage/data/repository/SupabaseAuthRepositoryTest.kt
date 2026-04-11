package com.nextpage.data.repository

import com.nextpage.data.session.SessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.GoogleSignInOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseAuthRepositoryTest {

    @Test
    fun startGoogleSignIn_returnsAuthorizeUrl_whenClientIsAvailable() = runTest {
        val repository = SupabaseAuthRepository(
            client = null,
            sessionManager = FakeSessionManager(),
            supabaseUrl = "https://example.supabase.co",
            redirectScheme = "nextpage",
            redirectHost = "auth",
            redirectPath = "/callback",
            diagnosticError = null,
            isClientAvailable = true
        )

        val result = repository.startGoogleSignIn()

        assertTrue(result.isSuccess)
        val url = result.getOrNull().orEmpty()
        assertTrue(url.startsWith("https://example.supabase.co/auth/v1/authorize?provider=google"))
        assertTrue(url.contains("redirect_to="))
        assertTrue(url.contains("state="))
    }

    @Test
    fun completeGoogleSignIn_returnsCancelled_whenGoogleReturnsAccessDenied() = runTest {
        val sessionManager = FakeSessionManager()
        val repository = SupabaseAuthRepository(
            client = null,
            sessionManager = sessionManager,
            supabaseUrl = "https://example.supabase.co",
            redirectScheme = "nextpage",
            redirectHost = "auth",
            redirectPath = "/callback",
            diagnosticError = null,
            isClientAvailable = true
        )

        val outcome = repository.completeGoogleSignIn(
            "nextpage://auth/callback?error=access_denied&error_description=user_cancelled"
        )

        assertEquals(GoogleSignInOutcome.Cancelled, outcome)
    }

    @Test
    fun completeGoogleSignIn_returnsFailure_whenGoogleReturnsNonCancelError() = runTest {
        val repository = SupabaseAuthRepository(
            client = null,
            sessionManager = FakeSessionManager(),
            supabaseUrl = "https://example.supabase.co",
            redirectScheme = "nextpage",
            redirectHost = "auth",
            redirectPath = "/callback",
            diagnosticError = null,
            isClientAvailable = true
        )

        val outcome = repository.completeGoogleSignIn(
            "nextpage://auth/callback?error=server_error&error_description=oauth_failed"
        )

        assertTrue(outcome is GoogleSignInOutcome.Failure)
        val error = (outcome as GoogleSignInOutcome.Failure).error as AppError
        assertEquals(ErrorCategory.WIRING_ERROR, error.category)
        assertEquals("GOOGLE_AUTH_CALLBACK_ERROR", error.code)
        assertEquals("oauth_failed", error.message)
    }

    @Test
    fun completeGoogleSignIn_returnsSuccess_andStoresSession_whenCallbackHasAccessToken() = runTest {
        val sessionManager = FakeSessionManager()
        val repository = SupabaseAuthRepository(
            client = null,
            sessionManager = sessionManager,
            supabaseUrl = "https://example.supabase.co",
            redirectScheme = "nextpage",
            redirectHost = "auth",
            redirectPath = "/callback",
            diagnosticError = null,
            isClientAvailable = true
        )

        val outcome = repository.completeGoogleSignIn(
            "nextpage://auth/callback?access_token=abc&user_id=user-7&email=user7%40mail.com"
        )

        assertTrue(outcome is GoogleSignInOutcome.Success)
        val session = (outcome as GoogleSignInOutcome.Success).session
        assertEquals("user-7", session.userId)
        assertEquals("user7@mail.com", session.email)
        assertEquals("user-7", sessionManager.latestSession?.userId)
    }

    @Test
    fun completeGoogleSignIn_returnsWiringFailure_whenCallbackPathMismatches() = runTest {
        val repository = SupabaseAuthRepository(
            client = null,
            sessionManager = FakeSessionManager(),
            supabaseUrl = "https://example.supabase.co",
            redirectScheme = "nextpage",
            redirectHost = "auth",
            redirectPath = "/callback",
            diagnosticError = null,
            isClientAvailable = true
        )

        val outcome = repository.completeGoogleSignIn("nextpage://auth/wrong?access_token=t")

        assertTrue(outcome is GoogleSignInOutcome.Failure)
        val error = (outcome as GoogleSignInOutcome.Failure).error as AppError
        assertEquals(ErrorCategory.WIRING_ERROR, error.category)
        assertEquals("GOOGLE_AUTH_CALLBACK_MISMATCH", error.code)
    }

    private class FakeSessionManager : SessionManager {
        var latestSession: AuthSession? = null

        override suspend fun restoreSession(): Result<AuthSession?> = Result.success(null)
        override suspend fun getCurrentSession(): Result<AuthSession?> = Result.success(null)
        override suspend fun ensureFreshSession(): Result<AuthSession> = Result.failure(IllegalStateException())
        override suspend fun signOutAll(): Result<Unit> = Result.success(Unit)
        override suspend fun setCurrentSession(session: AuthSession?): Result<Unit> {
            latestSession = session
            return Result.success(Unit)
        }
    }
}
