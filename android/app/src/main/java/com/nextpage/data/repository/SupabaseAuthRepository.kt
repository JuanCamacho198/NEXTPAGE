@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.nextpage.data.repository

import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.data.session.SessionManager
import com.nextpage.data.session.SupabaseSessionManager
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.user.UserInfo

/**
 * AuthRepository backed by Supabase Auth.
 *
 * Replaces [GoogleAuthRepository] (kept as deprecated for 1 release cycle).
 *
 * Google sign-in flow:
 * 1. UI layer obtains a Google ID token via Credential Manager
 *    ([ActivityResultContracts.GetCredential]).
 * 2. The token is passed to [signInWithGoogleIdToken], which sends it to Supabase
 *    Auth via [IDToken] provider — no browser needed.
 * 3. Session is persisted automatically by supabase-kt's Auth plugin.
 */
class SupabaseAuthRepository(
    private val sessionManager: SessionManager = SupabaseSessionManager()
) : AuthRepository {

    private val supabase get() = SupabaseClientProvider.client

    override suspend fun startGoogleSignIn(): Result<String> {
        return Result.failure(UnsupportedOperationException("Deprecated. Use signInWithGoogleIdToken() instead."))
    }

    override suspend fun completeGoogleSignIn(callbackUri: String): Result<AuthSession?> {
        return Result.failure(UnsupportedOperationException("Deprecated. Use signInWithGoogleIdToken() instead."))
    }

    override suspend fun signInWithGoogle(): Result<AuthSession> {
        return Result.failure(UnsupportedOperationException("Deprecated. Use signInWithGoogleIdToken() instead."))
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthSession> {
        return runCatching {
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
            val session = supabase.auth.currentSessionOrNull()
                ?: throw Exception("No session returned after Google sign-in")
            val authSession = mapToAuthSession(session.user)
                ?: throw Exception("No user info returned after Google sign-in")
            sessionManager.setCurrentSession(authSession)
            authSession
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthSession> {
        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val authSession = supabase.auth.currentSessionOrNull()?.user?.let { mapToAuthSession(it) }
                ?: throw Exception("No user info returned after sign-in")
            sessionManager.setCurrentSession(authSession)
            authSession
        }
    }

    override suspend fun signUp(email: String, password: String): Result<AuthSession> {
        return runCatching {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val authSession = supabase.auth.currentSessionOrNull()?.user?.let { mapToAuthSession(it) }
                ?: throw Exception("No user info returned after sign-up")
            sessionManager.setCurrentSession(authSession)
            authSession
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return runCatching {
            supabase.auth.signOut()
            sessionManager.setCurrentSession(null)
            SupabaseClientProvider.reset()
            Unit
        }
    }

    override suspend fun getCurrentSession(): Result<AuthSession?> {
        return runCatching {
            val session = supabase.auth.currentSessionOrNull()
            session?.let { mapToAuthSession(it.user) }
        }
    }

    override suspend fun signInLocally(): Result<AuthSession> {
        return runCatching {
            supabase.auth.signInAnonymously()
            val session = supabase.auth.currentSessionOrNull()
            AuthSession(
                userId = session?.user?.id ?: "anon-${java.util.UUID.randomUUID()}",
                email = null,
                displayName = null,
                photoUrl = null
            )
            .also { sessionManager.setCurrentSession(it) }
        }
    }

    /**
     * Get the Google provider token from the current session.
     * Used by Google Drive sync operations.
     */
    suspend fun getProviderToken(): Result<String?> {
        return runCatching {
            supabase.auth.currentSessionOrNull()?.providerToken
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun mapToAuthSession(user: UserInfo?): AuthSession? {
        return user?.let {
            AuthSession(
                userId = it.id,
                email = it.email,
                displayName = it.userMetadata?.get("full_name") as? String
                    ?: it.userMetadata?.get("name") as? String,
                photoUrl = it.userMetadata?.get("avatar_url") as? String
                    ?: it.userMetadata?.get("picture") as? String,
                provider = it.userMetadata?.get("provider") as? String,
                createdAt = it.createdAt?.toString()
            )
        }
    }
}
