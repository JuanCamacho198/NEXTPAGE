package com.nextpage.domain.model

data class AuthSession(
    val userId: String,
    val email: String?,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val providerToken: String? = null
)