package com.nextpage.presentation.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.data.remote.supabase.SupabaseProgressSync
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.domain.model.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SDD android-tooling-hygiene WS2b slice 5: decomposed — takes explicit
 * dependencies instead of the whole [com.nextpage.di.AppContainer] (it was
 * the widest container dependency). Lazy manual singletons stay lazy via
 * providers so cold-start partitions are preserved; slice 6 migrates this
 * to `@HiltViewModel` constructor injection.
 */
class DebugViewModel(
    initTimings: InitTimingsSection,
    private val supabaseProgressSyncProvider: () -> SupabaseProgressSync,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val readingSessionDao: ReadingSessionDao,
    private val readingProgressDao: ReadingProgressDao,
    private val clearAllData: () -> Unit,
    private val syncServiceProvider: () -> SyncService,
) : ViewModel() {

    companion object {
        private const val TAG = "DebugViewModel"
    }

    private val _debugInfo = MutableStateFlow(DebugInfo())
    val debugInfo: StateFlow<DebugInfo> = _debugInfo.asStateFlow()

    init {
        _debugInfo.update { it.copy(initTimings = initTimings) }
    }

    fun updateSessionInfo(
        userId: String,
        email: String?,
        displayName: String?,
        isSupabaseConfigured: Boolean,
        hasWiringIssue: Boolean
    ) {
        val authMode = when {
            userId.startsWith("local-") -> "local"
            isSupabaseConfigured -> "supabase"
            else -> "none"
        }
        val session = SessionSection(
            userId = userId,
            email = email,
            displayName = displayName,
            authMode = authMode,
            isSupabaseConfigured = isSupabaseConfigured,
            hasWiringIssue = hasWiringIssue
        )
        _debugInfo.update { it.copy(session = session) }
    }

    fun updatePdfInfo(
        currentPage: Int,
        totalPages: Int,
        loadTimeMs: Long?,
        filePath: String?
    ) {
        val pdfInfo = if (totalPages > 0) {
            PdfDebugSection(
                currentPage = currentPage,
                totalPages = totalPages,
                loadTimeMs = loadTimeMs,
                filePath = filePath
            )
        } else {
            null
        }
        _debugInfo.update { it.copy(pdfDebug = pdfInfo) }
    }

    fun updateSyncInfo(syncService: SyncService) {
        viewModelScope.launch {
            val state = syncService.syncState.first()
            val pending = syncService.pendingCount.first()
            // Supabase sync state + pending count via provider (AFR-3)
            val supabaseState = supabaseProgressSyncProvider().state.first()
            val supabasePending = supabaseProgressSyncProvider().pendingCount.first()
            val (supaStateStr, gatedReason) = when (supabaseState) {
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Gated -> "Gated" to supabaseState.reason
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Idle -> "Idle" to null
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Running -> "Running" to null
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Error -> "Error" to (supabaseState as com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Error).message
            }
            _debugInfo.update {
                it.copy(
                    syncDebug = SyncDebugSection(
                        state = state.toString().removePrefix("DriveSyncState."),
                        pendingCount = pending
                    ),
                    supabaseSyncDebug = SupabaseSyncDebugSection(
                        state = supaStateStr,
                        gatedReason = gatedReason,
                        pendingCount = supabasePending
                    )
                )
            }
        }
    }

    /**
     * Overload that collects only the Supabase sync state (used when Drive
     * syncService is not available but Supabase state is needed via
     * the progress-sync provider).
     */
    fun updateSupabaseSyncInfo() {
        viewModelScope.launch {
            val supabaseState = supabaseProgressSyncProvider().state.first()
            val supabasePending = supabaseProgressSyncProvider().pendingCount.first()
            val (supaStateStr, gatedReason) = when (supabaseState) {
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Gated -> "Gated" to supabaseState.reason
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Idle -> "Idle" to null
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Running -> "Running" to null
                is com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Error -> "Error" to (supabaseState as com.nextpage.data.remote.supabase.SupabaseProgressSync.State.Error).message
            }
            _debugInfo.update {
                it.copy(
                    supabaseSyncDebug = SupabaseSyncDebugSection(
                        state = supaStateStr,
                        gatedReason = gatedReason,
                        pendingCount = supabasePending
                    )
                )
            }
        }
    }

    fun loadDbCounts() {
        _debugInfo.update { it.copy(isLoadingDbCounts = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val counts = DbCountsSection(
                    books = bookDao.count(),
                    highlights = highlightDao.count(),
                    bookmarks = bookmarkDao.count(),
                    readingSessions = readingSessionDao.count(),
                    readingProgress = readingProgressDao.count()
                )
                _debugInfo.update {
                    it.copy(dbCounts = counts, isLoadingDbCounts = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load DB counts", e)
                _debugInfo.update { it.copy(isLoadingDbCounts = false) }
            }
        }
    }

    fun clearDb() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    clearAllData()
                }
                loadDbCounts()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear DB", e)
            }
        }
    }

    fun forceSyncPush() {
        viewModelScope.launch {
            syncServiceProvider().schedulePush()
        }
    }

    fun forceSyncPull() {
        viewModelScope.launch {
            syncServiceProvider().schedulePull()
        }
    }

    fun copySessionInfo(context: Context, session: AuthSession?) {
        val text = buildString {
            if (session != null) {
                appendLine("userId: ${session.userId}")
                appendLine("email: ${session.email ?: "N/A"}")
                appendLine("displayName: ${session.displayName ?: "N/A"}")
            } else {
                appendLine("No session")
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session Info", text.trim())
        clipboard.setPrimaryClip(clip)
    }

    class Factory(
        private val initTimings: InitTimingsSection,
        private val supabaseProgressSyncProvider: () -> SupabaseProgressSync,
        private val bookDao: BookDao,
        private val highlightDao: HighlightDao,
        private val bookmarkDao: BookmarkDao,
        private val readingSessionDao: ReadingSessionDao,
        private val readingProgressDao: ReadingProgressDao,
        private val clearAllData: () -> Unit,
        private val syncServiceProvider: () -> SyncService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DebugViewModel(
                initTimings = initTimings,
                supabaseProgressSyncProvider = supabaseProgressSyncProvider,
                bookDao = bookDao,
                highlightDao = highlightDao,
                bookmarkDao = bookmarkDao,
                readingSessionDao = readingSessionDao,
                readingProgressDao = readingProgressDao,
                clearAllData = clearAllData,
                syncServiceProvider = syncServiceProvider,
            ) as T
        }
    }
}
