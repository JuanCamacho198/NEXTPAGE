package com.nextpage.presentation.viewmodel

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.epub.EpubContentLoader
import com.nextpage.data.pdf.PdfContentLoader
import com.nextpage.data.session.ReaderPreferences
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.SearchResult
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

data class ReaderUiState(
    val selectedBookId: String? = null,
    val bookFilePath: String? = null,
    val bookFormat: String? = null,
    val chapters: List<EpubContentLoader.Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val chapterContent: String = "",
    val chapterHtmlContent: String? = null,
    val currentPdfPage: Int = 0,
    val totalPdfPages: Int = 0,
    val pdfPageBitmap: Bitmap? = null,
    val readingProgress: ReadingProgress? = null,
    val highlights: List<Highlight> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val readerSettings: ReaderSettings = ReaderSettings(),
    // ── Sleep Timer ────────────────────────────────────────────────
    val sleepTimerActive: Boolean = false,
    val sleepTimerRemainingSecs: Int = 0,
    val sleepTimerFinished: Boolean = false,
    val sleepTimerPresetMinutes: Int? = null,
    val sleepTimerEndOfChapterMode: Boolean = false,

    // ── Reading Progress ────────────────────────────────────────────
    val progressPercent: Float = 0f,
    val progressLabel: String = "",

    // ── Search (Gap 3) ──────────────────────────────────────────────
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,

    // ── Text Selection (Gap 4) ──────────────────────────────────────
    val selectedText: String? = null,
    val selectionRect: Rect? = null,
    val showColorPicker: Boolean = false,
    val showContextMenu: Boolean = false,

    // ── Highlights Panel (Gap 5) ────────────────────────────────────
    val showHighlightsSheet: Boolean = false,

    // ── aA Settings (Gap 6) ─────────────────────────────────────────
    val showSplitSettings: Boolean = false,

    // ── Fullscreen (Gap 7) ──────────────────────────────────────────
    val isFullscreen: Boolean = false,

    val isLoading: Boolean = true,
    val loadTimeMs: Long? = null,
    val error: String? = null
)

class ReaderViewModel(
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    private val readerPreferences: ReaderPreferences? = null,
    private val epubContentLoader: EpubContentLoader? = null,
    private val pdfContentLoader: PdfContentLoader? = null,
    defaultBookId: String?,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {
    companion object {
        private const val TAG = "ReaderViewModel"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val mutableUiState = MutableStateFlow(
        ReaderUiState(selectedBookId = defaultBookId)
    )
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var observeProgressJob: Job? = null
    private var observeHighlightsJob: Job? = null
    private var observeBookmarksJob: Job? = null
    private var readingTimeTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var searchJob: Job? = null
    private var sessionStartTime: Long = 0L

    init {
        // Load persisted reading settings
        val savedSettings = readerPreferences?.load() ?: ReaderSettings()
        mutableUiState.update { it.copy(readerSettings = savedSettings) }

        if (!defaultBookId.isNullOrBlank()) {
            restoreProgressForBook(defaultBookId)
        } else {
            mutableUiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadBook(bookId: String, filePath: String, format: String = "epub") {
        val startTime = System.currentTimeMillis()

        mutableUiState.update {
            it.copy(
                selectedBookId = bookId,
                bookFilePath = filePath,
                bookFormat = format,
                isLoading = true,
                error = null,
                // Reset fullscreen on new book load
                isFullscreen = false
            )
        }

        when (format.lowercase()) {
            "pdf" -> loadPdfBook(bookId, filePath, startTime)
            else -> loadEpubBook(bookId, filePath, startTime)
        }
    }

    private fun loadEpubBook(bookId: String, filePath: String, startTime: Long) {
        viewModelScope.launch(mainDispatcher) {
            val loader = epubContentLoader
            if (loader == null) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Reader content loader is unavailable"
                    )
                }
                return@launch
            }

            val result = loader.loadEpub(filePath)

            result.onSuccess { book ->
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Book loaded in ${loadTime}ms, ${book.chapters.size} chapters")

                mutableUiState.update { state ->
                    state.copy(
                        chapters = book.chapters,
                        currentChapterIndex = 0,
                        chapterContent = "",
                        isLoading = false,
                        loadTimeMs = loadTime
                    )
                }
                updateProgressDisplay()

                if (book.chapters.isNotEmpty()) {
                    loadChapterContent(0)
                }

                startObservingHighlights(bookId)
                startObservingBookmarks(bookId)
            }.onFailure { error ->
                Log.e(TAG, "Failed to load book: ${error.message}")
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load book"
                    )
                }
            }
        }
    }

    private fun loadPdfBook(bookId: String, filePath: String, startTime: Long) {
        viewModelScope.launch(mainDispatcher) {
            val loader = pdfContentLoader
            if (loader == null) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = "PDF content loader is unavailable"
                    )
                }
                return@launch
            }

            try {
                val file = java.io.File(filePath)
                if (!file.exists()) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            error = "File not found. Try importing the book again."
                        )
                    }
                    return@launch
                }

                loader.load(file)
                val pageCount = loader.getPageCount()
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "PDF loaded in ${loadTime}ms, $pageCount pages")

                mutableUiState.update { state ->
                    state.copy(
                        currentPdfPage = 0,
                        totalPdfPages = pageCount,
                        isLoading = false,
                        loadTimeMs = loadTime
                    )
                }
                updateProgressDisplay()

                renderPdfPage(0)
                updatePdfProgress(0, pageCount)
                startObservingHighlights(bookId)
                startObservingBookmarks(bookId)
            } catch (e: Throwable) {
                Log.e(
                    TAG,
                    "Failed to load PDF for bookId=$bookId, filePath=$filePath: ${e.message}",
                    e
                )
                val userMessage = when (e) {
                    is OutOfMemoryError -> "The PDF is too large to display on this device."
                    is java.io.FileNotFoundException -> "PDF file not found. Try importing the book again."
                    is java.lang.SecurityException -> "Cannot access the PDF file."
                    else -> e.message ?: "Failed to load PDF"
                }
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = userMessage
                    )
                }
            }
        }
    }

    private fun renderPdfPage(pageIndex: Int) {
        viewModelScope.launch(mainDispatcher) {
            val loader = pdfContentLoader
            if (loader == null) {
                mutableUiState.update {
                    it.copy(
                        currentPdfPage = pageIndex,
                        pdfPageBitmap = null,
                        error = "PDF content loader is unavailable"
                    )
                }
                return@launch
            }

            try {
                val bitmap = loader.getPage(pageIndex, 1080)
                mutableUiState.update {
                    it.copy(
                        currentPdfPage = pageIndex,
                        pdfPageBitmap = bitmap,
                        error = null
                    )
                }
                updateProgressDisplay()
            } catch (e: Throwable) {
                val selectedBookId = mutableUiState.value.selectedBookId
                Log.e(
                    TAG,
                    "Failed to render PDF page index=$pageIndex for bookId=$selectedBookId: ${e.message}",
                    e
                )
                val userMessage = when (e) {
                    is OutOfMemoryError -> "Page too large to display on this device."
                    else -> e.message ?: "Failed to render PDF page"
                }
                mutableUiState.update {
                    it.copy(error = userMessage)
                }
            }
        }
    }

    private fun loadChapterContent(chapterIndex: Int) {
        val filePath = mutableUiState.value.bookFilePath ?: return
        val chapter = mutableUiState.value.chapters.getOrNull(chapterIndex) ?: return

        val startTime = System.currentTimeMillis()

        viewModelScope.launch(mainDispatcher) {
            val loader = epubContentLoader ?: return@launch
            val result = loader.getChapterContent(filePath, chapter.href)

            result.onSuccess { htmlContent ->
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Chapter ${chapterIndex} loaded in ${loadTime}ms")

                mutableUiState.update {
                    it.copy(
                        currentChapterIndex = chapterIndex,
                        chapterHtmlContent = htmlContent
                    )
                }
                updateProgressDisplay()
            }.onFailure { error ->
                Log.e(TAG, "Failed to load chapter: ${error.message}")
                mutableUiState.update {
                    it.copy(error = error.message ?: "Failed to load chapter")
                }
            }
        }
    }

    // ── Search (Gap 3) ──────────────────────────────────────────────

    fun onToggleSearch() {
        mutableUiState.update {
            it.copy(
                isSearchActive = !it.isSearchActive,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
    }

    fun onSearchQuery(query: String) {
        mutableUiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()
        if (query.isBlank()) {
            mutableUiState.update {
                it.copy(searchResults = emptyList(), isSearching = false)
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            mutableUiState.update { it.copy(isSearching = true) }

            val state = mutableUiState.value
            val filePath = state.bookFilePath ?: return@launch

            val results = when (state.bookFormat) {
                "pdf" -> {
                    pdfContentLoader?.searchText(query) ?: emptyList()
                }
                else -> {
                    epubContentLoader?.searchAllChapters(filePath, query) ?: emptyList()
                }
            }

            mutableUiState.update {
                it.copy(searchResults = results, isSearching = false)
            }
        }
    }

    fun onClearSearch() {
        mutableUiState.update {
            it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
        }
        searchJob?.cancel()
    }

    fun onDismissSearch() {
        mutableUiState.update {
            it.copy(
                isSearchActive = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
        searchJob?.cancel()
    }

    fun onSearchResultSelected(result: SearchResult) {
        val state = mutableUiState.value
        if (state.bookFormat == "pdf") {
            goToPdfPage(result.page.toInt())
        } else {
            if (result.chapterIndex != state.currentChapterIndex) {
                goToChapter(result.chapterIndex)
            }
        }
        onDismissSearch()
    }

    // ── Text Selection (Gap 4) ──────────────────────────────────────

    fun onTextSelection(text: String, rect: Rect) {
        mutableUiState.update {
            it.copy(
                selectedText = text,
                selectionRect = rect,
                showColorPicker = true,
                showContextMenu = false
            )
        }
    }

    fun onTextSelectionEvent(text: String, left: Float, top: Float, right: Float, bottom: Float) {
        onTextSelection(text, Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
    }

    fun onSelectHighlightColor(color: String) {
        val state = mutableUiState.value
        val bookId = state.selectedBookId ?: return
        val text = state.selectedText ?: return

        val cfiRange = if (state.bookFormat == "pdf") {
            "pdfpage:${state.currentPdfPage}"
        } else {
            "epubcfi(/6/${state.currentChapterIndex + 1})"
        }

        createHighlight(
            bookId = bookId,
            cfiRange = cfiRange,
            textContent = text,
            color = color
        )

        mutableUiState.update {
            it.copy(
                showColorPicker = false,
                showContextMenu = false,
                selectedText = null,
                selectionRect = null
            )
        }
    }

    fun onCopySelectedText() {
        if (mutableUiState.value.selectedText == null) return
        // Clipboard copy handled in View layer
        mutableUiState.update {
            it.copy(
                showColorPicker = false,
                showContextMenu = false,
                selectedText = null,
                selectionRect = null
            )
        }
    }

    fun onShowContextMenu() {
        mutableUiState.update {
            it.copy(showColorPicker = false, showContextMenu = true)
        }
    }

    fun onDismissContextMenu() {
        mutableUiState.update {
            it.copy(
                showColorPicker = false,
                showContextMenu = false,
                selectedText = null,
                selectionRect = null
            )
        }
    }

    // ── Highlights Panel (Gap 5) ────────────────────────────────────

    fun onToggleHighlightsPanel() {
        mutableUiState.update {
            it.copy(showHighlightsSheet = !it.showHighlightsSheet)
        }
    }

    fun onHighlightSelected(highlight: Highlight) {
        // Navigate to highlight position
        val cfi = highlight.cfiRange
        if (cfi.startsWith("pdfpage:")) {
            val page = cfi.removePrefix("pdfpage:").toIntOrNull()
            if (page != null) goToPdfPage(page)
        } else {
            // EPUB CFI: extract chapter index
            val chapterMatch = Regex("/6/(\\d+)").find(cfi)
            val chapterIndex = chapterMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (chapterIndex != null && chapterIndex - 1 != mutableUiState.value.currentChapterIndex) {
                goToChapter(chapterIndex - 1)
            }
        }
        mutableUiState.update { it.copy(showHighlightsSheet = false) }
    }

    // ── aA Settings (Gap 6) ─────────────────────────────────────────

    fun onToggleSplitSettings() {
        mutableUiState.update {
            it.copy(showSplitSettings = !it.showSplitSettings)
        }
    }

    // ── Fullscreen (Gap 7) ──────────────────────────────────────────

    fun onToggleFullscreen() {
        mutableUiState.update {
            it.copy(isFullscreen = !it.isFullscreen)
        }
    }

    // ── Existing methods (unchanged) ────────────────────────────────

    private fun checkEndOfChapterTrigger() {
        if (mutableUiState.value.sleepTimerEndOfChapterMode) {
            mutableUiState.update {
                it.copy(
                    sleepTimerActive = false,
                    sleepTimerRemainingSecs = 0,
                    sleepTimerFinished = true,
                    sleepTimerPresetMinutes = null,
                    sleepTimerEndOfChapterMode = false
                )
            }
        }
    }

    fun goToNextChapter() {
        val currentIndex = mutableUiState.value.currentChapterIndex
        val totalChapters = mutableUiState.value.chapters.size

        if (currentIndex < totalChapters - 1) {
            val newIndex = currentIndex + 1
            mutableUiState.update { it.copy(currentChapterIndex = newIndex) }
            loadChapterContent(newIndex)
            updateProgressForChapter(newIndex)
            checkEndOfChapterTrigger()
        }
    }

    fun goToPreviousChapter() {
        val currentIndex = mutableUiState.value.currentChapterIndex

        if (currentIndex > 0) {
            val newIndex = currentIndex - 1
            mutableUiState.update { it.copy(currentChapterIndex = newIndex) }
            loadChapterContent(newIndex)
            updateProgressForChapter(newIndex)
            checkEndOfChapterTrigger()
        }
    }

    fun onTapZone(isLeftZone: Boolean) {
        val format = mutableUiState.value.bookFormat
        when (format) {
            "pdf" -> if (isLeftZone) goToPreviousPdfPage() else goToNextPdfPage()
            else -> if (isLeftZone) goToPreviousChapter() else goToNextChapter()
        }
    }

    fun goToNextPdfPage() {
        val currentPage = mutableUiState.value.currentPdfPage
        val totalPages = mutableUiState.value.totalPdfPages

        if (currentPage < totalPages - 1) {
            val newPage = currentPage + 1
            renderPdfPage(newPage)
            updatePdfProgress(newPage, totalPages)
        }
    }

    fun goToPreviousPdfPage() {
        val currentPage = mutableUiState.value.currentPdfPage

        if (currentPage > 0) {
            val newPage = currentPage - 1
            renderPdfPage(newPage)
            updatePdfProgress(newPage, mutableUiState.value.totalPdfPages)
        }
    }

    fun goToPage(pageNumber: Int) {
        val totalPages = mutableUiState.value.totalPdfPages
        if (pageNumber in 1..totalPages) {
            val newPage = pageNumber - 1
            renderPdfPage(newPage)
            updatePdfProgress(newPage, totalPages)
        }
    }

    fun goToPdfPage(pageIndex: Int) {
        val totalPages = mutableUiState.value.totalPdfPages
        if (pageIndex in 0 until totalPages) {
            renderPdfPage(pageIndex)
            updatePdfProgress(pageIndex, totalPages)
        }
    }

    private fun updatePdfProgress(currentPage: Int, totalPages: Int) {
        val bookId = mutableUiState.value.selectedBookId ?: return

        if (totalPages > 0) {
            val percentage = ((currentPage + 1).toFloat() / totalPages) * 100
            val cfiLocation = "pdfpage:$currentPage"

            viewModelScope.launch(mainDispatcher) {
                updateReadingProgressUseCase(
                    bookId = bookId,
                    cfiLocation = cfiLocation,
                    percentage = percentage
                )
            }
        }
    }

    private fun updateProgressForChapter(chapterIndex: Int) {
        val bookId = mutableUiState.value.selectedBookId ?: return
        val totalChapters = mutableUiState.value.chapters.size

        if (totalChapters > 0) {
            val percentage = ((chapterIndex + 1).toFloat() / totalChapters) * 100
            val cfiLocation = "epubcfi(/6/${chapterIndex + 1})"

            viewModelScope.launch(mainDispatcher) {
                updateReadingProgressUseCase(
                    bookId = bookId,
                    cfiLocation = cfiLocation,
                    percentage = percentage
                )
            }
        }
    }

    /**
     * Updates the progress percentage and label according to the current format.
     */
    private fun updateProgressDisplay() {
        val state = mutableUiState.value
        val percent: Float
        val label: String

        if (state.totalPdfPages > 0) {
            val current = state.currentPdfPage + 1
            val total = state.totalPdfPages
            percent = (current.toFloat() / total) * 100f
            label = "$current / $total"
        } else if (state.chapters.isNotEmpty()) {
            val current = state.currentChapterIndex + 1
            val total = state.chapters.size
            percent = (current.toFloat() / total) * 100f
            label = "$current / $total"
        } else {
            percent = 0f
            label = ""
        }

        mutableUiState.update {
            it.copy(progressPercent = percent, progressLabel = label)
        }
    }

    fun restoreProgressForBook(bookId: String) {
        observeProgressJob?.cancel()

        mutableUiState.update {
            it.copy(
                selectedBookId = bookId,
                isLoading = true
            )
        }

        observeProgressJob = viewModelScope.launch(mainDispatcher) {
            readerRepository.observeProgress(bookId).collect { progress ->
                mutableUiState.update {
                    it.copy(
                        readingProgress = progress,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateProgress(bookId: String, cfiLocation: String, percentage: Float) {
        viewModelScope.launch(mainDispatcher) {
            updateReadingProgressUseCase(
                bookId = bookId,
                cfiLocation = cfiLocation,
                percentage = percentage.coerceIn(0f, 100f)
            )
        }
    }

    fun createHighlight(
        bookId: String,
        cfiRange: String,
        textContent: String,
        note: String? = null,
        color: String = HighlightColor.YELLOW.hex
    ) {
        viewModelScope.launch(mainDispatcher) {
            val highlight = Highlight(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                cfiRange = cfiRange,
                textContent = textContent,
                note = note,
                color = color,
                updatedAtEpochMillis = System.currentTimeMillis(),
                deletedAtEpochMillis = null
            )
            readerRepository.upsertHighlight(highlight)
            Log.d(TAG, "Highlight created: ${highlight.id}")
        }
    }

    fun createBookmark(bookId: String, cfiLocation: String, titleOrSnippet: String) {
        viewModelScope.launch(mainDispatcher) {
            val bookmark = Bookmark(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                cfiLocation = cfiLocation,
                titleOrSnippet = titleOrSnippet,
                updatedAtEpochMillis = System.currentTimeMillis(),
                deletedAtEpochMillis = null
            )
            readerRepository.upsertBookmark(bookmark)
            Log.d(TAG, "Bookmark created: ${bookmark.id}")
        }
    }

    fun createBookmarkFromCurrentPosition() {
        val bookId = mutableUiState.value.selectedBookId ?: return
        val format = mutableUiState.value.bookFormat

        when (format) {
            "pdf" -> {
                val currentPage = mutableUiState.value.currentPdfPage
                val cfiLocation = "pdfpage:$currentPage"
                val titleOrSnippet = "Page ${currentPage + 1}"
                createBookmark(bookId, cfiLocation, titleOrSnippet)
            }
            else -> {
                val chapter = mutableUiState.value.chapters.getOrNull(mutableUiState.value.currentChapterIndex)
                    ?: return
                val cfiLocation = "epubcfi(/6/${mutableUiState.value.currentChapterIndex + 1})"
                val titleOrSnippet = "Chapter ${mutableUiState.value.currentChapterIndex + 1}: ${chapter.title}"
                createBookmark(bookId, cfiLocation, titleOrSnippet)
            }
        }
    }

    private fun startObservingHighlights(bookId: String) {
        observeHighlightsJob?.cancel()
        observeHighlightsJob = viewModelScope.launch(mainDispatcher) {
            readerRepository.observeHighlights(bookId).collect { highlights ->
                mutableUiState.update {
                    it.copy(highlights = highlights)
                }
            }
        }
    }

    private fun startObservingBookmarks(bookId: String) {
        observeBookmarksJob?.cancel()
        observeBookmarksJob = viewModelScope.launch(mainDispatcher) {
            readerRepository.observeBookmarks(bookId).collect { bookmarks ->
                mutableUiState.update {
                    it.copy(bookmarks = bookmarks)
                }
            }
        }
    }

    fun goToChapter(index: Int) {
        if (index in mutableUiState.value.chapters.indices) {
            if (index == mutableUiState.value.currentChapterIndex) return
            mutableUiState.update { it.copy(currentChapterIndex = index) }
            loadChapterContent(index)
            updateProgressForChapter(index)
            checkEndOfChapterTrigger()
        }
    }

    // ── Sleep Timer ──────────────────────────────────────────────────

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val isEndOfChapter = minutes == Int.MIN_VALUE

        mutableUiState.update {
            it.copy(
                sleepTimerActive = true,
                sleepTimerRemainingSecs = if (isEndOfChapter) 0 else minutes * 60,
                sleepTimerFinished = false,
                sleepTimerPresetMinutes = if (isEndOfChapter) null else minutes,
                sleepTimerEndOfChapterMode = isEndOfChapter
            )
        }

        if (isEndOfChapter) {
            return
        }

        sleepTimerJob = viewModelScope.launch(mainDispatcher) {
            while (isActive && mutableUiState.value.sleepTimerRemainingSecs > 0) {
                delay(1000L)
                val remaining = mutableUiState.value.sleepTimerRemainingSecs - 1
                if (remaining <= 0) {
                    mutableUiState.update {
                        it.copy(
                            sleepTimerActive = false,
                            sleepTimerRemainingSecs = 0,
                            sleepTimerFinished = true,
                            sleepTimerPresetMinutes = null,
                            sleepTimerEndOfChapterMode = false
                        )
                    }
                } else {
                    mutableUiState.update {
                        it.copy(sleepTimerRemainingSecs = remaining)
                    }
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        mutableUiState.update {
            it.copy(
                sleepTimerActive = false,
                sleepTimerRemainingSecs = 0,
                sleepTimerFinished = false,
                sleepTimerPresetMinutes = null,
                sleepTimerEndOfChapterMode = false
            )
        }
    }

    fun dismissSleepTimerOverlay() {
        mutableUiState.update {
            it.copy(sleepTimerFinished = false)
        }
    }

    fun formatSleepTimerRemaining(secs: Int): String {
        val minutes = secs / 60
        val seconds = secs % 60
        return "%d:%02d".format(minutes, seconds)
    }

    // ── Reader Settings ──────────────────────────────────────────────

    fun updateReaderSettings(settings: ReaderSettings) {
        readerPreferences?.save(settings)
        mutableUiState.update { it.copy(readerSettings = settings) }
    }

    // ── Progress drag ────────────────────────────────────────────────

    fun onProgressChange(percent: Float) {
        val clamped = percent.coerceIn(0f, 100f)
        mutableUiState.update {
            it.copy(progressPercent = clamped)
        }

        if (mutableUiState.value.selectedBookId == null) return

        if (mutableUiState.value.totalPdfPages > 0) {
            val pageIndex = ((clamped / 100f) * mutableUiState.value.totalPdfPages).toInt()
                .coerceIn(0, mutableUiState.value.totalPdfPages - 1)
            renderPdfPage(pageIndex)
            updatePdfProgress(pageIndex, mutableUiState.value.totalPdfPages)
        } else if (mutableUiState.value.chapters.isNotEmpty()) {
            val chapterIndex = ((clamped / 100f) * mutableUiState.value.chapters.size).toInt()
                .coerceIn(0, mutableUiState.value.chapters.size - 1)
            if (chapterIndex != mutableUiState.value.currentChapterIndex) {
                goToChapter(chapterIndex)
            }
        }
    }

    fun onReaderOpened() {
        if (sessionStartTime > 0L) {
            return
        }
        sessionStartTime = System.currentTimeMillis()
        readingTimeTickerJob?.cancel()
        readingTimeTickerJob = viewModelScope.launch(mainDispatcher) {
            while (isActive) {
                delay(60_000L)
                flushReadingTime(minimumMinutes = 1L)
            }
        }
    }

    fun onReaderPaused() {
        readingTimeTickerJob?.cancel()
        readingTimeTickerJob = null
        flushReadingTime(minimumMinutes = 1L)
    }

    fun onReaderBackgrounded() {
        onReaderPaused()
    }

    override fun onCleared() {
        onReaderPaused()
        super.onCleared()
    }

    private fun flushReadingTime(minimumMinutes: Long = 0L) {
        val bookId = mutableUiState.value.selectedBookId ?: return
        if (sessionStartTime <= 0L) {
            return
        }

        val now = System.currentTimeMillis()
        val elapsedMs = now - sessionStartTime
        val computedMinutes = elapsedMs / 60000L
        val additionalMinutes = if (minimumMinutes > 0L) {
            computedMinutes.coerceAtLeast(minimumMinutes)
        } else {
            computedMinutes
        }

        if (additionalMinutes <= 0L) {
            return
        }

        viewModelScope.launch(mainDispatcher) {
            readingStatsRepository.updateReadingTime(bookId, additionalMinutes)
            Log.d(TAG, "Recorded $additionalMinutes minutes for book $bookId")
        }
        sessionStartTime = now
    }
}

class ReaderViewModelFactory(
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val readerPreferences: ReaderPreferences,
    private val epubContentLoader: EpubContentLoader,
    private val pdfContentLoader: PdfContentLoader,
    private val defaultBookId: String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            return ReaderViewModel(
                readerRepository = readerRepository,
                readingStatsRepository = readingStatsRepository,
                updateReadingProgressUseCase = UpdateReadingProgressUseCase(readerRepository),
                readerPreferences = readerPreferences,
                epubContentLoader = epubContentLoader,
                pdfContentLoader = pdfContentLoader,
                defaultBookId = defaultBookId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
