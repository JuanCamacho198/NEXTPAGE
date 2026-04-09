package com.nextpage.data.remote.supabase

import com.nextpage.BuildConfig

class SupabaseConfigProvider {
    fun get(): SupabaseConfig {
        return SupabaseConfig(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY
        )
    }
}
