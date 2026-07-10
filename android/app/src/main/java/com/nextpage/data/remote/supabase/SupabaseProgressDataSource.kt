package com.nextpage.data.remote.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reading progress CRUD via Supabase PostgREST (using supabase-kt postgrest-kt).
 *
 * Uses the session-authenticated client from [SupabaseClientProvider]
 * so RLS policies apply automatically.
 *
 * Supports Realtime subscriptions for live cross-device progress sync.
 * Call [subscribeToUserChanges] to start listening, [unsubscribe] to stop.
 */
class SupabaseProgressDataSource {

    private val postgrest get() = SupabaseClientProvider.client.postgrest
    private val realtime get() = SupabaseClientProvider.client.realtime

    private var changesChannel: RealtimeChannel? = null

    /**
     * Subscribe to realtime reading_progress changes for a given [userId].
     * Returns a Flow that emits [PostgresAction] for INSERT, UPDATE, DELETE.
     */
    fun subscribeToUserChanges(userId: String): Flow<PostgresAction> {
        unsubscribe()
        changesChannel = realtime.createChannel("reading-progress-changes")
        val flow = changesChannel!!.postgresChangeFlow<ReadingProgressRow>(
            schema = "public",
            table = "reading_progress",
            filter = PostgresAction.PostgresUpdateFilter(
                filter = "user_id=eq.$userId"
            )
        )
        changesChannel!!.subscribe()
        return flow
    }

    /**
     * Unsubscribe from realtime changes.
     */
    fun unsubscribe() {
        changesChannel?.unsubscribe()
        changesChannel = null
    }

    suspend fun upsertProgress(progress: ReadingProgressRow): ReadingProgressRow {
        return postgrest["reading_progress"]
            .upsert(progress) {
                onConflict("user_id, book_id")
            }
            .decodeSingle<ReadingProgressRow>()
    }

    suspend fun getProgress(userId: String, bookId: String): ReadingProgressRow? {
        return postgrest["reading_progress"]
            .select {
                filter {
                    eq("user_id", userId)
                    eq("book_id", bookId)
                }
            }
            .decodeSingleOrNull<ReadingProgressRow>()
    }

    suspend fun listProgress(userId: String): List<ReadingProgressRow> {
        return postgrest["reading_progress"]
            .select {
                filter {
                    eq("user_id", userId)
                }
                order("updated_at", Order.DESCENDING)
            }
            .decodeList<ReadingProgressRow>()
    }

    suspend fun deleteProgress(userId: String, bookId: String) {
        postgrest["reading_progress"]
            .delete {
                filter {
                    eq("user_id", userId)
                    eq("book_id", bookId)
                }
            }
    }

    /**
     * Batch import reading progress rows (used when seeding Supabase from Drive).
     * Each row is upserted individually to honor conflict resolution.
     */
    suspend fun batchImport(rows: List<ReadingProgressRow>) {
        for (row in rows) {
            upsertProgress(row)
        }
    }
}

/**
 * Represents a row in the `reading_progress` Supabase table.
 */
@Serializable
data class ReadingProgressRow(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("cfi_location")
    val cfiLocation: String = "",
    val percentage: Double = 0.0,
    @SerialName("updated_at")
    val updatedAt: String,
)
