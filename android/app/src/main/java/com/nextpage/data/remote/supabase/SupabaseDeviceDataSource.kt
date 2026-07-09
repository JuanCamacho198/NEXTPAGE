package com.nextpage.data.remote.supabase

import com.nextpage.domain.model.Device
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class SupabaseDeviceDataSource(
    private val supabaseUrl: String,
    private val supabaseKey: String
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders() {
        header("apikey", supabaseKey)
        header("Authorization", "Bearer $supabaseKey")
        header("Content-Type", "application/json")
    }

    suspend fun listDevices(userId: String): List<Device> {
        return client.get("$supabaseUrl/rest/v1/devices") {
            authHeaders()
            parameter("user_id", "eq.$userId")
            parameter("order", "last_active.desc")
        }.body()
    }

    suspend fun upsertDevice(device: Device): Device {
        return client.post("$supabaseUrl/rest/v1/devices") {
            authHeaders()
            header("Prefer", "resolution=merge-duplicates")
            setBody(device)
        }.body()
    }

    suspend fun updateHeartbeat(deviceId: String) {
        client.patch("$supabaseUrl/rest/v1/devices") {
            authHeaders()
            parameter("id", "eq.$deviceId")
            setBody(mapOf("last_active" to java.time.Instant.now().toString()))
        }
    }

    suspend fun removeDevice(deviceId: String, userId: String) {
        client.delete("$supabaseUrl/rest/v1/devices") {
            authHeaders()
            parameter("id", "eq.$deviceId")
            parameter("user_id", "eq.$userId")
        }
    }
}
