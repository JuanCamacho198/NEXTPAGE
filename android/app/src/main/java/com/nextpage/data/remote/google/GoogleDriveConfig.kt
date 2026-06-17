package com.nextpage.data.remote.google

import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory

data class GoogleDriveConfig(
    val oauthClientId: String
) {
    fun validate(component: String = COMPONENT): Result<Unit> {
        val trimmed = oauthClientId.trim()

        if (trimmed.isBlank()) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "GOOGLE_DRIVE_CONFIG_MISSING_CLIENT_ID",
                    message = "Google OAuth client ID is missing or blank.",
                    component = component
                )
            )
        }

        if (trimmed.contains("your-client-id", ignoreCase = true) || trimmed.contains("YOUR_CLIENT_ID")) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "GOOGLE_DRIVE_CONFIG_PLACEHOLDER_CLIENT_ID",
                    message = "Google OAuth client ID still uses a placeholder value.",
                    component = component
                )
            )
        }

        // Must end with .apps.googleusercontent.com
        if (!trimmed.endsWith(".apps.googleusercontent.com")) {
            return Result.failure(
                AppError(
                    category = ErrorCategory.CONFIG_ERROR,
                    code = "GOOGLE_DRIVE_CONFIG_MALFORMED_CLIENT_ID",
                    message = "Google OAuth client ID appears malformed. Expected format: <id>.apps.googleusercontent.com",
                    component = component
                )
            )
        }

        return Result.success(Unit)
    }

    val isConfigured: Boolean
        get() = validate().isSuccess

    companion object {
        const val COMPONENT = "GoogleDriveConfig"
    }
}
