package com.nextpage.data.remote.supabase

data class SupabaseConfig(
    val url: String,
    val anonKey: String
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank() &&
            !url.contains("your-project", ignoreCase = true) &&
            !anonKey.contains("your-anon-key", ignoreCase = true)
}
