package com.nextpage.domain.repository

import com.nextpage.domain.model.AuthSession

sealed class GoogleSignInOutcome {
    data class Success(val session: AuthSession) : GoogleSignInOutcome()
    data object Cancelled : GoogleSignInOutcome()
    data class Failure(val error: Throwable) : GoogleSignInOutcome()
}

interface AuthRepository {
    suspend fun startGoogleSignIn(): Result<String>
    suspend fun completeGoogleSignIn(callbackUri: String): GoogleSignInOutcome
    suspend fun signIn(email: String, password: String): Result<AuthSession>
    suspend fun signUp(email: String, password: String): Result<AuthSession>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentSession(): Result<AuthSession?>
}
