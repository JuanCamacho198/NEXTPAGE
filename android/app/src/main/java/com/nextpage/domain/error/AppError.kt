package com.nextpage.domain.error

enum class ErrorCategory {
    CONFIG_ERROR,
    WIRING_ERROR
}

data class AppError(
    val category: ErrorCategory,
    val code: String,
    override val message: String,
    val component: String
) : RuntimeException(message)
