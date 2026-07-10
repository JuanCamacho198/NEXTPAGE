package com.nextpage.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.data.session.SessionManager
import com.nextpage.data.session.SupabaseSessionManager
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.user.UserInfo

/**
 * AuthRepository backed by Supabase Auth.
 *
 * Replaces [GoogleAuthRepository] (kept as deprecated for 1 release cycle).
 *
 * Sign-in flow:
 * 1. [startGoogleSignIn] opens Supabase's hosted Google OAuth URL in the browser.
 * 2. Supabase redirects to `nextpage://auth/callback` with PKCE code.
 * 3. [completeGoogleSignIn] exchanges the code for a session.
 * 4. Session is persisted automatically by supabase-kt's Auth plugin.
 *
 * Google Drive access: Use [getProviderToken] from the session to obtain
 * the Google OAuth token needed for Drive API calls.
 */
class SupabaseAuthRepository(
    private val context: Context,
    private val sessionManager: SessionManager = SupabaseSessionManager()
) : AuthRepository {

    private val supabase get() = SupabaseClientProvider.client

    override suspend fun startGoogleSignIn(): Result<String> {
        return try {
            supabase.auth.signInWith(Google, redirectUrl = "nextpage://auth/callback") {
                scopes.addAll(listOf("openid", "email", "profile", "https://www.googleapis.com/auth/drive.appdata"))
            }
            Result.success("nextpage://auth/callback")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeGoogleSignIn(callbackUri: String): Result<AuthSession?> {
        return try {
            supabase.auth.signInWith(Google, redirectUrl = "nextpage://auth/callback") // TODO: parse PKCE code from callbackUri
            val session = supabase.auth.currentSessionOrNull()
            val authSession = session?.let { mapToAuthSession(it.user) }
            sessionManager.setCurrentSession(authSession)
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(): Result<AuthSession> {
        return try {
            supabase.auth.signInWith(Google, redirectUrl = "nextpage://auth/callback") {
                scopes.addAll(listOf("openid", "email", "profile", "https://www.googleapis.com/auth/drive.appdata"))
            }
            Result.failure(UnsupportedOperationException("Google sign-in must be completed via deep-link callback"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthSession> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val authSession = supabase.auth.currentSessionOrNull()?.user?.let { mapToAuthSession(it) }
                ?: return Result.failure(Exception("No user info returned after sign-in"))
            sessionManager.setCurrentSession(authSession)
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUp(email: String, password: String): Result<AuthSession> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val authSession = supabase.auth.currentSessionOrNull()?.user?.let { mapToAuthSession(it) }
                ?: return Result.failure(Exception("No user info returned after sign-up"))
            sessionManager.setCurrentSession(authSession)
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            sessionManager.setCurrentSession(null)
            SupabaseClientProvider.reset()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentSession(): Result<AuthSession?> {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            val authSession = session?.let { mapToAuthSession(it.user) }
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInLocally(): Result<AuthSession> {
        // Sign in anonymously for RLS context
        return try {
            supabase.auth.signInAnonymously()
            val session = supabase.auth.currentSessionOrNull()
            val authSession = AuthSession(
                userId = session?.user?.id ?: "anon-${java.util.UUID.randomUUID()}",
                email = null,
                displayName = null,
                photoUrl = null
            )
            sessionManager.setCurrentSession(authSession)
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the Google provider token from the current session.
     * Used by Google Drive sync operations.
     */
    suspend fun getProviderToken(): Result<String?> {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            Result.success(session?.providerToken)
        } catch (e: Exception) {
            Result.failure(e)
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
                    ?: it.userMetadata?.get("picture") as? String
            )
        }
    }
}
