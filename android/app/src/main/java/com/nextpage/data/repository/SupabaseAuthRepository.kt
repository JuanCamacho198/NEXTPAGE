package com.nextpage.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
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
import java.util.UUID

/**
 * AuthRepository backed by Supabase Auth.
 *
 * Replaces [GoogleAuthRepository] (kept as deprecated for 1 release cycle).
 *
 * Sign-in flow:
 * 1. [signInWithGoogle] gets a Google ID token via Credential Manager (native bottom sheet).
 * 2. The ID token is passed to Supabase Auth via [IDToken] provider — no browser needed.
 * 3. Session is persisted automatically by supabase-kt's Auth plugin.
 *
 * Google Drive access: Use [getProviderToken] from the session to obtain
 * the Google OAuth token needed for Drive API calls.
 */
class SupabaseAuthRepository(
    private val context: Context,
    private val sessionManager: SessionManager = SupabaseSessionManager(),
    private val clientId: String
) : AuthRepository {

    private val supabase get() = SupabaseClientProvider.client
    private val credentialManager = CredentialManager.create(context)

    override suspend fun startGoogleSignIn(): Result<String> {
        return Result.failure(UnsupportedOperationException("Deprecated. Use signInWithGoogle() for native Credential Manager flow."))
    }

    override suspend fun completeGoogleSignIn(callbackUri: String): Result<AuthSession?> {
        return Result.failure(UnsupportedOperationException("Deprecated. Use signInWithGoogle() for native Credential Manager flow."))
    }

    override suspend fun signInWithGoogle(): Result<AuthSession> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setNonce(UUID.randomUUID().hashCode().toString())
                .setFilterByAuthorizedAccounts(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                context = context,
                request = request
            )
            handleGoogleCredential(result)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign in cancelled by user"))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Google credential error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun handleGoogleCredential(result: GetCredentialResponse): Result<AuthSession> {
        return runCatching {
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                throw Exception("Unexpected credential type: ${credential.type}")
            }

            val googleIdTokenCredential = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: GoogleIdTokenParsingException) {
                throw Exception("Failed to parse Google ID token", e)
            }

            // Pass the ID token to Supabase Auth — no browser OAuth needed
            supabase.auth.signInWith(IDToken) {
                idToken = googleIdTokenCredential.idToken
                provider = Google
                nonce = UUID.randomUUID().hashCode().toString()
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
