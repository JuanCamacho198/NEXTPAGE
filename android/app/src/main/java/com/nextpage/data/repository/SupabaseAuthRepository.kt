package com.nextpage.data.repository

import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient

class SupabaseAuthRepository(
    private val client: SupabaseClient?
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): Result<AuthSession> {
        if (client == null) {
            return Result.failure(IllegalStateException(MISSING_CONFIG_ERROR))
        }
        return Result.failure(UnsupportedOperationException(NOT_IMPLEMENTED_YET))
    }

    override suspend fun signUp(email: String, password: String): Result<AuthSession> {
        if (client == null) {
            return Result.failure(IllegalStateException(MISSING_CONFIG_ERROR))
        }
        return Result.failure(UnsupportedOperationException(NOT_IMPLEMENTED_YET))
    }

    override suspend fun signOut(): Result<Unit> {
        if (client == null) {
            return Result.failure(IllegalStateException(MISSING_CONFIG_ERROR))
        }
        return Result.failure(UnsupportedOperationException(NOT_IMPLEMENTED_YET))
    }

    override suspend fun getCurrentSession(): Result<AuthSession?> {
        if (client == null) {
            return Result.success(null)
        }
        return Result.success(null)
    }

    private companion object {
        const val MISSING_CONFIG_ERROR =
            "Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties."
        const val NOT_IMPLEMENTED_YET =
            "Supabase Auth transport wiring will be completed in a dedicated sync/auth execution batch."
    }
}
