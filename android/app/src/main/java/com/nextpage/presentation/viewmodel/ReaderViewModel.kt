package com.nextpage.presentation.viewmodel

import android.app.Application
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.viewmodel.reader.SleepTimerManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.util.UUID

/**
 * Represents a single chapter in the reader's chapter list.
 * Replaces [com.nextpage.data.epub.EpubContentLoader.Chapter] after Phase 2 cleanup.
 */
data class BookChapter(
    val index: Int,
    val id: String,
    val title: String,
    val href: String
)

data class ReaderUiState(
    val selectedBookId: String? = null,
    val bookFilePath: String? = null,
    val bookFormat: String? = null,
    val chapters: List<BookChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val previewText: String = "",
    val currentPdfPage: Int = 0,
    val totalPdfPages: Int = 0,
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

    // ── Readium (Phase 1+) ─────────────────────────────────────────
    val readiumPublication: Publication? = null,
    val readiumLocator: Locator? = null,
    val readiumSelectionLocator: Locator? = null,
    val readiumViewportHeight: Int = 0,

    // ── Debug ──────────────────────────────────────────────────────
    val debugForceMenu: Boolean = false,

    val isLoading: Boolean = true,
    val loadTimeMs: Long? = null,
    val error: String? = null
)

class ReaderViewModel(
    application: Application,
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    private val readerPreferences: ReaderPreferences? = null,
    private val pdfContentLoader: PdfContentLoader? = null,
    defaultBookId: String?,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ReaderViewModel"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val mutableUiState = MutableStateFlow(
        ReaderUiState(selectedBookId = defaultBookId)
    )
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    val sleepTimerManager = SleepTimerManager(viewModelScope)

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val _navigateToLocator = MutableSharedFlow<Locator>()
    val navigateToLocator: SharedFlow<Locator> = _navigateToLocator.asSharedFlow()

    private var observeProgressJob: Job? = null
    private var observeHighlightsJob: Job? = null
    private var observeBookmarksJob: Job? = null
    private var readingTimeTickerJob: Job? = null
    private var searchJob: Job? = null
    private var sessionStartTime: Long = 0L

    init {
        // Load persisted reading settings
        val savedSettings = readerPreferences?.load() ?: ReaderSettings()
        mutableUiState.update { it.copy(readerSettings = savedSettings) }

        // Merge sleep timer state into ReaderUiState
        viewModelScope.launch(mainDispatcher) {
            sleepTimerManager.state.collect { timerState ->
                mutableUiState.update { current ->
                    current.copy(
                        sleepTimerActive = timerState.isActive,
                        sleepTimerRemainingSecs = timerState.remainingSecs,
                        sleepTimerFinished = timerState.isFinished,
                        sleepTimerPresetMinutes = timerState.presetMinutes,
                        sleepTimerEndOfChapterMode = timerState.isEndOfChapter
                    )
                }
            }
        }

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
                // Reset all book-specific state
                chapters = emptyList(),
                currentChapterIndex = 0,
                currentPdfPage = 0,
                totalPdfPages = 0,
                progressPercent = 0f,
                progressLabel = "",
                highlights = emptyList(),
                bookmarks = emptyList(),
                isFullscreen = false,
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
     * resulting [Publication] in [ReaderUiState.readiumPublication].
     *
     * This is the primary EPUB loading path (replaces the old WebView-based
     * [loadEpubBook] after Phase 2).
     */
    fun loadEpubBook(bookId: String, filePath: String) {
        val startTime = System.currentTimeMillis()
        mutableUiState.update {
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
                progressLabel = "",
                highlights = emptyList(),
                bookmarks = emptyList(),
                isFullscreen = false
            )
        }

        viewModelScope.launch(mainDispatcher) {
            try {
                val app = getApplication<Application>()
                val file = File(filePath)
                val fileUri = android.net.Uri.fromFile(file).toString()

                // Step 1: create HttpClient and AssetRetriever
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(app.contentResolver, httpClient)

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
                    context = app,
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

                mutableUiState.update {
                    it.copy(
                        readiumPublication = publication,
                        chapters = chapters,
                        isLoading = false,
                        loadTimeMs = loadTime
                    )
                }
                // Migrate legacy CFI data to Readium Locator format (idempotent)
                withContext(Dispatchers.IO) {
                    migrateCfiDataForBook(bookId, publication.readingOrder)
                }
                updateProgressDisplay()
                startObservingHighlights(bookId)
                startObservingBookmarks(bookId)
            } catch (e: Exception) {
                Log.e(TAG, "Readium failed to open EPUB", e)
                val message = e.message ?: "Failed to open EPUB with Readium"
                mutableUiState.update {
                    it.copy(isLoading = false, error = message)
                }
                _uiEvent.tryEmit(UiEvent.ShowSnackbar(message))
            }
        }
    }

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
        val publication = mutableUiState.value.readiumPublication ?: return
        val matchingLink = publication.linkWithHref(locator.href) ?: return
        val index = publication.readingOrder.indexOf(matchingLink)
        if (index >= 0) {
            val totalProgression = locator.locations.totalProgression?.toFloat() ?: 0f
            val progressPercent = (totalProgression * 100f).coerceIn(0f, 100f)
            mutableUiState.update {
                it.copy(
                    currentChapterIndex = index,
                    readiumLocator = locator,
                    progressPercent = progressPercent
                )
            }
            updateProgressDisplay()

            // Persist real progression from Readium locator
            val bookId = mutableUiState.value.selectedBookId ?: return
            val locatorJson = CfiMigrator.locatorToJson(locator)
            viewModelScope.launch(mainDispatcher) {
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
        mutableUiState.update { it.copy(readiumViewportHeight = height) }
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



    private fun loadPdfBook(bookId: String, filePath: String, startTime: Long) {
        viewModelScope.launch(mainDispatcher) {
            val loader = pdfContentLoader
            if (loader == null) {
                val message = "PDF content loader is unavailable"
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = message
                    )
                }
                _uiEvent.emit(UiEvent.ShowSnackbar(message))
                return@launch
            }

            try {
                val file = java.io.File(filePath)
                if (!file.exists()) {
                    val message = "File not found. Try importing the book again."
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            error = message
                        )
                    }
                    _uiEvent.emit(UiEvent.ShowSnackbar(message))
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
                _uiEvent.emit(UiEvent.ShowSnackbar(userMessage))
            }
        }
    }

    fun onPdfDocumentLoaded(pages: Int) {
        val currentPages = mutableUiState.value.totalPdfPages
        if (currentPages != pages) {
            mutableUiState.update { it.copy(totalPdfPages = pages) }
            updateProgressDisplay()
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
            if (state.bookFormat == "pdf") {
                // PDF search is handled by PdfWebView via JS bridge.
                // Results arrive via onSearchResults callback → onPdfSearchResults()
                mutableUiState.update { it.copy(isSearching = false) }
                return@launch
            }

            // EPUB search through Readium publication resources
            val results = searchReadiumPublication(query)

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

    fun onPdfSearchResults(json: String) {
        try {
            val array = JSONArray(json)
            val results = mutableListOf<SearchResult>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                results.add(
                    SearchResult(
                        text = obj.getString("text"),
                        page = obj.getInt("page").toFloat(),
                        offset = obj.optInt("offset", 0),
                        chapterIndex = 0
                    )
                )
            }
            mutableUiState.update { it.copy(searchResults = results, isSearching = false) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PDF search results: ${e.message}", e)
            mutableUiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    /**
     * Searches EPUB publication content by iterating reading-order resources.
     * SPIKE result: [Publication.content()] does NOT exist in Readium 3.2.0.
     * Fallback: use [Publication.get] → [Resource.read] to extract text.
     */
    private suspend fun searchReadiumPublication(query: String): List<SearchResult> {
        val publication = mutableUiState.value.readiumPublication ?: return emptyList()
        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()

        for ((index, link) in publication.readingOrder.withIndex()) {
            try {
                val resource = publication.get(link) ?: continue
                val readResult = resource.read()
                val bytes = readResult.getOrNull() ?: continue
                val content = bytes.decodeToString()
                val lowerContent = content.lowercase()

                var offset = 0
                while (true) {
                    val matchIndex = lowerContent.indexOf(lowerQuery, offset)
                    if (matchIndex < 0) break
                    val start = maxOf(0, matchIndex - 60)
                    val end = minOf(content.length, matchIndex + query.length + 60)
                    val snippet = content.substring(start, end)
                    results.add(
                        SearchResult(
                            text = snippet.trim(),
                            offset = matchIndex,
                            chapterIndex = index,
                            page = 0f
                        )
                    )
                    offset = matchIndex + 1
                }
            } catch (_: Exception) {
                // Skip resources that can't be read
            }
        }
        return results
    }

    fun onSearchResultSelected(result: SearchResult) {
        val state = mutableUiState.value
        if (state.bookFormat == "pdf") {
            goToPdfPage(result.page.toInt())
        } else if (state.readiumPublication != null) {
            // Readium path: emit locator for the composable → navigator.go()
            val link = state.readiumPublication!!.readingOrder.getOrNull(result.chapterIndex) ?: return
            val locator = state.readiumPublication!!.locatorFromLink(link) ?: return
            viewModelScope.launch { _navigateToLocator.emit(locator) }
        } else {
            // Legacy EPUB fallback
            if (result.chapterIndex != state.currentChapterIndex) {
                goToChapter(result.chapterIndex)
            }
        }
        onDismissSearch()
    }

    // ── Text Selection (Gap 4) ──────────────────────────────────────

    @Suppress("UNUSED_PARAMETER")
    fun onHighlightTapped(highlightId: String, text: String, left: Float, top: Float, right: Float, bottom: Float) {
        mutableUiState.update {
            it.copy(
                selectedText = text,
                selectionRect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()),
                showColorPicker = false,
                showContextMenu = true
            )
        }
    }

    /** @suppress debug — shows toast at each pipeline stage */
    private fun debugToast(msg: String) {
        Log.d(TAG, "DEBUG: $msg")
        _uiEvent.tryEmit(UiEvent.ShowToast("🐛 $msg"))
    }

    /**
     * Called from [PdfWebView] JS bridge with raw selection coordinates.
     * Delegates to [onTextSelection] for state update.
     */
    fun onTextSelectionEvent(text: String, left: Float, top: Float, right: Float, bottom: Float) {
        onTextSelection(text, Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
    }

    fun onTextSelection(text: String, rect: Rect) {
        Log.d("ReaderVM", "onTextSelection: \"${text.take(50)}\" rect=$rect")
        mutableUiState.update {
            it.copy(
                selectedText = text,
                selectionRect = rect,
                showColorPicker = true,
                showContextMenu = false
            )
        }
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

        debugToast("STEP 3: FaPN3 activado -> showContextMenu=true")
        // Show FaPN3 over the highlighted text — keep selectedText and
        // selectionRect so the context menu (tag/note/comment/share/delete)
        // positions correctly over the now-highlighted span.
        mutableUiState.update {
            it.copy(
                showColorPicker = false,
                showContextMenu = true
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

    // ── Readium Selection (Phase 2+) ───────────────────────────────

    /**
     * Called by [ReadiumReaderContent] when the [SelectableNavigator]
     * returns a non-null [Selection]. Stores the locator (needed for
     * highlight creation) and derives a Compose-friendly [Rect] from the
     * viewport-space [RectF].
     */
    fun onReadiumSelection(locator: Locator, rect: RectF, text: String) {
        val selectionRect = Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
        mutableUiState.update {
            it.copy(
                readiumSelectionLocator = locator,
                selectedText = text,
                selectionRect = selectionRect,
                showColorPicker = true,
                showContextMenu = false
            )
        }
    }

    // ── Readium Highlights (Phase 3+) ──────────────────────────────

    /**
     * Called when the user picks a highlight colour for the current
     * [readiumSelectionLocator]. Builds a fresh [Locator] from the stored
     * locator, serialises it to JSON, and persists via [createHighlight].
     */
    fun onReadiumHighlightColorSelected(color: String) {
        val state = mutableUiState.value
        val bookId = state.selectedBookId ?: return
        val locator = state.readiumSelectionLocator ?: return
        val text = state.selectedText ?: return
        val locatorJson = CfiMigrator.locatorToJson(locator)

        createHighlight(
            bookId = bookId,
            cfiRange = "readium:${locator.href}",
            textContent = text,
            color = color,
            locatorJson = locatorJson
        )

        mutableUiState.update {
            it.copy(
                showColorPicker = false,
                showContextMenu = true
            )
        }
    }

    /**
     * Soft-deletes a highlight by setting [Highlight.deletedAtEpochMillis].
     * The Room flow emission triggers the decoration reapply cycle.
     */
    fun onReadiumDeleteHighlight(highlightId: String) {
        val state = mutableUiState.value
        val existing = state.highlights.find { it.id == highlightId } ?: return
        val updated = existing.copy(
            deletedAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        viewModelScope.launch(mainDispatcher) {
            readerRepository.upsertHighlight(updated)
        }
    }

    /**
     * Updates the colour of an existing highlight in-place.
     * Reapply is triggered reactively via the highlights Flow.
     */
    fun onReadiumUpdateHighlightColor(highlightId: String, color: String) {
        val state = mutableUiState.value
        val existing = state.highlights.find { it.id == highlightId } ?: return
        val updated = existing.copy(
            color = color,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        viewModelScope.launch(mainDispatcher) {
            readerRepository.upsertHighlight(updated)
        }
    }

    fun onSelectionCleared() {
        Log.d(TAG, "onSelectionCleared — dismissing selection overlay")
        mutableUiState.update {
            it.copy(
                showColorPicker = false,
                showContextMenu = false,
                selectedText = null,
                selectionRect = null,
                debugForceMenu = false
            )
        }
    }

    // ── Debug: Force menu visibility ──────────────────────────────
    //
    // Long-press the reader header to toggle this. If the menu still
    // doesn't appear, the bug is in SelectionOverlay or FloatingContextMenu.
    // If it DOES appear, the bug is in the JS→bridge→VM pipeline.
    //
    fun onDebugForceMenu() {
        val current = mutableUiState.value.debugForceMenu
        if (current) {
            // Toggle off — clear everything
            onSelectionCleared()
            return
        }
        debugToast("DEBUG: Forzando menú FaPN3 con rect hardcodeado")
        mutableUiState.update {
            it.copy(
                selectedText = "Texto de prueba debug",
                // Center-ish of a 1080px-wide viewport
                selectionRect = Rect(200, 200, 600, 250),
                showColorPicker = false,
                showContextMenu = true,
                debugForceMenu = true
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

    // ── Chapter Navigation ─────────────────────────────────────────

    fun goToNextChapter() {
        val currentIndex = mutableUiState.value.currentChapterIndex
        val totalChapters = mutableUiState.value.chapters.size

        if (currentIndex < totalChapters - 1) {
            val newIndex = currentIndex + 1
            mutableUiState.update { it.copy(currentChapterIndex = newIndex) }
            updateProgressForChapter(newIndex)
            sleepTimerManager.onChapterChanged()
        }
    }

    fun goToPreviousChapter() {
        val currentIndex = mutableUiState.value.currentChapterIndex

        if (currentIndex > 0) {
            val newIndex = currentIndex - 1
            mutableUiState.update { it.copy(currentChapterIndex = newIndex) }
            updateProgressForChapter(newIndex)
            sleepTimerManager.onChapterChanged()
        }
    }

    fun onTapZone(isLeftZone: Boolean) {
        // Always dismiss selection overlay before navigating
        onSelectionCleared()

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
            mutableUiState.update { it.copy(currentPdfPage = newPage) }
            updatePdfProgress(newPage, totalPages)
            updateProgressDisplay()
        }
    }

    fun goToPreviousPdfPage() {
        val currentPage = mutableUiState.value.currentPdfPage

        if (currentPage > 0) {
            val newPage = currentPage - 1
            mutableUiState.update { it.copy(currentPdfPage = newPage) }
            updatePdfProgress(newPage, mutableUiState.value.totalPdfPages)
            updateProgressDisplay()
        }
    }

    fun goToPage(pageNumber: Int) {
        val totalPages = mutableUiState.value.totalPdfPages
        if (pageNumber in 1..totalPages) {
            val newPage = pageNumber - 1
            mutableUiState.update { it.copy(currentPdfPage = newPage) }
            updatePdfProgress(newPage, totalPages)
            updateProgressDisplay()
        }
    }

    fun goToPdfPage(pageIndex: Int) {
        val totalPages = mutableUiState.value.totalPdfPages
        if (pageIndex in 0 until totalPages) {
            mutableUiState.update { it.copy(currentPdfPage = pageIndex) }
            updatePdfProgress(pageIndex, totalPages)
            updateProgressDisplay()
        }
    }

    private fun updatePdfProgress(currentPage: Int, totalPages: Int) {
        val bookId = mutableUiState.value.selectedBookId ?: return

        if (totalPages > 0) {
            val percentage = (((currentPage + 1).toFloat() / totalPages) * 100f)
                .coerceIn(0f, 100f)
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
            // Chapter-based percentage: cap at 99% so the last chapter doesn't show 100%
            // until Readium's locator totalProgression confirms it.
            val percentage = (((chapterIndex + 1).toFloat() / totalChapters) * 100f)
                .coerceIn(0f, 99f)
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
     *
     * Priority:
     * 1. PDF → (currentPage+1)/totalPages
     * 2. EPUB + Readium locator available → totalProgression from locator (most accurate)
     * 3. EPUB fallback → chapter-based (capped at 99%)
     */
    private fun updateProgressDisplay() {
        val state = mutableUiState.value
        val percent: Float
        val label: String

        if (state.totalPdfPages > 0) {
            val current = state.currentPdfPage + 1
            val total = state.totalPdfPages
            percent = ((current.toFloat() / total) * 100f).coerceIn(0f, 100f)
            label = "$current / $total"
        } else if (state.readiumLocator != null) {
            // Use Readium's totalProgression for real overall percentage
            val totalProgression = state.readiumLocator.locations.totalProgression?.toFloat() ?: 0f
            percent = (totalProgression * 100f).coerceIn(0f, 100f)
            val current = state.currentChapterIndex + 1
            val total = state.chapters.size.coerceAtLeast(1)
            label = "$current / $total"
        } else if (state.chapters.isNotEmpty()) {
            val current = state.currentChapterIndex + 1
            val total = state.chapters.size
            percent = ((current.toFloat() / total) * 100f).coerceIn(0f, 99f)
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
                mutableUiState.update { state ->
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
        color: String = HighlightColor.YELLOW.hex,
        locatorJson: String? = null
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
                deletedAtEpochMillis = null,
                locatorJson = locatorJson
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
            updateProgressForChapter(index)
            sleepTimerManager.onChapterChanged()
        }
    }

    // ── Sleep Timer (delegated to SleepTimerManager) ─────────────────

    fun startSleepTimer(minutes: Int) = sleepTimerManager.startTimer(minutes)

    fun cancelSleepTimer() = sleepTimerManager.cancel()

    fun dismissSleepTimerOverlay() = sleepTimerManager.dismissOverlay()

    fun formatSleepTimerRemaining(secs: Int): String = sleepTimerManager.formatRemaining(secs)

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
            mutableUiState.update { it.copy(currentPdfPage = pageIndex) }
            updatePdfProgress(pageIndex, mutableUiState.value.totalPdfPages)
            updateProgressDisplay()
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
    private val application: Application,
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val readerPreferences: ReaderPreferences,
    private val pdfContentLoader: PdfContentLoader,
    private val defaultBookId: String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            return ReaderViewModel(
                application = application,
                readerRepository = readerRepository,
                readingStatsRepository = readingStatsRepository,
                updateReadingProgressUseCase = UpdateReadingProgressUseCase(readerRepository),
                readerPreferences = readerPreferences,
                pdfContentLoader = pdfContentLoader,
                defaultBookId = defaultBookId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
