package com.nextpage.presentation.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.di.AppContainer
import com.nextpage.domain.model.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    companion object {
        private const val TAG = "DebugViewModel"
    }

    private val _debugInfo = MutableStateFlow(DebugInfo())
    val debugInfo: StateFlow<DebugInfo> = _debugInfo.asStateFlow()

    init {
        val timings = InitTimingsSection(
            dbInitMs = appContainer.dbInitTimeMs,
            epubImportInitMs = appContainer.epubImportInitTimeMs,
            readerRepoInitMs = appContainer.readerRepoInitTimeMs,
            totalInitMs = appContainer.totalInitTimeMs
        )
        _debugInfo.update { it.copy(initTimings = timings) }
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
            _debugInfo.update {
                it.copy(
                    syncDebug = SyncDebugSection(
                        state = state.toString().removePrefix("SyncState."),
                        pendingCount = pending
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
                    books = appContainer.bookDao.count(),
                    highlights = appContainer.highlightDao.count(),
                    bookmarks = appContainer.bookmarkDao.count(),
                    readingSessions = appContainer.readingSessionDao.count(),
                    readingProgress = appContainer.readingProgressDao.count()
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
                    appContainer.clearAllData()
                }
                loadDbCounts()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear DB", e)
            }
        }
    }

    fun forceSyncPush() {
        viewModelScope.launch {
            appContainer.syncService.schedulePush()
        }
    }

    fun forceSyncPull() {
        viewModelScope.launch {
            appContainer.syncService.schedulePull()
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

    class Factory(private val appContainer: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DebugViewModel(appContainer) as T
        }
    }
}
