package com.nextpage.data.session

import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import io.github.jan.supabase.SupabaseClient

class SupabaseSessionManager(
    private val client: SupabaseClient?,
    private val diagnosticError: AppError?,
    private val sessionStore: SessionStore,
    private val refresher: suspend (AuthSession) -> Result<AuthSession> = { session -> Result.success(session) },
    private val remoteSignOut: suspend () -> Result<Unit> = { Result.success(Unit) },
    private val isClientAvailable: Boolean = client != null
) : SessionManager {

    @Volatile
    private var currentSession: AuthSession? = null

    override suspend fun restoreSession(): Result<AuthSession?> {
        val cached = currentSession
        if (cached != null) {
            return Result.success(cached)
        }

        return runCatching { sessionStore.read() }
            .fold(
                onSuccess = { restored ->
                    currentSession = restored
                    Result.success(restored)
                },
                onFailure = { throwable ->
                    Result.failure(
                        AppError(
                            category = ErrorCategory.WIRING_ERROR,
                            code = "SUPABASE_SESSION_RESTORE_FAILED",
                            message = throwable.message ?: "Failed to restore persisted session.",
                            component = COMPONENT
                        )
                    )
                }
            )
    }

    override suspend fun getCurrentSession(): Result<AuthSession?> {
        val current = currentSession
        return if (current != null) {
            Result.success(current)
        } else {
            restoreSession()
        }
    }

    override suspend fun ensureFreshSession(): Result<AuthSession> {
        if (!isClientAvailable) {
            return Result.failure(missingClientError())
        }

        val session = getCurrentSession().getOrNull()
            ?: return Result.failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "SUPABASE_SESSION_UNAVAILABLE",
                    message = "No active session is available to refresh.",
                    component = COMPONENT
                )
            )

        val refreshed = refresher(session)
        if (refreshed.isSuccess) {
            val freshSession = refreshed.getOrThrow()
            currentSession = freshSession
            runCatching { sessionStore.write(freshSession) }
            return Result.success(freshSession)
        }

        val signOutResult = signOutAll()
        val signOutMessage = signOutResult.exceptionOrNull()?.message
        return Result.failure(
            AppError(
                category = ErrorCategory.WIRING_ERROR,
                code = "SUPABASE_SESSION_REFRESH_FAILED",
                message = buildString {
                    append(
                        refreshed.exceptionOrNull()?.message
                            ?: "Session refresh failed and local session was cleared."
                    )
                    if (!signOutMessage.isNullOrBlank()) {
                        append(" Remote sign-out also failed: ")
                        append(signOutMessage)
                    }
                },
                component = COMPONENT
            )
        )
    }

    override suspend fun signOutAll(): Result<Unit> {
        currentSession = null
        runCatching { sessionStore.clear() }
        val remoteResult = runCatching { remoteSignOut() }
            .getOrElse { throwable -> Result.failure(throwable) }
        return if (remoteResult.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(
                AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "SUPABASE_REMOTE_SIGN_OUT_FAILED",
                    message = remoteResult.exceptionOrNull()?.message
                        ?: "Remote sign-out failed after clearing local session.",
                    component = COMPONENT
                )
            )
        }
    }

    override suspend fun setCurrentSession(session: AuthSession?): Result<Unit> {
        currentSession = session
        return runCatching {
            if (session == null) {
                sessionStore.clear()
            } else {
                sessionStore.write(session)
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { throwable ->
                Result.failure(
                    AppError(
                        category = ErrorCategory.WIRING_ERROR,
                        code = "SUPABASE_SESSION_PERSIST_FAILED",
                        message = throwable.message ?: "Failed to persist local session.",
                        component = COMPONENT
                    )
                )
            }
        )
    }

    private fun missingClientError(): AppError {
        return diagnosticError ?: AppError(
            category = ErrorCategory.WIRING_ERROR,
            code = "SUPABASE_CLIENT_NOT_AVAILABLE",
            message = "Supabase client is not available in session manager.",
            component = COMPONENT
        )
    }

    private companion object {
        const val COMPONENT = "SupabaseSessionManager"
    }
}
