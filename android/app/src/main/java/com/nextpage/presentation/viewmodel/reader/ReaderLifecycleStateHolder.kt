package com.nextpage.presentation.viewmodel.reader

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.viewmodel.CfiMigrator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import java.io.File

/**
 * State holder that encapsulates ALL book lifecycle management: loading EPUB/PDF via
 * Readium, chapter/page navigation, progress tracking, and reading time.
 *
 * Owns a [ReaderLifecycleState] exposed as [state] and updated via internal methods.
 */
class ReaderLifecycleStateHolder(
    private val application: Application,
    private val readerRepository: ReaderRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    private val readingStatsRepository: ReadingStatsRepository,
    private val scope: CoroutineScope,
    private val onChapterChanged: () -> Unit,
    private val onErrorEvent: (UiEvent) -> Unit,
    private val onSelectionCleared: () -> Unit = {},
    private val onNavigateToLocator: (Locator) -> Unit = {},
    private val onBookLoaded: (bookId: String) -> Unit = {},
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    private val _state = MutableStateFlow(ReaderLifecycleState())
    val state: StateFlow<ReaderLifecycleState> = _state.asStateFlow()

    private var observeProgressJob: Job? = null
    private var readingTimeTickerJob: Job? = null
    private var sessionStartTime: Long = 0L

    companion object {
        private const val TAG = "ReaderLifecycleStateHolder"
    }

    // ──────────────────────────────────────────────────────────────
    // A.3 — Book Loading
    // ──────────────────────────────────────────────────────────────

    fun loadBook(bookId: String, filePath: String, format: String = "epub") {
        val startTime = System.currentTimeMillis()

        _state.update {
            it.copy(
                selectedBookId = bookId,
                bookFilePath = filePath,
                bookFormat = format,
                isLoading = true,
                error = null,
                chapters = emptyList(),
                currentChapterIndex = 0,
                currentPdfPage = 0,
                totalPdfPages = 0,
                progressPercent = 0f,
                progressLabel = "",
                readiumPublication = null,
                readiumLocator = null
            )
        }

        when (format.lowercase()) {
            "pdf" -> loadPdfBook(bookId, filePath, startTime)
            else -> loadEpubBook(bookId, filePath)
        }
    }

    /**
     * Opens an EPUB via Readium's [PublicationOpener] and stores the
     * resulting [Publication] in [ReaderLifecycleState.readiumPublication].
     *
     * This is the primary EPUB loading path (replaces the old WebView-based
     * [loadEpubBook] after Phase 2).
     */
    fun loadEpubBook(bookId: String, filePath: String) {
        val startTime = System.currentTimeMillis()
        _state.update {
            it.copy(
                selectedBookId = bookId,
                bookFilePath = filePath,
                bookFormat = "epub",
                isLoading = true,
                error = null,
                chapters = emptyList(),
                currentChapterIndex = 0,
                readiumPublication = null,
                readiumLocator = null,
                progressPercent = 0f,
                progressLabel = ""
            )
        }

        scope.launch(mainDispatcher) {
            try {
                val file = File(filePath)
                val fileUri = android.net.Uri.fromFile(file).toString()

                // Step 1: create HttpClient and AssetRetriever
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(application.contentResolver, httpClient)

                // Step 2: retrieve the Asset from the absolute file URL
                val url = AbsoluteUrl(fileUri)
                    ?: throw Exception("Invalid file URI: $fileUri")
                val retrieveResult = withContext(Dispatchers.IO) {
                    assetRetriever.retrieve(url)
                }
                val asset = retrieveResult.getOrNull()
                    ?: throw Exception("Failed to retrieve EPUB asset")

                // Step 3: open a Publication from the Asset
                val parser = DefaultPublicationParser(
                    context = application,
                    httpClient = httpClient,
                    assetRetriever = assetRetriever,
                    pdfFactory = null
                )
                val opener = PublicationOpener(parser)
                val openResult = withContext(Dispatchers.IO) {
                    opener.open(asset, allowUserInteraction = false)
                }
                val publication: Publication = openResult.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        throw Exception("Readium open failed: ${error.message}")
                    }
                )

                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Readium loaded EPUB in ${loadTime}ms")

                val chapters = publication.readingOrder.mapIndexed { index, link ->
                    BookChapter(
                        index = index,
                        id = link.href.toString(),
                        title = link.title ?: "Chapter ${index + 1}",
                        href = link.href.toString()
                    )
                }

                // ── Restore saved position ──────────────────────────────
                val savedProgress = readerRepository.getProgressForBook(bookId)
                val initialLocator: Locator? = savedProgress?.locatorJson
                    ?.let { CfiMigrator.jsonToLocator(it) }

                _state.update {
                    it.copy(
                        readiumPublication = publication,
                        chapters = chapters,
                        readiumLocator = initialLocator,
                        isLoading = false,
                        loadTimeMs = loadTime
                    )
                }
                // Migrate legacy CFI data to Readium Locator format (idempotent)
                withContext(Dispatchers.IO) {
                    migrateCfiDataForBook(bookId, publication.readingOrder)
                }
                updateProgressDisplay()
                onBookLoaded(bookId)
            } catch (e: Exception) {
                Log.e(TAG, "Readium failed to open EPUB", e)
                val message = e.message ?: "Failed to open EPUB with Readium"
                _state.update {
                    it.copy(isLoading = false, error = message)
                }
                onErrorEvent(UiEvent.ShowSnackbar(message))
            }
        }
    }

    /**
     * Opens a PDF via Readium's [PublicationOpener] with [PdfiumDocumentFactory].
     *
     * Follows the same Readium open pattern as [loadEpubBook], using a PDF-specific
     * parser factory so the [Publication] is available for the composable layer
     * ([ReadiumPdfReaderContent]).
     */
    private fun loadPdfBook(bookId: String, filePath: String, startTime: Long) {
        scope.launch(mainDispatcher) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    val message = "File not found. Try importing the book again."
                    _state.update { it.copy(isLoading = false, error = message) }
                    onErrorEvent(UiEvent.ShowSnackbar(message))
                    return@launch
                }

                val fileUri = android.net.Uri.fromFile(file).toString()
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(application.contentResolver, httpClient)
                val url = AbsoluteUrl(fileUri)
                    ?: throw Exception("Invalid file URI: $fileUri")
                val retrieveResult = withContext(Dispatchers.IO) {
                    assetRetriever.retrieve(url)
                }
                val asset = retrieveResult.getOrNull()
                    ?: throw Exception("Failed to retrieve PDF asset")

                val parser = DefaultPublicationParser(
                    context = application,
                    httpClient = httpClient,
                    assetRetriever = assetRetriever,
                    pdfFactory = PdfiumDocumentFactory(application)
                )
                val opener = PublicationOpener(parser)
                val openResult = withContext(Dispatchers.IO) {
                    opener.open(asset, allowUserInteraction = false)
                }
                val publication: Publication = openResult.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        throw Exception("Readium PDF open failed: ${error.message}")
                    }
                )

                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Readium loaded PDF in ${loadTime}ms")

                val chapters = publication.readingOrder.mapIndexed { index, link ->
                    BookChapter(
                        index = index,
                        id = link.href.toString(),
                        title = link.title ?: "Page ${index + 1}",
                        href = link.href.toString()
                    )
                }

                _state.update {
                    it.copy(
                        readiumPublication = publication,
                        chapters = chapters,
                        currentChapterIndex = 0,
                        isLoading = false,
                        loadTimeMs = loadTime
                    )
                }
                updateProgressDisplay()
                onBookLoaded(bookId)
            } catch (e: Exception) {
                Log.e(TAG, "Readium failed to open PDF", e)
                val userMessage = when (e) {
                    is OutOfMemoryError -> "The PDF is too large to display on this device."
                    else -> e.message ?: "Failed to open PDF with Readium"
                }
                _state.update { it.copy(isLoading = false, error = userMessage) }
                onErrorEvent(UiEvent.ShowSnackbar(userMessage))
            }
        }
    }

    fun onPdfDocumentLoaded(pages: Int) {
        val currentPages = _state.value.totalPdfPages
        if (currentPages != pages) {
            _state.update { it.copy(totalPdfPages = pages) }
            updateProgressDisplay()
        }
    }

    /**
     * Migrates legacy CFI data for a book to Readium Locator JSON format.
     *
     * Only migrates records that have a CFI string but no [locatorJson] yet.
     * This is idempotent — already-migrated records are skipped.
     */
    private suspend fun migrateCfiDataForBook(bookId: String, readingOrder: List<Link>) {
        val readingOrderLinks = readingOrder
        if (readingOrderLinks.isEmpty()) return

        // ── Migrate highlights ──────────────────────────────────────────
        val highlights = readerRepository.getHighlightsForBook(bookId)
        for (highlight in highlights) {
            if (highlight.cfiRange.startsWith("epubcfi(") && highlight.locatorJson == null) {
                val locator = CfiMigrator.migrateCfiToLocator(highlight.cfiRange, readingOrderLinks)
                if (locator != null) {
                    val migrated = highlight.copy(locatorJson = CfiMigrator.locatorToJson(locator))
                    readerRepository.upsertHighlight(migrated)
                }
            }
        }

        // ── Migrate bookmarks ───────────────────────────────────────────
        val bookmarks = readerRepository.getBookmarksForBook(bookId)
        for (bookmark in bookmarks) {
            if (bookmark.cfiLocation.startsWith("epubcfi(") && bookmark.locatorJson == null) {
                val locator = CfiMigrator.migrateCfiToLocator(bookmark.cfiLocation, readingOrderLinks)
                if (locator != null) {
                    val migrated = bookmark.copy(locatorJson = CfiMigrator.locatorToJson(locator))
                    readerRepository.upsertBookmark(migrated)
                }
            }
        }

        // ── Migrate reading progress ────────────────────────────────────
        val progress = readerRepository.getProgressForBook(bookId)
        if (progress != null && progress.cfiLocation.startsWith("epubcfi(") && progress.locatorJson == null) {
            val locator = CfiMigrator.migrateCfiToLocator(progress.cfiLocation, readingOrderLinks)
            if (locator != null) {
                val migrated = progress.copy(locatorJson = CfiMigrator.locatorToJson(locator))
                readerRepository.upsertProgress(migrated)
            }
        }

        Log.d(TAG, "CFI migration complete for book $bookId")
    }

    // ──────────────────────────────────────────────────────────────
    // A.4 — Navigation + Progress
    // ──────────────────────────────────────────────────────────────

    /**
     * Called by [ReadiumReaderContent] when the navigator reports a
     * new [Locator].  Uses [Publication.linkWithHref] to find the
     * matching reading-order entry, then updates chapter index and
     * progression percentage.
     *
     * Uses Readium's [Locator.locations.totalProgression] (0.0 to ~1.0)
     * for the real overall document percentage, NOT chapter-based math.
     * Saves the progression to the database via [updateReadingProgressUseCase].
     */
    fun onReadiumLocatorChanged(locator: Locator) {
        val publication = _state.value.readiumPublication ?: return
        val matchingLink = publication.linkWithHref(locator.href) ?: return
        val index = publication.readingOrder.indexOf(matchingLink)
        if (index >= 0) {
            // ── Dismiss floating context menu on page-change ───────
            val previousHref = _state.value.readiumLocator?.href
            if (previousHref != null && previousHref != locator.href) {
                onSelectionCleared()
            }

            val totalProgression = locator.locations.totalProgression?.toFloat() ?: 0f
            val progressPercent = (totalProgression * 100f).coerceIn(0f, 100f)
            _state.update {
                it.copy(
                    currentChapterIndex = index,
                    readiumLocator = locator,
                    progressPercent = progressPercent
                )
            }
            updateProgressDisplay()

            // Persist real progression from Readium locator
            val bookId = _state.value.selectedBookId ?: return
            val locatorJson = CfiMigrator.locatorToJson(locator)
            scope.launch(mainDispatcher) {
                updateReadingProgressUseCase(
                    bookId = bookId,
                    cfiLocation = "readium:${locator.href}",
                    percentage = progressPercent,
                    locatorJson = locatorJson
                )
            }
        }
    }

    /**
     * Called by [ReadiumReaderContent] to report the viewport height so
     * selection rects can be derived from [Locator.locations.progression].
     */
    fun onReadiumViewportChanged(height: Int) {
        _state.update { it.copy(readiumViewportHeight = height) }
    }

    fun onHighlightSelected(highlight: Highlight) {
        val cfi = highlight.cfiRange
        if (cfi.startsWith("pdfpage:")) {
            val page = cfi.removePrefix("pdfpage:").toIntOrNull()
            if (page != null) goToPdfPage(page)
        } else {
            // EPUB path: prefer the persisted Readium Locator (precise CFI/position)
            // over the legacy chapter-only CFI fallback.
            val locatorJson = highlight.locatorJson
            val locator = locatorJson?.let { CfiMigrator.jsonToLocator(it) }
            if (locator != null) {
                scope.launch(mainDispatcher) {
                    onNavigateToLocator(locator)
                }
            } else {
                // Legacy CFI without a stored locator: extract chapter index and
                // navigate to the chapter start.
                val chapterMatch = Regex("/6/(\\d+)").find(cfi)
                val chapterIndex = chapterMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (chapterIndex != null) {
                    goToChapter(chapterIndex - 1)
                }
            }
        }
        _state.update { it.copy(showTocSheet = false) }
    }

    // ── Chapter Navigation ─────────────────────────────────────────

    fun goToNextChapter() {
        val currentIndex = _state.value.currentChapterIndex
        val totalChapters = _state.value.chapters.size

        if (currentIndex < totalChapters - 1) {
            val newIndex = currentIndex + 1
            _state.update { it.copy(currentChapterIndex = newIndex) }
            updateProgressForChapter(newIndex)
            onChapterChanged()
        }
    }

    fun goToPreviousChapter() {
        val currentIndex = _state.value.currentChapterIndex

        if (currentIndex > 0) {
            val newIndex = currentIndex - 1
            _state.update { it.copy(currentChapterIndex = newIndex) }
            updateProgressForChapter(newIndex)
            onChapterChanged()
        }
    }

    fun goToChapter(index: Int) {
        if (index in _state.value.chapters.indices) {
            if (index == _state.value.currentChapterIndex) return
            _state.update { it.copy(currentChapterIndex = index) }
            updateProgressForChapter(index)
            onChapterChanged()
        }
    }

    // ── PDF Page Navigation ────────────────────────────────────────

    fun goToNextPdfPage() {
        val currentPage = _state.value.currentPdfPage
        val totalPages = _state.value.totalPdfPages

        if (currentPage < totalPages - 1) {
            val newPage = currentPage + 1
            _state.update { it.copy(currentPdfPage = newPage) }
            updatePdfProgress(newPage, totalPages)
            updateProgressDisplay()
        }
    }

    fun goToPreviousPdfPage() {
        val currentPage = _state.value.currentPdfPage

        if (currentPage > 0) {
            val newPage = currentPage - 1
            _state.update { it.copy(currentPdfPage = newPage) }
            updatePdfProgress(newPage, _state.value.totalPdfPages)
            updateProgressDisplay()
        }
    }

    fun goToPage(pageNumber: Int) {
        val totalPages = _state.value.totalPdfPages
        if (pageNumber in 1..totalPages) {
            val newPage = pageNumber - 1
            _state.update { it.copy(currentPdfPage = newPage) }
            updatePdfProgress(newPage, totalPages)
            updateProgressDisplay()
        }
    }

    fun goToPdfPage(pageIndex: Int) {
        val totalPages = _state.value.totalPdfPages
        if (pageIndex in 0 until totalPages) {
            _state.update { it.copy(currentPdfPage = pageIndex) }
            updatePdfProgress(pageIndex, totalPages)
            updateProgressDisplay()
        }
    }

    fun onTapZone(isLeftZone: Boolean) {
        // Always dismiss selection overlay before navigating
        onSelectionCleared()

        val format = _state.value.bookFormat
        when (format) {
            "pdf" -> if (isLeftZone) goToPreviousPdfPage() else goToNextPdfPage()
            else -> if (isLeftZone) goToPreviousChapter() else goToNextChapter()
        }
    }

    // ── Progress Persistence ──────────────────────────────────────

    private fun updatePdfProgress(currentPage: Int, totalPages: Int) {
        val bookId = _state.value.selectedBookId ?: return

        if (totalPages > 0) {
            val percentage = (((currentPage + 1).toFloat() / totalPages) * 100f)
                .coerceIn(0f, 100f)
            val cfiLocation = "pdfpage:$currentPage"

            scope.launch(mainDispatcher) {
                updateReadingProgressUseCase(
                    bookId = bookId,
                    cfiLocation = cfiLocation,
                    percentage = percentage
                )
            }
        }
    }

    private fun updateProgressForChapter(chapterIndex: Int) {
        val bookId = _state.value.selectedBookId ?: return
        val totalChapters = _state.value.chapters.size

        if (totalChapters > 0) {
            // Chapter-based percentage: cap at 99% so the last chapter doesn't show 100%
            // until Readium's locator totalProgression confirms it.
            val percentage = (((chapterIndex + 1).toFloat() / totalChapters) * 100f)
                .coerceIn(0f, 99f)
            val cfiLocation = "epubcfi(/6/${chapterIndex + 1})"

            scope.launch(mainDispatcher) {
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
     *
     * Priority:
     * 1. PDF → (currentPage+1)/totalPages
     * 2. EPUB + Readium locator available → totalProgression from locator (most accurate)
     * 3. EPUB fallback → chapter-based (capped at 99%)
     */
    private fun updateProgressDisplay() {
        val currentState = _state.value
        val percent: Float
        val label: String

        if (currentState.totalPdfPages > 0) {
            val current = currentState.currentPdfPage + 1
            val total = currentState.totalPdfPages
            percent = ((current.toFloat() / total) * 100f).coerceIn(0f, 100f)
            label = "$current / $total"
        } else if (currentState.readiumLocator != null) {
            // Use Readium's totalProgression for real overall percentage
            val totalProgression = currentState.readiumLocator.locations.totalProgression?.toFloat() ?: 0f
            percent = (totalProgression * 100f).coerceIn(0f, 100f)
            val current = currentState.currentChapterIndex + 1
            val total = currentState.chapters.size.coerceAtLeast(1)
            label = "$current / $total"
        } else if (currentState.chapters.isNotEmpty()) {
            val current = currentState.currentChapterIndex + 1
            val total = currentState.chapters.size
            percent = ((current.toFloat() / total) * 100f).coerceIn(0f, 99f)
            label = "$current / $total"
        } else {
            percent = 0f
            label = ""
        }

        _state.update {
            it.copy(progressPercent = percent, progressLabel = label)
        }
    }

    fun updateProgress(bookId: String, cfiLocation: String, percentage: Float) {
        scope.launch(mainDispatcher) {
            updateReadingProgressUseCase(
                bookId = bookId,
                cfiLocation = cfiLocation,
                percentage = percentage.coerceIn(0f, 100f)
            )
        }
    }

    fun restoreProgressForBook(bookId: String) {
        observeProgressJob?.cancel()

        _state.update {
            it.copy(
                selectedBookId = bookId,
                isLoading = true
            )
        }

        observeProgressJob = scope.launch(mainDispatcher) {
            readerRepository.observeProgress(bookId).collect { progress ->
                _state.update { state ->
                    var newState = state.copy(
                        readingProgress = progress,
                        isLoading = false
                    )

                    // Restore current position from progress if available
                    if (progress != null) {
                        val cfi = progress.cfiLocation
                        if (cfi.startsWith("pdfpage:")) {
                            val page = cfi.removePrefix("pdfpage:").toIntOrNull()
                            if (page != null) {
                                newState = newState.copy(currentPdfPage = page)
                            }
                        } else if (cfi.startsWith("epubcfi(")) {
                            // EPUB CFI: extract chapter index
                            val chapterMatch = Regex("/6/(\\d+)").find(cfi)
                            val chapterIndex = chapterMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                            if (chapterIndex != null) {
                                newState = newState.copy(currentChapterIndex = chapterIndex - 1)
                            }
                        }
                    }
                    newState
                }
                updateProgressDisplay()
            }
        }
    }

    // ── Progress drag ────────────────────────────────────────────────

    fun onProgressChange(percent: Float) {
        val clamped = percent.coerceIn(0f, 100f)
        _state.update {
            it.copy(progressPercent = clamped)
        }

        if (_state.value.selectedBookId == null) return

        if (_state.value.totalPdfPages > 0) {
            val pageIndex = ((clamped / 100f) * _state.value.totalPdfPages).toInt()
                .coerceIn(0, _state.value.totalPdfPages - 1)
            _state.update { it.copy(currentPdfPage = pageIndex) }
            updatePdfProgress(pageIndex, _state.value.totalPdfPages)
            updateProgressDisplay()
        } else if (_state.value.chapters.isNotEmpty()) {
            val chapterIndex = ((clamped / 100f) * _state.value.chapters.size).toInt()
                .coerceIn(0, _state.value.chapters.size - 1)
            if (chapterIndex != _state.value.currentChapterIndex) {
                goToChapter(chapterIndex)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // A.5 — Reading Time
    // ──────────────────────────────────────────────────────────────

    fun onReaderOpened() {
        if (sessionStartTime > 0L) {
            return
        }
        sessionStartTime = System.currentTimeMillis()
        readingTimeTickerJob?.cancel()
        readingTimeTickerJob = scope.launch(mainDispatcher) {
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

    fun onCleared() {
        onReaderPaused()
    }

    private fun flushReadingTime(minimumMinutes: Long = 0L) {
        val bookId = _state.value.selectedBookId ?: return
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

        scope.launch(mainDispatcher) {
            readingStatsRepository.updateReadingTime(bookId, additionalMinutes)
            Log.d(TAG, "Recorded $additionalMinutes minutes for book $bookId")
        }
        sessionStartTime = now
    }

    // ── Test helpers ────────────────────────────────────────────────

    @VisibleForTesting
    internal fun setChaptersForTest(chapters: List<BookChapter>) {
        _state.update { it.copy(chapters = chapters) }
    }

    @VisibleForTesting
    internal fun setPdfStateForTest(
        selectedBookId: String = "",
        totalPages: Int = 0,
        currentPage: Int = 0
    ) {
        _state.update {
            it.copy(
                selectedBookId = selectedBookId,
                totalPdfPages = totalPages,
                currentPdfPage = currentPage,
                bookFormat = "pdf"
            )
        }
        updateProgressDisplay()
    }

    @VisibleForTesting
    internal fun setEpubStateForTest(
        chapters: List<BookChapter>,
        currentChapterIndex: Int = 0,
        selectedBookId: String = ""
    ) {
        _state.update {
            it.copy(
                chapters = chapters,
                currentChapterIndex = currentChapterIndex,
                bookFormat = "epub",
                totalPdfPages = 0,
                selectedBookId = selectedBookId
            )
        }
        updateProgressDisplay()
    }
}
