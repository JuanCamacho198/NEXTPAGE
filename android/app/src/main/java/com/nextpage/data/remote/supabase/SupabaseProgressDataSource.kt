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
    private var bookmarksChannel: RealtimeChannel? = null
    private var highlightsChannel: RealtimeChannel? = null

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

    // ─── Bookmarks ───────────────────────────────────────────────

    suspend fun upsertBookmark(bookmark: BookmarkRow): BookmarkRow {
        return postgrest["bookmarks"]
            .upsert(bookmark) {
                onConflict("user_id, book_id, cfi_location")
            }
            .decodeSingle<BookmarkRow>()
    }

    suspend fun getBookmark(userId: String, bookId: String, cfiLocation: String): BookmarkRow? {
        return postgrest["bookmarks"]
            .select {
                filter {
                    eq("user_id", userId)
                    eq("book_id", bookId)
                    eq("cfi_location", cfiLocation)
                }
            }
            .decodeSingleOrNull<BookmarkRow>()
    }

    suspend fun listBookmarks(userId: String, bookId: String? = null): List<BookmarkRow> {
        return postgrest["bookmarks"]
            .select {
                filter { eq("user_id", userId) }
                bookId?.let { eq("book_id", it) }
                isNull("deleted_at")
                order("updated_at", Order.DESCENDING)
            }
            .decodeList<BookmarkRow>()
    }

    suspend fun softDeleteBookmark(id: String, userId: String) {
        postgrest["bookmarks"]
            .update(
                mapOf(
                    "deleted_at" to java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        java.util.Locale.US
                    ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date())
                )
            ) {
                filter {
                    eq("id", id)
                    eq("user_id", userId)
                }
            }
    }

    /**
     * Subscribe to realtime bookmark changes for a given [userId].
     */
    fun subscribeToBookmarkChanges(userId: String): Flow<PostgresAction> {
        bookmarksChannel?.unsubscribe()
        bookmarksChannel = realtime.createChannel("bookmark-changes")
        val flow = bookmarksChannel!!.postgresChangeFlow<BookmarkRow>(
            schema = "public",
            table = "bookmarks",
            filter = PostgresAction.PostgresUpdateFilter(
                filter = "user_id=eq.$userId"
            )
        )
        bookmarksChannel!!.subscribe()
        return flow
    }

    // ─── Highlights ─────────────────────────────────────────────

    suspend fun upsertHighlight(highlight: HighlightRow): HighlightRow {
        return postgrest["highlights"]
            .upsert(highlight) {
                onConflict("id")
            }
            .decodeSingle<HighlightRow>()
    }

    suspend fun getHighlight(id: String): HighlightRow? {
        return postgrest["highlights"]
            .select {
                filter { eq("id", id) }
            }
            .decodeSingleOrNull<HighlightRow>()
    }

    suspend fun listHighlights(userId: String, bookId: String? = null): List<HighlightRow> {
        return postgrest["highlights"]
            .select {
                filter { eq("user_id", userId) }
                bookId?.let { eq("book_id", it) }
                isNull("deleted_at")
                order("updated_at", Order.DESCENDING)
            }
            .decodeList<HighlightRow>()
    }

    suspend fun softDeleteHighlight(id: String, userId: String) {
        postgrest["highlights"]
            .update(
                mapOf(
                    "deleted_at" to java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        java.util.Locale.US
                    ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date())
                )
            ) {
                filter {
                    eq("id", id)
                    eq("user_id", userId)
                }
            }
    }

    /**
     * Subscribe to realtime highlight changes for a given [userId].
     */
    fun subscribeToHighlightChanges(userId: String): Flow<PostgresAction> {
        highlightsChannel?.unsubscribe()
        highlightsChannel = realtime.createChannel("highlight-changes")
        val flow = highlightsChannel!!.postgresChangeFlow<HighlightRow>(
            schema = "public",
            table = "highlights",
            filter = PostgresAction.PostgresUpdateFilter(
                filter = "user_id=eq.$userId"
            )
        )
        highlightsChannel!!.subscribe()
        return flow
    }

    // ─── Tags ───────────────────────────────────────────────────

    suspend fun findTagByName(userId: String, name: String): TagRow? {
        return postgrest["tags"]
            .select {
                filter {
                    eq("user_id", userId)
                    eq("name", name)
                }
                limit(1)
            }
            .decodeSingleOrNull<TagRow>()
    }

    suspend fun createTag(tag: TagRow): TagRow {
        return postgrest["tags"]
            .upsert(tag) {
                onConflict("user_id, name")
            }
            .decodeSingle<TagRow>()
    }

    suspend fun findOrCreateTag(userId: String, name: String, color: String? = null): TagRow {
        val existing = findTagByName(userId, name)
        if (existing != null) {
            if (color != null && color != existing.color) {
                postgrest["tags"].update(
                    mapOf("color" to color)
                ) {
                    filter {
                        eq("id", existing.id!!)
                        eq("user_id", userId)
                    }
                }
            }
            return existing
        }
        return createTag(
            TagRow(
                id = java.util.UUID.randomUUID().toString(),
                userId = userId,
                name = name,
                color = color
            )
        )
    }

    suspend fun linkTagToHighlight(highlightId: String, tagId: String) {
        postgrest["highlight_tags"]
            .upsert(
                mapOf(
                    "highlight_id" to highlightId,
                    "tag_id" to tagId
                )
            ) {
                onConflict("highlight_id, tag_id")
            }
    }

    suspend fun listTagsForHighlight(highlightId: String): List<TagRow> {
        return postgrest["highlight_tags"]
            .select {
                filter { eq("highlight_id", highlightId) }
            }
            .decodeList<TagRow>()
    }

    /**
     * Batch import bookmark rows (used when seeding Supabase from Drive).
     */
    suspend fun batchImportBookmarks(rows: List<BookmarkRow>) {
        for (row in rows) {
            upsertBookmark(row)
        }
    }

    /**
     * Batch import highlight rows (used when seeding Supabase from Drive).
     */
    suspend fun batchImportHighlights(rows: List<HighlightRow>) {
        for (row in rows) {
            upsertHighlight(row)
        }
    }

    /**
     * Batch import progress rows (used when seeding Supabase from Drive).
     */
    suspend fun batchImport(rows: List<ReadingProgressRow>) {
        for (row in rows) {
            upsertProgress(row)
        }
    }

    // ─── Channel cleanup ────────────────────────────────────────

    fun unsubscribeAll() {
        unsubscribe()
        bookmarksChannel?.unsubscribe()
        bookmarksChannel = null
        highlightsChannel?.unsubscribe()
        highlightsChannel = null
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

/**
 * Represents a row in the `bookmarks` Supabase table.
 */
@Serializable
data class BookmarkRow(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("cfi_location")
    val cfiLocation: String = "",
    @SerialName("title_snippet")
    val titleSnippet: String? = null,
    @SerialName("locator_json")
    val locatorJson: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)

/**
 * Represents a row in the `highlights` Supabase table.
 */
@Serializable
data class HighlightRow(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("cfi_range")
    val cfiRange: String = "",
    @SerialName("text_content")
    val textContent: String = "",
    val note: String? = null,
    val color: String = "yellow",
    val page: Int? = null,
    val type: String? = null,
    @SerialName("rect_json")
    val rectJson: Map<String, Double>? = null,
    @SerialName("locator_json")
    val locatorJson: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)

/**
 * Represents a row in the `tags` Supabase table.
 */
@Serializable
data class TagRow(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    val name: String,
    val color: String? = null,
)
