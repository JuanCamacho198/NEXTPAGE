package com.nextpage.domain.repository

import com.nextpage.domain.model.AuthSession

interface AuthRepository {
    /** @deprecated Browser-based OAuth. Use [signInWithGoogleIdToken] instead. */
    suspend fun startGoogleSignIn(): Result<String>
    /** @deprecated Browser-based OAuth. Use [signInWithGoogleIdToken] instead. */
    suspend fun completeGoogleSignIn(callbackUri: String): Result<AuthSession?>
    /**
     * Sign in with Google via browser OAuth.
     * @deprecated Browser OAuth flow is deprecated. Use [signInWithGoogleIdToken] instead.
     */
    suspend fun signInWithGoogle(): Result<AuthSession> {
        return Result.failure(UnsupportedOperationException("Not implemented"))
    }
    /**
     * Sign in with a Google ID token obtained from Credential Manager (native).
     * Callers should obtain the ID token via Credential Manager at the UI layer
     * and pass it here.
     *
     * @param idToken The Google ID token from Credential Manager.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthSession>
    suspend fun signIn(email: String, password: String): Result<AuthSession>
    suspend fun signUp(email: String, password: String): Result<AuthSession>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentSession(): Result<AuthSession?>
    suspend fun signInLocally(): Result<AuthSession>
}
