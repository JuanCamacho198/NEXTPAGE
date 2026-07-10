package com.nextpage.data.remote.supabase

import com.nextpage.domain.model.Device
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Devices CRUD via Supabase PostgREST (using supabase-kt postgrest-kt).
 *
 * Uses the session-authenticated client from [SupabaseClientProvider]
 * so RLS policies apply automatically.
 *
 * Replaces the old raw-Ktor implementation (which used the anon key directly).
 *
 * Supports Realtime subscriptions for live device list updates.
 * Call [subscribeToChanges] to start listening, [unsubscribe] to stop.
 */
class SupabaseDeviceDataSource {

    private val postgrest get() = SupabaseClientProvider.client.postgrest
    private val realtime get() = SupabaseClientProvider.client.realtime

    private var changesChannel: RealtimeChannel? = null

    /**
     * Subscribe to realtime device changes for a given [userId].
     * Returns a Flow that emits updated [Device] objects on INSERT, UPDATE, DELETE.
     */
    fun subscribeToChanges(userId: String): Flow<PostgresAction> {
        unsubscribe()
        changesChannel = realtime.createChannel("devices-changes")
        val flow = changesChannel!!.postgresChangeFlow<Device>(
            schema = "public",
            table = "devices",
            filter = PostgresAction.PostgresUpdateFilter(
                filter = "user_id=eq.$userId"
            )
        ) {
            decodeColumn("device_info") { element ->
                SupabaseClientProvider.client.serializer
                    .decodeFromJsonElement<Device>(element)
            }
        }
        changesChannel!!.subscribe()
        return flow
    }

    /**
     * Flow that emits [Device] lists whenever a change is detected.
     * Requires [subscribeToChanges] to have been called first.
     */
    suspend fun observeDevices(userId: String): Flow<List<Device>> {
        val channel = changesChannel ?: return emptyFlow()
        return channel.postgresChangeFlow<JsonElement>(
            schema = "public",
            table = "devices",
            filter = PostgresAction.PostgresUpdateFilter(
                filter = "user_id=eq.$userId"
            )
        ).mapNotNull { action ->
            when (action) {
                is PostgresAction.PostgresUpdateAction -> action.decodeRecord<Device>()
                is PostgresAction.PostgresInsertAction -> action.decodeRecord<Device>()
                is PostgresAction.PostgresDeleteAction -> null // handled by re-fetch
                else -> null
            }
        }
    }

    /**
     * Unsubscribe from realtime changes.
     */
    fun unsubscribe() {
        changesChannel?.unsubscribe()
        changesChannel = null
    }

    suspend fun listDevices(userId: String): List<Device> {
        return postgrest["devices"]
            .select {
                filter {
                    eq("user_id", userId)
                }
                order("last_active", Order.DESCENDING)
            }
            .decodeList<Device>()
    }

    suspend fun upsertDevice(device: Device): Device {
        return postgrest["devices"]
            .upsert(device) {
                onConflict("user_id,hardware_id")
            }
            .decodeSingle<Device>()
    }

    suspend fun updateHeartbeat(deviceId: String) {
        postgrest["devices"]
            .patch(
                value = mapOf("last_active" to Clock.System.now().toString()),
                request = {
                    filter {
                        eq("id", deviceId)
                    }
                }
            )
    }

    suspend fun removeDevice(deviceId: String, userId: String) {
        postgrest["devices"]
            .delete {
                filter {
                    eq("id", deviceId)
                    eq("user_id", userId)
                }
            }
    }
}
