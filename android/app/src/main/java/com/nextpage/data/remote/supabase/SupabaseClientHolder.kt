package com.nextpage.data.remote.supabase

import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage

data class SupabaseInitDiagnostic(
    val component: String,
    val status: Status,
    val message: String,
    val error: AppError? = null
) {
    enum class Status {
        SUCCESS,
        CONFIG_ERROR,
        WIRING_ERROR
    }
}

class SupabaseClientProvider(
    val config: SupabaseConfig,
    private val component: String = DEFAULT_COMPONENT,
    private val clientFactory: (SupabaseConfig) -> SupabaseClient? = { validatedConfig ->
        createSupabaseClient(
            supabaseUrl = validatedConfig.url.trim(),
            supabaseKey = validatedConfig.anonKey.trim()
        ) {
            install(Storage)
        }
    }
) {
    val initDiagnostic: SupabaseInitDiagnostic
    val client: SupabaseClient?

    init {
        val validation = config.validate(component)
        if (validation.isFailure) {
            val appError = validation.exceptionOrNull() as? AppError ?: AppError(
                category = ErrorCategory.CONFIG_ERROR,
                code = "SUPABASE_CONFIG_UNKNOWN",
                message = "Supabase configuration failed validation.",
                component = component
            )
            client = null
            initDiagnostic = SupabaseInitDiagnostic(
                component = component,
                status = SupabaseInitDiagnostic.Status.CONFIG_ERROR,
                message = appError.message,
                error = appError
            )
        } else {
            val created = runCatching {
                clientFactory(config)
            }

            if (created.isSuccess && created.getOrNull() != null) {
                client = created.getOrNull()
                initDiagnostic = SupabaseInitDiagnostic(
                    component = component,
                    status = SupabaseInitDiagnostic.Status.SUCCESS,
                    message = "Supabase client initialized successfully."
                )
            } else {
                val cause = created.exceptionOrNull()
                val appError = AppError(
                    category = ErrorCategory.WIRING_ERROR,
                    code = "SUPABASE_CLIENT_BOOTSTRAP_FAILED",
                    message = cause?.message ?: "Supabase client bootstrap failed with null instance.",
                    component = component
                )
                client = null
                initDiagnostic = SupabaseInitDiagnostic(
                    component = component,
                    status = SupabaseInitDiagnostic.Status.WIRING_ERROR,
                    message = appError.message,
                    error = appError
                )
            }
        }
    }

    val isConfigured: Boolean
        get() = initDiagnostic.status == SupabaseInitDiagnostic.Status.SUCCESS

    companion object {
        private const val DEFAULT_COMPONENT = "SupabaseClientProvider"
    }
}
