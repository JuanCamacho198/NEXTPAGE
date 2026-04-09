package com.nextpage.data.remote.sync

import android.util.Log
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.ReadingProgressEntity
import com.nextpage.data.local.entity.SyncOutboxEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.mapper.EntityMappers.toDto
import com.nextpage.data.remote.dto.BookDto
import com.nextpage.data.remote.dto.BookmarkDto
import com.nextpage.data.remote.dto.HighlightDto
import com.nextpage.data.remote.dto.ReadingProgressDto
import io.github.jan-tennert.supabase.SupabaseClient
import io.github.jan-tennert.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

class SyncService(
    private val client: SupabaseClient?,
    private val userId: String,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val outboxDao: com.nextpage.data.local.dao.SyncOutboxDao
) {
    companion object {
        private const val TAG = "SyncService"
        private const val MAX_RETRIES = 3
        private const val PULL_INTERVAL_MS = 30_000L
        private const val PUSH_INTERVAL_MS = 15_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    fun startPeriodicSync() {
        if (client == null) {
            Log.w(TAG, "Sync not started - client is null")
            return
        }

        scope.launch {
            while (true) {
                try {
                    pullRemoteChanges()
                    pushLocalChanges()
                    delay(PULL_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop error", e)
                    delay(PUSH_INTERVAL_MS)
                }
            }
        }
    }

    suspend fun pullRemoteChanges() {
        if (client == null) return

        _syncState.value = SyncState.Syncing
        try {
            pullBooks()
            pullReadingProgress()
            pullHighlights()
            pullBookmarks()
            _syncState.value = SyncState.Synced
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed", e)
            _syncState.value = SyncState.Error(e.message ?: "Pull failed")
        }
    }

    private suspend fun pullBooks() {
        try {
            val response: List<BookDto> = client.postgrest["books"]
                .select {
                    eq("user_id", userId)
                }
                .body()

            response.forEach { dto ->
                val local = bookDao.observeBookById(dto.id)
                local.collect { localBook ->
                    if (localBook == null || localBook.updatedAtEpochMillis < dto.updatedAtEpochMillis) {
                        bookDao.upsert(dto.toEntity())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull books failed", e)
        }
    }

    private suspend fun pullReadingProgress() {
        try {
            val response: List<ReadingProgressDto> = client.postgrest["reading_progress"]
                .select {
                    // Need to filter by book ownership - this requires join with books table
                    // For MVP, we'll pull all and filter in code
                }
                .body()

            response.forEach { dto ->
                readingProgressDao.upsert(dto.toEntity())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull progress failed", e)
        }
    }

    private suspend fun pullHighlights() {
        try {
            val response: List<HighlightDto> = client.postgrest["highlights"]
                .select()
                .body()

            response.forEach { dto ->
                val existing = highlightDao.getHighlightById(dto.id)
                if (existing == null || existing.updatedAtEpochMillis < dto.updatedAtEpochMillis) {
                    if (dto.deletedAtEpochMillis != null) {
                        existing?.let { highlightDao.deleteById(it.id) }
                    } else {
                        highlightDao.upsert(dto.toEntity())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull highlights failed", e)
        }
    }

    private suspend fun pullBookmarks() {
        try {
            val response: List<BookmarkDto> = client.postgrest["bookmarks"]
                .select()
                .body()

            response.forEach { dto ->
                val existing = bookmarkDao.getBookmarkById(dto.id)
                if (existing == null || existing.updatedAtEpochMillis < dto.updatedAtEpochMillis) {
                    if (dto.deletedAtEpochMillis != null) {
                        existing?.let { bookmarkDao.deleteById(it.id) }
                    } else {
                        bookmarkDao.upsert(dto.toEntity())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull bookmarks failed", e)
        }
    }

    suspend fun pushLocalChanges() {
        if (client == null) return

        _syncState.value = SyncState.Syncing
        val items = outboxDao.getPendingItems()
        _pendingCount.value = items.size

        for (item in items) {
            try {
                when (item.entityType) {
                    SyncEntityType.BOOK.name -> pushBook(item)
                    SyncEntityType.READING_PROGRESS.name -> pushProgress(item)
                    SyncEntityType.HIGHLIGHT.name -> pushHighlight(item)
                    SyncEntityType.BOOKMARK.name -> pushBookmark(item)
                }
                outboxDao.deleteById(item.id)
            } catch (e: Exception) {
                Log.e(TAG, "Push failed for ${item.entityId}", e)
                outboxDao.incrementRetryCount(item.id, e.message ?: "Push failed")
            }
        }

        outboxDao.pruneFailedItems(MAX_RETRIES)
        _pendingCount.value = outboxDao.getPendingItems().size
        _syncState.value = SyncState.Synced
    }

    private suspend fun pushBook(item: SyncOutboxEntity) {
        val dto = json.decodeFromString<BookDto>(item.payloadJson)
        when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE, SyncOperation.UPDATE -> {
                client.postgrest["books"].upsert(dto)
            }
            SyncOperation.DELETE -> {
                client.postgrest["books"].delete {
                    eq("id", item.entityId)
                }
            }
        }
    }

    private suspend fun pushProgress(item: SyncOutboxEntity) {
        val dto = json.decodeFromString<ReadingProgressDto>(item.payloadJson)
        when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE, SyncOperation.UPDATE -> {
                client.postgrest["reading_progress"].upsert(dto)
            }
            SyncOperation.DELETE -> {
                client.postgrest["reading_progress"].delete {
                    eq("id", item.entityId)
                }
            }
        }
    }

    private suspend fun pushHighlight(item: SyncOutboxEntity) {
        val dto = json.decodeFromString<HighlightDto>(item.payloadJson)
        when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE, SyncOperation.UPDATE -> {
                client.postgrest["highlights"].upsert(dto)
            }
            SyncOperation.DELETE -> {
                client.postgrest["highlights"].delete {
                    eq("id", item.entityId)
                }
            }
        }
    }

    private suspend fun pushBookmark(item: SyncOutboxEntity) {
        val dto = json.decodeFromString<BookmarkDto>(item.payloadJson)
        when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE, SyncOperation.UPDATE -> {
                client.postgrest["bookmarks"].upsert(dto)
            }
            SyncOperation.DELETE -> {
                client.postgrest["bookmarks"].delete {
                    eq("id", item.entityId)
                }
            }
        }
    }

    suspend fun <T> enqueueSync(entityType: SyncEntityType, entityId: String, operation: SyncOperation, payload: T) {
        val item = SyncOutboxEntity(
            id = UUID.randomUUID().toString(),
            entityType = entityType.name,
            entityId = entityId,
            operation = operation.name,
            payloadJson = json.encodeToString(
                kotlinx.serialization.serializer<Any>() as kotlinx.serialization.KSerializer<T>,
                payload
            ),
            createdAtEpochMillis = System.currentTimeMillis()
        )
        outboxDao.insert(item)
        _pendingCount.value = outboxDao.getPendingItems().size
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data object Synced : SyncState()
    data class Error(val message: String) : SyncState()
}

fun <T> kotlinx.serialization.json.Json.encodeToString(serializer: kotlinx.serialization.KSerializer<T>, value: T): String {
    val encoder = kotlinx.serialization.encoding.Encoder
    return this.encodeToString(serializer, value)
}