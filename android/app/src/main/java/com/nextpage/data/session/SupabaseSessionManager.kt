package com.nextpage.data.session

import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.domain.model.AuthSession

/**
 * SessionManager backed by Supabase GoTrue.
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
            val authSession = session?.let {
                AuthSession(
                    userId = it.user.id,
                    email = it.user.email,
                    displayName = it.user.userMetadata["full_name"] as? String
                        ?: it.user.userMetadata["name"] as? String,
                    photoUrl = it.user.userMetadata["avatar_url"] as? String
                        ?: it.user.userMetadata["picture"] as? String,
                    providerToken = it.providerToken
                )
            }
            Result.success(authSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun ensureFreshSession(): Result<AuthSession> {
        return try {
            val session = supabase.auth.currentSessionOrNull()
            if (session == null || session.expiresAt?.let { it * 1000 <= System.currentTimeMillis() + 60_000 } == true) {
                val refreshed = supabase.auth.refreshCurrentSession()
                val authSession = AuthSession(
                    userId = refreshed.user.id,
                    email = refreshed.user.email,
                    displayName = refreshed.user.userMetadata["full_name"] as? String
                        ?: refreshed.user.userMetadata["name"] as? String,
                    photoUrl = refreshed.user.userMetadata["avatar_url"] as? String
                        ?: refreshed.user.userMetadata["picture"] as? String,
                    providerToken = refreshed.providerToken
                )
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
