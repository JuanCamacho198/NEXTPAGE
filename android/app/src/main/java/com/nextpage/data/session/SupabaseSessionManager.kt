@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.nextpage.data.session

import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.domain.model.AuthSession
import io.github.jan.supabase.auth.auth

/**
 * SessionManager backed by Supabase Auth.
 *
 * The session is managed natively by supabase-kt (persisted in app storage,
 * auto-refreshed). This adapter maps to the existing [SessionManager] interface
 * so consuming code (SyncService, etc.) does not need refactoring.
 *
 * @see SessionManager
 * @see SupabaseClientProvider
 */
class SupabaseSessionManager : SessionManager {

    private val supabase get() = SupabaseClientProvider.client

    override suspend fun restoreSession(): Result<AuthSession?> {
        return getCurrentSession()
    }

    override suspend fun getCurrentSession(): Result<AuthSession?> {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            val authSession = session?.let { s ->
                s.user?.let { user ->
                    AuthSession(
                        userId = user.id,
                        email = user.email,
                        displayName = user.userMetadata?.get("full_name") as? String
                            ?: user.userMetadata?.get("name") as? String,
                        photoUrl = user.userMetadata?.get("avatar_url") as? String
                            ?: user.userMetadata?.get("picture") as? String,
                        providerToken = s.providerToken,
                        provider = user.userMetadata?.get("provider") as? String,
                        createdAt = user.createdAt?.toString()
                    )
                }
            }
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    override suspend fun ensureFreshSession(): Result<AuthSession> {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            if (session == null || session.expiresAt.epochSeconds <= System.currentTimeMillis() / 1000 + 60) {
                supabase.auth.refreshCurrentSession()
                val freshSession = supabase.auth.currentSessionOrNull()
                    ?: return Result.failure(Exception("No session after refresh"))
                val authSession = freshSession.user?.let { user ->
                    AuthSession(
                        userId = user.id,
                        email = user.email,
                        displayName = user.userMetadata?.get("full_name") as? String
                            ?: user.userMetadata?.get("name") as? String,
                        photoUrl = user.userMetadata?.get("avatar_url") as? String
                            ?: user.userMetadata?.get("picture") as? String,
                        providerToken = freshSession.providerToken,
                        provider = user.userMetadata?.get("provider") as? String,
                        createdAt = user.createdAt?.toString()
                    )
                } ?: return Result.failure(Exception("No user in session after refresh"))
                Result.success(authSession)
            } else {
                getCurrentSession().map { it!! }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOutAll(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            SupabaseClientProvider.reset()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setCurrentSession(session: AuthSession?): Result<Unit> {
        // supabase-kt manages session persistence internally.
        // The AuthSession is used by ViewModel/UI, not persisted separately.
        return Result.success(Unit)
    }
}
