package com.nextpage.data.remote.supabase

import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.session.SessionManager
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val sessionManager: SessionManager,
    private val dataSource: SupabaseProgressDataSource = SupabaseProgressDataSource(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processJob: Job? = null
    private var realtimeJob: Job? = null

    private val _state = MutableStateFlow(State.Idle)
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

    private suspend fun processOutbox() {
        val session = sessionManager.ensureFreshSession().getOrNull() ?: return

        _state.value = State.Running
        val pendingItems = outboxDao.getPendingItems()
            .filter { it.entityType == SyncEntityType.READING_PROGRESS.name }

        for (item in pendingItems) {
            val bookId = item.entityId ?: continue

            // Read current progress from Room (latest local state)
            val localProgress = readingProgressDao.getProgressForBook(bookId) ?: continue

            val row = ReadingProgressRow(
                userId = session.userId,
                bookId = localProgress.bookId,
                cfiLocation = localProgress.cfiLocation,
                percentage = localProgress.percentage.toDouble(),
                updatedAt = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    java.util.Locale.US
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date(localProgress.updatedAtEpochMillis))
            )

            try {
                dataSource.upsertProgress(row)
                outboxDao.deleteById(item.id)
            } catch (e: Exception) {
                outboxDao.incrementRetryCount(item.id, e.message ?: "Unknown error")
                outboxDao.pruneFailedItems(3)
            }
        }

        _state.value = State.Idle
    }

    /**
     * Subscribe to Realtime changes so remote progress updates are applied
     * to the local Room database.
     */
    fun subscribeToRealtimeChanges() {
        if (realtimeJob?.isActive == true) return

        realtimeJob = scope.launch {
            val session = sessionManager.ensureFreshSession().getOrNull() ?: return@launch

            dataSource.subscribeToUserChanges(session.userId).collect { action ->
                when (action) {
                    is PostgresAction.PostgresInsertAction -> {
                        val row = action.decodeRecord<ReadingProgressRow>()
                        applyRemoteProgress(row)
                    }
                    is PostgresAction.PostgresUpdateAction -> {
                        val row = action.decodeRecord<ReadingProgressRow>()
                        applyRemoteProgress(row)
                    }
                    else -> { /* DELETE handled by ignoring */ }
                }
            }
        }
    }

    private suspend fun applyRemoteProgress(row: ReadingProgressRow) {
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
                    locatorJson = localProgress?.locatorJson
                )
            )
        }
    }

    /**
     * Stop periodic processing and unsubscribe from Realtime.
     */
    fun stop() {
        processJob?.cancel()
        processJob = null
        realtimeJob?.cancel()
        realtimeJob = null
        dataSource.unsubscribe()
        _state.value = State.Idle
    }
}
