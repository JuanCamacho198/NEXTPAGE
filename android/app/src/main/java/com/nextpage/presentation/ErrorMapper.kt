package com.nextpage.presentation

import android.content.Context
import com.nextpage.R
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory

@Suppress("unused")

object ErrorMapper {

    fun map(context: Context, error: AppError): String {
        return when (error.category) {
            ErrorCategory.NETWORK -> context.getString(R.string.error_network)
            ErrorCategory.AUTH -> context.getString(R.string.error_auth)
            ErrorCategory.VALIDATION -> context.getString(R.string.error_validation)
            ErrorCategory.NOT_FOUND -> context.getString(R.string.error_not_found)
            ErrorCategory.STORAGE -> context.getString(R.string.error_storage)
            ErrorCategory.CONFIG_ERROR -> context.getString(R.string.error_config)
            ErrorCategory.WIRING_ERROR -> context.getString(R.string.error_wiring)
            ErrorCategory.UNKNOWN -> context.getString(R.string.error_unknown)
        }
    }

    fun mapResource(error: AppError): Int {
        return when (error.category) {
            ErrorCategory.NETWORK -> R.string.error_network
            ErrorCategory.AUTH -> R.string.error_auth
            ErrorCategory.VALIDATION -> R.string.error_validation
            ErrorCategory.NOT_FOUND -> R.string.error_not_found
            ErrorCategory.STORAGE -> R.string.error_storage
            ErrorCategory.CONFIG_ERROR -> R.string.error_config
            ErrorCategory.WIRING_ERROR -> R.string.error_wiring
            ErrorCategory.UNKNOWN -> R.string.error_unknown
        }
    }
}
