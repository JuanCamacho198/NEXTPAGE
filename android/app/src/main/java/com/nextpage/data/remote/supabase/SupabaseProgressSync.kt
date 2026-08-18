package com.nextpage.data.remote.supabase

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookmarkEntity
import com.nextpage.data.local.entity.HighlightEntity
import com.nextpage.data.local.entity.ReadingSessionEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.session.SessionManager
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
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
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val readingSessionDao: ReadingSessionDao,
    private val sessionManager: SessionManager,
    private val dataSource: SupabaseProgressDataSource = SupabaseProgressDataSource(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processJob: Job? = null
    private var realtimeJob: Job? = null

    /**
     * Remote progress rows whose book was not yet present locally when the pull
     * arrived (e.g. a book was just downloaded from the cloud). reading_progress
     * has a real FK to books.id, so we cannot upsert without the book; instead we
     * retain the row here and apply it as soon as the book becomes available.
     * This fixes the race where a freshly downloaded book opened before the pull
     * applied would start at 0 and then overwrite the remote progress (LWW).
     */
    private val pendingRemoteProgress = java.util.concurrent.ConcurrentHashMap<String, ReadingProgressRow>()

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
        // A book downloaded from the cloud may not be registered in `books` yet
        // when this pull runs. reading_progress has a real FK to books.id, so we
        // must wait until the book exists before applying the remote progress —
        // otherwise the remote value is dropped and the local 0% wins (LWW),
        // wiping the "continue reading" position. Retry briefly with a small
        // delay instead of giving up on the first attempt.
        val bookReady = bookDao.getBookById(bookId) != null
        var attempts = 0
        while (!bookReady && attempts < PULL_BOOK_READY_MAX_ATTEMPTS) {
            delay(PULL_BOOK_READY_RETRY_DELAY_MS)
            attempts++
        }
        runCatching {
            val state = dataSource.fetchBookState(session.userId, bookId)
            state.progress?.let { row ->
                if (applyRemoteProgress(row)) onProgressApplied(row)
            }
            state.bookmarks.forEach { applyRemoteBookmark(it) }
            state.highlights.forEach { applyRemoteHighlight(it) }
        }
        // Apply any progress retained while the book was not yet available.
        applyPendingProgressForBook(bookId)?.let { onProgressApplied(it) }
    }

    /**
     * Apply a previously-retained remote progress row for [bookId] now that the
     * book exists locally (or has just been pulled). Returns the applied row, or
     * null if there was none pending / the book is still missing.
     */
    internal suspend fun applyPendingProgressForBook(bookId: String): ReadingProgressRow? {
        val row = pendingRemoteProgress.remove(bookId) ?: return null
        if (bookDao.getBookById(bookId) == null) {
            // Book still missing — keep it pending for a later flush.
            pendingRemoteProgress[bookId] = row
            return null
        }
        return if (applyRemoteProgress(row)) row else null
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
                SyncEntityType.READING_SESSION.name -> processSessionItem(item, session.userId)
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
                                dataSource.linkTagToHighlight(
                                    localHighlight.id,
                                    requireNotNull(tag.id) { "Supabase tag missing id after findOrCreateTag" }
                                )
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
     * Subscribe to Realtime changes so remote progress, bookmark, highlight,
     * and reading-session updates are applied to the local Room database.
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

            // Reading-session changes (REQ-reading-sessions-sync-4, SCEN-sync-6/7)
            launch {
                dataSource.subscribeToReadingSessionChanges(session.userId).collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            val row = action.decodeRecord<ReadingSessionRow>()
                            applyRemoteSession(row)
                        }
                        is PostgresAction.Update -> {
                            val row = action.decodeRecord<ReadingSessionRow>()
                            applyRemoteSession(row)
                        }
                        is PostgresAction.Delete, is PostgresAction.Select -> { /* no-op */ }
                    }
                }
            }
        }
    }

    private suspend fun applyRemoteProgress(row: ReadingProgressRow): Boolean {
        // Guard: reading_progress.book_id has a FK to books.id. Remote progress
        // may arrive for a book that is not present locally (e.g. read on the
        // desktop, or the local copy was removed while cloud progress remains).
        // Upserting would throw SQLiteConstraintException and crash the app.
        // Instead of silently dropping the remote value (which would let the local
        // 0% win by LWW and wipe "continue reading"), retain it and apply it once
        // the book becomes available (see applyPendingProgressForBook).
        if (bookDao.getBookById(row.bookId) == null) {
            pendingRemoteProgress[row.bookId] = row
            return false
        }

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

        // Guard: bookmark.book_id has a FK to books.id — never insert a
        // bookmark for a book that is not present locally (see applyRemoteProgress).
        if (!isDeleted && bookDao.getBookById(row.bookId) == null) return

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

        // Guard: highlight.book_id has a FK to books.id — never insert a
        // highlight for a book that is not present locally (see applyRemoteProgress).
        if (!isDeleted && bookDao.getBookById(row.bookId) == null) return

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
     * Push a READING_SESSION outbox item (REQ-reading-sessions-sync-3).
     *
     * The remote row uses the FRESH session [userId] (pre-auth flushes recorded
     * with '' merge into the syncing account) and reuses the deterministic id
     * from the payload, so `onConflict = "id"` keeps the upsert idempotent.
     */
    private suspend fun processSessionItem(
        item: com.nextpage.data.local.entity.SyncOutboxEntity,
        userId: String
    ) {
        val payload = try {
            JSONObject(item.payloadJson)
        } catch (_: Exception) {
            outboxDao.deleteById(item.id)
            return
        }

        val id = payload.optString("id", "")
        val bookId = payload.optString("bookId", item.entityId ?: "")
        val startTimeEpochMillis = payload.optLong("startTimeEpochMillis", 0L)
        val durationMinutes = payload.optInt("durationMinutes", 0)
        val date = payload.optLong("date", 0L)
        val updatedAtEpochMillis = payload.optLong("updatedAtEpochMillis", 0L)

        if (id.isBlank() || bookId.isBlank() || durationMinutes <= 0) {
            outboxDao.deleteById(item.id)
            return
        }

        val row = ReadingSessionRow(
            id = id,
            userId = userId,
            bookId = bookId,
            startedAt = dateFormat.format(Date(startTimeEpochMillis)),
            durationMinutes = durationMinutes,
            date = dateFormat.format(Date(date)),
            device = "android",
            updatedAt = dateFormat.format(Date(updatedAtEpochMillis))
        )

        try {
            dataSource.upsertReadingSession(row)
            outboxDao.deleteById(item.id)
        } catch (e: Exception) {
            outboxDao.incrementRetryCount(item.id, e.message ?: "Unknown error")
            outboxDao.pruneFailedItems(3)
        }
    }

    /**
     * Apply a remote reading session (REQ-reading-sessions-sync-4).
     *
     * FK guard first: reading_sessions.book_id references books.id, so a remote
     * session for a book not present locally is skipped (SCEN-sync-6). Then LWW:
     * the remote `updated_at` beats the local `updatedAtEpochMillis`; on equal or
     * older remote clocks nothing changes. The deterministic id REPLACEs the local
     * row (insert REPLACE by PK — SCEN-sync-7).
     */
    internal suspend fun applyRemoteSession(row: ReadingSessionRow): Boolean {
        if (bookDao.getBookById(row.bookId) == null) return false

        val remoteTime = try {
            dateFormat.parse(row.updatedAt)?.time ?: 0L
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

        val local = readingSessionDao.getById(row.id)
        if (local == null || remoteTime > local.updatedAtEpochMillis) {
            readingSessionDao.insert(
                ReadingSessionEntity(
                    id = row.id,
                    bookId = row.bookId,
                    startTimeEpochMillis = try {
                        dateFormat.parse(row.startedAt)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    },
                    durationMinutes = row.durationMinutes,
                    date = try {
                        dateFormat.parse(row.date)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    },
                    userId = row.userId,
                    updatedAtEpochMillis = remoteTime
                )
            )
            return true
        }
        return false
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

    companion object {
        /** How many times to re-check that a freshly-downloaded book exists locally. */
        internal const val PULL_BOOK_READY_MAX_ATTEMPTS = 5

        /** Delay between book-readiness checks (5 * 400ms = up to ~2s total). */
        internal const val PULL_BOOK_READY_RETRY_DELAY_MS = 400L
    }
}
