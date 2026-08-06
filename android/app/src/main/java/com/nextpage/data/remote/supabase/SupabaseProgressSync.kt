package com.nextpage.data.remote.supabase

import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.session.SessionManager
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Processes the local outbox for READING_PROGRESS entries and upserts them
 * to Supabase via [SupabaseProgressDataSource].
 *
 * Also manages Realtime subscription so incoming remote progress changes
 * are applied to the local Room database.
 *
 * Only acts when a valid Supabase session is active.
 */
class SupabaseProgressSync(
    private val outboxDao: SyncOutboxDao,
    private val readingProgressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val sessionManager: SessionManager,
    private val dataSource: SupabaseProgressDataSource = SupabaseProgressDataSource(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processJob: Job? = null
    private var realtimeJob: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    sealed class State {
        data object Idle : State()
        data object Running : State()
        data class Error(val message: String) : State()
    }

    /**
     * Start periodic outbox processing. Processes all pending READING_PROGRESS
     * outbox entries and upserts them to Supabase.
     */
    fun startProcessing() {
        if (processJob?.isActive == true) return
        _state.value = State.Idle

        processJob = scope.launch {
            processOutbox()
        }
    }

    /** Pulls one book without blocking the reader's local open path. */
    suspend fun resumeForBook(bookId: String, onProgressApplied: (ReadingProgressRow) -> Unit = {}) {
        val session = sessionManager.ensureFreshSession().getOrNull() ?: return
        runCatching {
            val state = dataSource.fetchBookState(session.userId, bookId)
            state.progress?.let { row ->
                if (applyRemoteProgress(row)) onProgressApplied(row)
            }
            state.bookmarks.forEach { applyRemoteBookmark(it) }
            state.highlights.forEach { applyRemoteHighlight(it) }
        }
    }

    private val dateFormat: SimpleDateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private suspend fun processOutbox() {
        val session = sessionManager.ensureFreshSession().getOrNull() ?: return

        _state.value = State.Running
        val pendingItems = outboxDao.getPendingItems()

        for (item in pendingItems) {
            when (item.entityType) {
                SyncEntityType.READING_PROGRESS.name -> processProgressItem(item, session.userId)
                SyncEntityType.BOOKMARK.name -> processBookmarkItem(item, session.userId)
                SyncEntityType.HIGHLIGHT.name -> processHighlightItem(item, session.userId)
            }
        }

        _state.value = State.Idle
    }

    private suspend fun processProgressItem(
        item: com.nextpage.data.local.entity.SyncOutboxEntity,
        userId: String
    ) {
        val bookId = item.entityId ?: return
        val localProgress = readingProgressDao.getProgressForBook(bookId) ?: return

        val row = ReadingProgressRow(
            userId = userId,
            bookId = localProgress.bookId,
            cfiLocation = localProgress.cfiLocation,
            percentage = localProgress.percentage.toDouble(),
            locatorJson = localProgress.locatorJson,
            updatedAt = dateFormat.format(Date(localProgress.updatedAtEpochMillis))
        )

        try {
            dataSource.upsertProgress(row)
            outboxDao.deleteById(item.id)
        } catch (e: Exception) {
            outboxDao.incrementRetryCount(item.id, e.message ?: "Unknown error")
            outboxDao.pruneFailedItems(3)
        }
    }

    private suspend fun processBookmarkItem(
        item: com.nextpage.data.local.entity.SyncOutboxEntity,
        userId: String
    ) {
        val bookmarkId = item.entityId ?: return
        val operation = try {
            SyncOperation.valueOf(item.operation)
        } catch (_: IllegalArgumentException) {
            SyncOperation.UPDATE
        }

        try {
            when (operation) {
                SyncOperation.DELETE -> {
                    dataSource.softDeleteBookmark(bookmarkId, userId)
                }
                else -> {
                    val localBookmark = bookmarkDao.getBookmarkById(bookmarkId) ?: return
                    val row = BookmarkRow(
                        id = localBookmark.id,
                        userId = userId,
                        bookId = localBookmark.bookId,
                        cfiLocation = localBookmark.cfiLocation,
                        titleSnippet = localBookmark.titleOrSnippet.ifEmpty { null },
                        locatorJson = localBookmark.locatorJson,
                        deletedAt = localBookmark.deletedAtEpochMillis?.let {
                            dateFormat.format(Date(it))
                        },
                        updatedAt = dateFormat.format(Date(localBookmark.updatedAtEpochMillis))
                    )
                    dataSource.upsertBookmark(row)
                }
            }
            outboxDao.deleteById(item.id)
        } catch (e: Exception) {
            outboxDao.incrementRetryCount(item.id, e.message ?: "Unknown error")
            outboxDao.pruneFailedItems(3)
        }
    }

    private suspend fun processHighlightItem(
        item: com.nextpage.data.local.entity.SyncOutboxEntity,
        userId: String
    ) {
        val entityId = item.entityId ?: return
        val operation = try {
            SyncOperation.valueOf(item.operation)
        } catch (_: IllegalArgumentException) {
            SyncOperation.UPDATE
        }

        try {
            when (operation) {
                SyncOperation.DELETE -> {
                    // If the payload contains a highlight ID, soft-delete it
                    val highlightId = entityId
                    dataSource.softDeleteHighlight(highlightId, userId)
                }
                else -> {
                    // Use entityId as bookId for highlights — need to look up local
                    val highlightsForBook = highlightDao.getHighlightsForBook(entityId)
                    for (localHighlight in highlightsForBook) {
                        val row = HighlightRow(
                            id = localHighlight.id,
                            userId = userId,
                            bookId = localHighlight.bookId,
                            cfiRange = localHighlight.cfiRange,
                            textContent = localHighlight.textContent,
                            note = localHighlight.note,
                            color = localHighlight.color,
                            page = null,
                            type = localHighlight.type,
                            locatorJson = localHighlight.locatorJson,
                            deletedAt = localHighlight.deletedAtEpochMillis?.let {
                                dateFormat.format(Date(it))
                            },
                            updatedAt = dateFormat.format(Date(localHighlight.updatedAtEpochMillis))
                        )
                        dataSource.upsertHighlight(row)

                        // Sync tag string → Supabase M2M tags
                        if (!localHighlight.tag.isNullOrBlank()) {
                            val tagNames = localHighlight.tag.split(",").map { it.trim() }
                                .filter { it.isNotBlank() }
                            for (tagName in tagNames) {
                                val tag = dataSource.findOrCreateTag(userId, tagName)
                                dataSource.linkTagToHighlight(localHighlight.id, tag.id!!)
                            }
                        }
                    }
                }
            }
            outboxDao.deleteById(item.id)
        } catch (e: Exception) {
            outboxDao.incrementRetryCount(item.id, e.message ?: "Unknown error")
            outboxDao.pruneFailedItems(3)
        }
    }

    /**
     * Subscribe to Realtime changes so remote progress, bookmark, and highlight
     * updates are applied to the local Room database.
     */
    fun subscribeToRealtimeChanges() {
        if (realtimeJob?.isActive == true) return

        realtimeJob = scope.launch {
            val session = sessionManager.ensureFreshSession().getOrNull() ?: return@launch

            // Progress changes
            launch {
                dataSource.subscribeToUserChanges(session.userId).collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            val row = action.decodeRecord<ReadingProgressRow>()
                            applyRemoteProgress(row)
                        }
                        is PostgresAction.Update -> {
                            val row = action.decodeRecord<ReadingProgressRow>()
                            applyRemoteProgress(row)
                        }
                        is PostgresAction.Delete, is PostgresAction.Select -> { /* no-op */ }
                    }
                }
            }

            // Bookmark changes
            launch {
                dataSource.subscribeToBookmarkChanges(session.userId).collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            val row = action.decodeRecord<BookmarkRow>()
                            applyRemoteBookmark(row)
                        }
                        is PostgresAction.Update -> {
                            val row = action.decodeRecord<BookmarkRow>()
                            applyRemoteBookmark(row)
                        }
                        is PostgresAction.Delete, is PostgresAction.Select -> { /* no-op */ }
                    }
                }
            }

            // Highlight changes
            launch {
                dataSource.subscribeToHighlightChanges(session.userId).collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            val row = action.decodeRecord<HighlightRow>()
                            applyRemoteHighlight(row)
                        }
                        is PostgresAction.Update -> {
                            val row = action.decodeRecord<HighlightRow>()
                            applyRemoteHighlight(row)
                        }
                        is PostgresAction.Delete, is PostgresAction.Select -> { /* no-op */ }
                    }
                }
            }
        }
    }

    private suspend fun applyRemoteProgress(row: ReadingProgressRow): Boolean {
        // Only apply if remote is newer than local
        val localProgress = readingProgressDao.getProgressForBook(row.bookId)
        val remoteTime = try {
            java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                java.util.Locale.US
            ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(row.updatedAt)?.time ?: 0L
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

        if (localProgress == null || remoteTime > localProgress.updatedAtEpochMillis) {
            readingProgressDao.upsert(
                com.nextpage.data.local.entity.ReadingProgressEntity(
                    id = row.id ?: java.util.UUID.randomUUID().toString(),
                    bookId = row.bookId,
                    cfiLocation = row.cfiLocation,
                    percentage = row.percentage.toFloat(),
                    currentPage = localProgress?.currentPage,
                    updatedAtEpochMillis = remoteTime,
                    locatorJson = row.locatorJson
                )
            )
            return true
        }
        return false
    }

    private suspend fun applyRemoteBookmark(row: BookmarkRow) {
        val isDeleted = row.deletedAt != null
        val localBookmark = bookmarkDao.getBookmarkById(row.id ?: return)

        if (isDeleted) {
            // Soft-delete tombstone: mark locally
            if (localBookmark != null) {
                bookmarkDao.upsert(
                    localBookmark.copy(
                        deletedAtEpochMillis = try {
                            dateFormat.parse(row.deletedAt)?.time
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    )
                )
            }
        } else {
            val remoteTime = try {
                dateFormat.parse(row.updatedAt)?.time ?: 0L
            } catch (_: Exception) {
                System.currentTimeMillis()
            }

            if (localBookmark == null || remoteTime > localBookmark.updatedAtEpochMillis) {
                bookmarkDao.upsert(
                    BookmarkEntity(
                        id = row.id ?: UUID.randomUUID().toString(),
                        bookId = row.bookId,
                        cfiLocation = row.cfiLocation,
                        titleOrSnippet = row.titleSnippet ?: "",
                        updatedAtEpochMillis = remoteTime,
                        deletedAtEpochMillis = if (isDeleted) remoteTime else null,
                        locatorJson = row.locatorJson
                    )
                )
            }
        }
    }

    private suspend fun applyRemoteHighlight(row: HighlightRow) {
        val isDeleted = row.deletedAt != null
        val localHighlight = highlightDao.getHighlightById(row.id ?: return)

        if (isDeleted) {
            if (localHighlight != null) {
                highlightDao.upsert(
                    localHighlight.copy(
                        deletedAtEpochMillis = try {
                            dateFormat.parse(row.deletedAt)?.time
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    )
                )
            }
        } else {
            val remoteTime = try {
                dateFormat.parse(row.updatedAt)?.time ?: 0L
            } catch (_: Exception) {
                System.currentTimeMillis()
            }

            if (localHighlight == null || remoteTime > localHighlight.updatedAtEpochMillis) {
                highlightDao.upsert(
                    HighlightEntity(
                        id = row.id ?: UUID.randomUUID().toString(),
                        bookId = row.bookId,
                        cfiRange = row.cfiRange,
                        textContent = row.textContent,
                        note = row.note,
                        color = row.color,
                        updatedAtEpochMillis = remoteTime,
                        deletedAtEpochMillis = if (isDeleted) remoteTime else null,
                        locatorJson = row.locatorJson,
                        type = row.type
                    )
                )
            }
        }
    }

    /**
     * Stop periodic processing and unsubscribe from Realtime.
     */
    suspend fun stop() {
        processJob?.cancel()
        processJob = null
        realtimeJob?.cancel()
        realtimeJob = null
        dataSource.unsubscribeAll()
        _state.value = State.Idle
    }
}
