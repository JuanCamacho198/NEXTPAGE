package com.nextpage.data.remote.supabase

import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory

data class SupabaseConfig(
    val url: String,
    val anonKey: String
) {
    fun validate(component: String = COMPONENT): Result<Unit> {
        val trimmedUrl = url.trim()
        val trimmedAnonKey = anonKey.trim()

        if (trimmedUrl.isBlank()) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "SUPABASE_CONFIG_MISSING_URL",
                    message = "Supabase URL is missing or blank.",
                    component = component
                )
            )
        }

        if (trimmedAnonKey.isBlank()) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "SUPABASE_CONFIG_MISSING_ANON_KEY",
                    message = "Supabase anon key is missing or blank.",
                    component = component
                )
            )
        }

        if (trimmedUrl.contains("your-project", ignoreCase = true)) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "SUPABASE_CONFIG_PLACEHOLDER_URL",
                    message = "Supabase URL still uses a placeholder value.",
                    component = component
                )
            )
        }

        if (trimmedAnonKey.contains("your-anon-key", ignoreCase = true)) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "SUPABASE_CONFIG_PLACEHOLDER_ANON_KEY",
                    message = "Supabase anon key still uses a placeholder value.",
                    component = component
                )
            )
        }

        if (!isLikelyJwt(trimmedAnonKey)) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "SUPABASE_CONFIG_MALFORMED_ANON_KEY",
                    message = "Supabase anon key appears malformed. Expected JWT-like format.",
                    component = component
                )
            )
        }

        return Result.success(Unit)
    }

    val isConfigured: Boolean
        get() = validate().isSuccess

    private fun isLikelyJwt(value: String): Boolean {
        val segments = value.split('.')
        return segments.size == 3 && segments.none { it.isBlank() }
    }

    private companion object {
        const val COMPONENT = "SupabaseConfig"
    }
}
