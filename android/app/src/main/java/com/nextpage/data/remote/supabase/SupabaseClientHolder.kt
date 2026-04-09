package com.nextpage.data.remote.supabase

import android.util.Log
import io.github.jan.supabase.SupabaseClient

class SupabaseClientProvider(
    val config: SupabaseConfig
) {
    companion object {
        private const val TAG = "SupabaseClient"
    }

    val isConfigured: Boolean = config.isConfigured

    val client: SupabaseClient? = null

    init {
        if (!isConfigured) {
            Log.w(TAG, "Supabase is not configured. Running in local-only mode.")
        } else {
            Log.i(TAG, "Supabase config detected. Client wiring stays minimal in this batch.")
        }
    }
}
