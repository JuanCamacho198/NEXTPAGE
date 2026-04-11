package com.nextpage.data.repository

import com.nextpage.data.session.SessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.GoogleSignInOutcome
import io.github.jan.supabase.SupabaseClient
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class SupabaseAuthRepository(
    private val client: SupabaseClient?,
    private val sessionManager: SessionManager,
    private val supabaseUrl: String,
    private val redirectScheme: String,
    private val redirectHost: String,
    private val redirectPath: String,
    private val diagnosticError: AppError?,
    private val isClientAvailable: Boolean = client != null
) : AuthRepository {

    @Volatile
    private var pendingOAuthState: String? = null

    override suspend fun startGoogleSignIn(): Result<String> {
        if (!isClientAvailable) {
            return Result.failure(missingClientError())
        }

        val state = UUID.randomUUID().toString()
        pendingOAuthState = state
        val redirectTo = buildRedirectUri()
        val authUrl = buildString {
            append("${baseUrl()}/auth/v1/authorize")
            append("?provider=google")
            append("&redirect_to=${encode(redirectTo)}")
            append("&state=${encode(state)}")
        }
        return Result.success(authUrl)
    }

    override suspend fun completeGoogleSignIn(callbackUri: String): GoogleSignInOutcome {
        val parsed = runCatching { URI(callbackUri) }
            .getOrElse {
                return GoogleSignInOutcome.Failure(
                    AppError(
                        category = ErrorCategory.WIRING_ERROR,
                        code = "GOOGLE_AUTH_CALLBACK_INVALID_URI",
                        message = "OAuth callback URI is invalid.",
                        component = COMPONENT
                    )
                )
            }
        val query = queryParams(parsed.rawQuery.orEmpty())
        if (!isExpectedCallback(parsed)) {
            return GoogleSignInOutcome.Failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "GOOGLE_AUTH_CALLBACK_MISMATCH",
                    message = "OAuth callback URI does not match configured redirect path.",
                    component = COMPONENT
                )
            )
        }

        val error = query["error"]
        if (error != null) {
            return if (error == "access_denied") {
                GoogleSignInOutcome.Cancelled
            } else {
                GoogleSignInOutcome.Failure(
                    AppError(
                        category = ErrorCategory.WIRING_ERROR,
                        code = "GOOGLE_AUTH_CALLBACK_ERROR",
                        message = query["error_description"]
                            ?: "Google OAuth callback failed: $error",
                        component = COMPONENT
                    )
                )
            }
        }

        val returnedState = query["state"]
        val expectedState = pendingOAuthState
        if (expectedState != null && returnedState != null && expectedState != returnedState) {
            return GoogleSignInOutcome.Failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "GOOGLE_AUTH_STATE_MISMATCH",
                    message = "Google OAuth callback state does not match the initiated request.",
                    component = COMPONENT
                )
            )
        }

        val accessToken = query["access_token"]
        if (accessToken.isNullOrBlank()) {
            return GoogleSignInOutcome.Failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "GOOGLE_AUTH_CALLBACK_INCOMPLETE",
                    message = "OAuth callback did not include an access token.",
                    component = COMPONENT
                )
            )
        }

        val userId = query["user_id"] ?: "oauth-user"
        val email = query["email"]
        val session = AuthSession(userId = userId, email = email)
        pendingOAuthState = null
        sessionManager.setCurrentSession(session)
        return GoogleSignInOutcome.Success(session)
    }

    override suspend fun signIn(email: String, password: String): Result<AuthSession> {
        return Result.failure(
            UnsupportedOperationException("Email/password auth is disabled; use Google sign-in.")
        )
    }

    override suspend fun signUp(email: String, password: String): Result<AuthSession> {
        return Result.failure(
            UnsupportedOperationException("Email/password sign-up is disabled; use Google sign-in.")
        )
    }

    override suspend fun signOut(): Result<Unit> {
        pendingOAuthState = null
        return sessionManager.signOutAll()
    }

    override suspend fun getCurrentSession(): Result<AuthSession?> {
        return sessionManager.getCurrentSession()
    }

    private fun isExpectedCallback(uri: URI): Boolean {
        return uri.scheme.equals(redirectScheme, ignoreCase = true) &&
            uri.host.equals(redirectHost, ignoreCase = true) &&
            uri.path.orEmpty() == redirectPath
    }

    private fun buildRedirectUri(): String = "$redirectScheme://$redirectHost$redirectPath"

    private fun baseUrl(): String = supabaseUrl.trim().trimEnd('/')

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun queryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery
            .split('&')
            .mapNotNull { token ->
                val index = token.indexOf('=')
                if (index < 0) return@mapNotNull null
                val key = token.substring(0, index)
                val value = token.substring(index + 1)
                decode(key) to decode(value)
            }
            .toMap()
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun missingClientError(): AppError {
        return diagnosticError ?: AppError(
            category = ErrorCategory.WIRING_ERROR,
            code = "SUPABASE_CLIENT_NOT_AVAILABLE",
            message = "Supabase auth requires a bootstrapped client.",
            component = COMPONENT
        )
    }

    private companion object {
        const val COMPONENT = "SupabaseAuthRepository"
    }
}
