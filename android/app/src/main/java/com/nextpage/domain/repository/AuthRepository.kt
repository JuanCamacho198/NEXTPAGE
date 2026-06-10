package com.nextpage.domain.repository

import com.nextpage.domain.model.AuthSession

interface AuthRepository {
    suspend fun startGoogleSignIn(): Result<String>
    suspend fun completeGoogleSignIn(callbackUri: String): Result<AuthSession?>
    suspend fun signIn(email: String, password: String): Result<AuthSession>
    suspend fun signUp(email: String, password: String): Result<AuthSession>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentSession(): Result<AuthSession?>
    suspend fun signInLocally(): Result<AuthSession>
}
