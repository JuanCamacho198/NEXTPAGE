package com.nextpage.data.remote.supabase

import android.content.Context
import android.util.Log
import io.github.jan-tennert.supabase.SupabaseClient
import io.github.jan-tennert.supabase.gotrue.GoTrue
import io.github.jan-tennert.supabase.postgrest.Postgrest
import io.github.jan-tennert.supabase.storage.Storage
import kotlinx.serialization.json.Json
import java.util.Properties

class SupabaseClientHolder(context: Context) {
    companion object {
        private const val TAG = "SupabaseClient"
    }

    private val properties = Properties()
    private val localPropsFile = context.filesDir.resolve("local.properties")

    val client: SupabaseClient?
    val isConfigured: Boolean

    init {
        try {
            if (localPropsFile.exists()) {
                localPropsFile.inputStream().use { properties.load(it) }
            }

            val url = properties.getProperty("supabase.url")
            val anonKey = properties.getProperty("supabase.anonkey")

            isConfigured = !url.isNullOrBlank() && !anonKey.isNullOrBlank()

            client = if (isConfigured) {
                Log.d(TAG, "Initializing Supabase client with URL: $url")
                io.github.jan-tennert.supabase.createSupabaseClient(
                    url = url,
                    key = anonKey,
                    modules = listOf(
                        GoTrue,
                        Postgrest,
                        Storage
                    )
                ) {
                    json = Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    }
                }
            } else {
                Log.w(TAG, "Supabase not configured - URL or key missing")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase client", e)
            client = null
            isConfigured = false
        }
    }
}