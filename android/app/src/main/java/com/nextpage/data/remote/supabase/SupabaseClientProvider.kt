package com.nextpage.data.remote.supabase

import com.nextpage.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Singleton factory that provides the Supabase client for Android.
 *
 * Uses supabase-kt v3 with GoTrue (auth), Postgrest (DB), and Realtime (live changes).
 * The client is session-aware: once the user signs in, all requests carry the
 * auth token via GoTrue (RLS applies automatically).
 *
 * @see [SupabaseDeviceDataSource] for direct DB access using this client.
 */
object SupabaseClientProvider {

    private var _client: SupabaseClient? = null

    /**
     * The session-aware Supabase client. Created lazily on first access.
     * Uses OkHttp engine (Ktor) for WebSocket support (Realtime).
     */
    val client: SupabaseClient
        get() {
            if (_client == null) {
                _client = createClient()
            }
            return _client!!
        }

    private fun createClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY

        require(url.isNotBlank()) { "SUPABASE_URL is not configured" }
        require(anonKey.isNotBlank()) { "SUPABASE_ANON_KEY is not configured" }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            install(GoTrue)
            install(Postgrest)
            install(Realtime)
        }
    }

    /**
     * Reset the client (used after sign-out).
     */
    fun reset() {
        _client = null
    }
}
