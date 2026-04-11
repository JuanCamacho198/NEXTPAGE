package com.nextpage.presentation.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.epub.EpubContentLoader
import com.nextpage.data.pdf.PdfContentLoader
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
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
import java.util.UUID

data class ReaderUiState(
    val selectedBookId: String? = null,
    val bookFilePath: String? = null,
    val bookFormat: String? = null,
    val chapters: List<EpubContentLoader.Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val chapterContent: String = "",
    val currentPdfPage: Int = 0,
    val totalPdfPages: Int = 0,
    val pdfPageBitmap: Bitmap? = null,
    val readingProgress: ReadingProgress? = null,
    val highlights: List<Highlight> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = true,
    val loadTimeMs: Long? = null,
    val error: String? = null
)

class ReaderViewModel(
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    private val epubContentLoader: EpubContentLoader? = null,
    private val pdfContentLoader: PdfContentLoader? = null,
    defaultBookId: String?,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {
    companion object {
        private const val TAG = "ReaderViewModel"
    }

    private val mutableUiState = MutableStateFlow(
        ReaderUiState(selectedBookId = defaultBookId)
    )
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var observeProgressJob: Job? = null
    private var observeHighlightsJob: Job? = null
    private var observeBookmarksJob: Job? = null
    private var readingTimeTickerJob: Job? = null
    private var sessionStartTime: Long = 0L

    init {
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
                error = null
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
                loader.load(java.io.File(filePath))
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

                renderPdfPage(0)
                updatePdfProgress(0, pageCount)
                startObservingHighlights(bookId)
                startObservingBookmarks(bookId)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to load PDF for bookId=$bookId, filePath=$filePath: ${e.message}",
                    e
                )
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load PDF"
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
                    it.copy(error = "PDF content loader is unavailable")
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
            } catch (e: Exception) {
                val selectedBookId = mutableUiState.value.selectedBookId
                Log.e(
                    TAG,
                    "Failed to render PDF page index=$pageIndex for bookId=$selectedBookId: ${e.message}",
                    e
                )
                mutableUiState.update {
                    it.copy(
                        error = e.message ?: "Failed to render PDF page"
                    )
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

            result.onSuccess { content ->
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Chapter ${chapterIndex} loaded in ${loadTime}ms")

                mutableUiState.update {
                    it.copy(
                        currentChapterIndex = chapterIndex,
                        chapterContent = content
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to load chapter: ${error.message}")
                mutableUiState.update {
                    it.copy(error = error.message ?: "Failed to load chapter")
                }
            }
        }
    }

    fun goToNextChapter() {
        val currentIndex = mutableUiState.value.currentChapterIndex
        val totalChapters = mutableUiState.value.chapters.size

        if (currentIndex < totalChapters - 1) {
            val newIndex = currentIndex + 1
            loadChapterContent(newIndex)
            updateProgressForChapter(newIndex)
        }
    }

    fun goToPreviousChapter() {
        val currentIndex = mutableUiState.value.currentChapterIndex

        if (currentIndex > 0) {
            val newIndex = currentIndex - 1
            loadChapterContent(newIndex)
            updateProgressForChapter(newIndex)
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
        color: String = "yellow"
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
            loadChapterContent(index)
            updateProgressForChapter(index)
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
                epubContentLoader = epubContentLoader,
                pdfContentLoader = pdfContentLoader,
                defaultBookId = defaultBookId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
