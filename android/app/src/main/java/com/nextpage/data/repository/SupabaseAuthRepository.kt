package com.nextpage.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.data.session.SessionManager
import com.nextpage.data.session.SupabaseSessionManager
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import io.github.jan.supabase.gotrue.Gotrue
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.user.UserInfo

/**
 * AuthRepository backed by Supabase GoTrue.
 *
 * Replaces [GoogleAuthRepository] (kept as deprecated for 1 release cycle).
 *
 * Sign-in flow:
 * 1. [startGoogleSignIn] opens Supabase's hosted Google OAuth URL in the browser.
 * 2. Supabase redirects to `nextpage://auth/callback` with PKCE code.
 * 3. [completeGoogleSignIn] exchanges the code for a session.
 * 4. Session is persisted automatically by supabase-kt's GoTrue plugin.
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
            val url = supabase.auth.signInWith(Google) {
                redirectTo = "nextpage://auth/callback"
                scopes = listOf("openid", "email", "profile", "https://www.googleapis.com/auth/drive.appdata")
            }
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeGoogleSignIn(callbackUri: String): Result<AuthSession?> {
        return try {
            supabase.auth.signInWith(Google) // TODO: parse PKCE code from callbackUri
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
            val url = supabase.auth.signInWith(Google) {
                redirectTo = "nextpage://auth/callback"
                scopes = listOf("openid", "email", "profile", "https://www.googleapis.com/auth/drive.appdata")
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            Result.failure(UnsupportedOperationException("Google sign-in must be completed via deep-link callback"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthSession> {
        return try {
            val session = supabase.auth.signInWith(email, password)
            val authSession = mapToAuthSession(session.user)
            sessionManager.setCurrentSession(authSession)
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUp(email: String, password: String): Result<AuthSession> {
        return try {
            val session = supabase.auth.signUpWith(email, password)
            val authSession = mapToAuthSession(session.user)
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
            val session = supabase.auth.signInAnonymously()
            val authSession = AuthSession(
                userId = session.user.id,
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

    private fun mapToAuthSession(user: UserInfo): AuthSession {
        return AuthSession(
            userId = user.id,
            email = user.email,
            displayName = user.userMetadata["full_name"] as? String
                ?: user.userMetadata["name"] as? String,
            photoUrl = user.userMetadata["avatar_url"] as? String
                ?: user.userMetadata["picture"] as? String
        )
    }
}
