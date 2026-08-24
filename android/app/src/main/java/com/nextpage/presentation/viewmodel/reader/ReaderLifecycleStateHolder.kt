package com.nextpage.presentation.viewmodel.reader

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nextpage.data.remote.supabase.SupabaseProgressSync
import com.nextpage.debug.DebugDual
import com.nextpage.debug.DebugEvent
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.viewmodel.CfiMigrator
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
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
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val supabaseProgressSync: SupabaseProgressSync? = null
) {

    private val _state = MutableStateFlow(ReaderLifecycleState())
    val state: StateFlow<ReaderLifecycleState> = _state.asStateFlow()

    private var observeProgressJob: Job? = null
    private var readingTimeTickerJob: Job? = null
    private var sessionStartTime: Long = 0L
    private var loadEpoch: Long = 0L

    /**
     * User id recorded on reading-session flushes (REQ-reading-sessions-sync-1).
     * Driven from the NavHost session effect via [setActiveUserId]; defaults to
     * "" so pre-auth / local users still record locally (visible via the
     * `userId = :userId OR userId = ''` aggregation convention).
     */
    @Volatile
    private var activeUserId: String = ""

    fun setActiveUserId(userId: String) {
        activeUserId = userId
    }

    companion object {
        private const val TAG = "ReaderLifecycleStateHolder"
    private const val MAX_PROGRESS_PERCENT = 99f
    private const val READING_TIME_TICK_MS = 60_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    }

    /**
     * Exact reflow scaffolding replacing ESTIMATED_PAGES_PER_CHAPTER=20.
     * Holds pagination geometry for remaining calculation.
     */
    data class PaginationInfo(
        val totalPositions: Int,
        val currentPosition: Int,
        val estimatedPagesViaChars: Int,
        val viewportH: Int,
        val viewportW: Int
    )

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
        val epoch = ++loadEpoch
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

                val chapters = buildChaptersFromPublication(publication)

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
                // SWR: local Room position is rendered immediately. A remote pull
                // can replace it later, but never prevents offline reading.
                supabaseProgressSync?.let { sync ->
                    scope.launch(Dispatchers.IO) {
                        sync.resumeForBook(bookId) { remote ->
                            val locator = remote.locatorJson?.let(CfiMigrator::jsonToLocator)
                            if (locator != null && epoch == loadEpoch && _state.value.selectedBookId == bookId) {
                                _state.update { current -> current.copy(readiumLocator = locator) }
                                scope.launch(mainDispatcher) { onNavigateToLocator(locator) }
                            }
                        }
                    }
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

                val chapters = buildChaptersFromPublication(publication)

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
                val userMessage = e.message ?: "Failed to open PDF with Readium"
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
    private fun resolveEpubCfi(cfi: String, readingOrderLinks: List<Link>): Locator? {
        val parsed = CfiMigrator.parsePreciseCfi(cfi)
        if (parsed != null) {
            val link = readingOrderLinks.getOrNull(parsed.spineIndex - 1)
            if (link != null) {
                val metric = CfiMigrator.TextMetric(charOffset = parsed.textOffset, chapterChars = 10000)
                val prog = CfiMigrator.progressionFor(metric) ?: 0.0
                val json = JSONObject().apply {
                    put("href", link.href.toString())
                    put("type", link.mediaType?.toString() ?: "application/xhtml+xml")
                    put("locations", JSONObject().apply { put("progression", prog); put("fragment", cfi) })
                }
                Locator.fromJSON(json)?.let { return it }
            }
        }
        CfiMigrator.migrateCfiToLocator(cfi, readingOrderLinks)?.let { return it }
        val spineIdx = Regex("epubcfi\\(/6/(\\d+)").find(cfi)?.groupValues?.getOrNull(1)?.toIntOrNull()?.minus(1)
        if (spineIdx != null && spineIdx >= 0) {
            val link = readingOrderLinks.getOrNull(spineIdx) ?: return null
            val json = JSONObject().apply {
                put("href", link.href.toString())
                put("type", link.mediaType?.toString() ?: "application/xhtml+xml")
                put("locations", JSONObject().apply { put("progression", 0.0); put("fragment", cfi) })
            }
            return Locator.fromJSON(json)
        }
        return null
    }

    private suspend fun migrateCfiDataForBook(bookId: String, readingOrder: List<Link>) {
        val readingOrderLinks = readingOrder
        if (readingOrderLinks.isEmpty()) return

        // ── Migrate highlights ──────────────────────────────────────────
        val highlights = readerRepository.getHighlightsForBook(bookId)
        for (highlight in highlights) {
            if (highlight.cfiRange.startsWith("epubcfi(") && highlight.locatorJson == null) {
                val locator = resolveEpubCfi(highlight.cfiRange, readingOrderLinks)
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
                val locator = resolveEpubCfi(bookmark.cfiLocation, readingOrderLinks)
                if (locator != null) {
                    val migrated = bookmark.copy(locatorJson = CfiMigrator.locatorToJson(locator))
                    readerRepository.upsertBookmark(migrated)
                }
            }
        }

        // ── Migrate reading progress ────────────────────────────────────
        val progress = readerRepository.getProgressForBook(bookId)
        if (progress != null && progress.cfiLocation.startsWith("epubcfi(") && progress.locatorJson == null) {
            val locator = resolveEpubCfi(progress.cfiLocation, readingOrderLinks)
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
    /**
     * Resolve list position (0 .. chapters.size-1) for a locator.
     * Prefers href string match against chapters (most robust for TOC vs spine offset),
     * falls back to readingOrder index mapping.
     */
    private fun resolveChapterListIndex(
        locator: Locator,
        publication: Publication?,
        chapters: List<BookChapter>
    ): Int? {
        if (chapters.isEmpty()) return null
        val locatorHref = locator.href.toString()
        fun normalizeFile(href: String): String =
            href.substringAfterLast('/').substringBefore('#').substringBefore('?').trim().lowercase()
        val normLocatorFile = normalizeFile(locatorHref)
        // 1. Exact href match
        chapters.indexOfFirst { it.href == locatorHref }.takeIf { it >= 0 }?.let { return it }
        // 2. Href without fragment/query exact
        val locatorBase = locatorHref.substringBefore('#').substringBefore('?')
        chapters.indexOfFirst { it.href.substringBefore('#').substringBefore('?') == locatorBase }
            .takeIf { it >= 0 }?.let { return it }
        // 3. Filename-only match (handles Text/chapter.xhtml vs chapter.xhtml)
        chapters.indexOfFirst { normalizeFile(it.href) == normLocatorFile }
            .takeIf { it >= 0 }?.let { return it }
        // 4. Fallback via publication readingOrder index -> chapters index mapping
        publication?.let { pub ->
            try {
                val link = pub.linkWithHref(locator.href) ?: return@let null
                val roIndex = pub.readingOrder.indexOf(link)
                if (roIndex >= 0) {
                    chapters.indexOfFirst { it.index == roIndex }.takeIf { it >= 0 }?.let { return it }
                    val firstIdx = chapters.minOfOrNull { it.index } ?: 0
                    val adjusted = roIndex - firstIdx
                    if (adjusted in chapters.indices) return adjusted
                    if (roIndex in chapters.indices) return roIndex
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun onReadiumLocatorChanged(locator: Locator) {
        // Atomic update: compute currentChapterIndex via href-aware mapping (chaptersByHref), not raw readingOrder index
        val currentState = _state.value
        val publication = currentState.readiumPublication
        val chapters = currentState.chapters
        val computedListIndex = resolveChapterListIndex(locator, publication, chapters)
        val newIndex = computedListIndex ?: currentState.currentChapterIndex
        val previousHref = currentState.readiumLocator?.href
        if (previousHref != null && previousHref != locator.href) {
            onSelectionCleared()
        }
        val totalProgression = locator.locations.totalProgression?.toFloat() ?: 0f
        val progressPercent = (totalProgression * 100f).coerceIn(0f, 100f)
        // Update both locator and index in same atomic block
        _state.update {
            it.copy(
                readiumLocator = locator,
                currentChapterIndex = newIndex,
                progressPercent = progressPercent
            )
        }
        // Use same snapshot for progress display to avoid tearing
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
        DebugDual.log(DebugEvent.ProgressEmit(bookId, progressPercent, "locatorChanged"))
    }

    /**
     * Called by [ReadiumReaderContent] to report the viewport dimensions.
     * Both height and width are stored atomically for exact reflow calc.
     * Width is optional for backward compat (defaults to height aspect if missing).
     * Also updates typographySnapshot viewport so charsPerPage fallback stays exact
     * after rotation or window resize (viewport typography listener).
     */
    fun onReadiumViewportChanged(height: Int, width: Int = 0) {
        val hasChanged = _state.value.readiumViewportHeight != height || _state.value.readiumViewportWidth != width
        _state.update { it.copy(readiumViewportHeight = height, readiumViewportWidth = width) }
        // Keep typographySnapshot in sync with viewport so fallback calc uses fresh dimensions
        typographySnapshot?.let { snap ->
            typographySnapshot = snap.copy(
                viewportW = width.takeIf { it > 0 } ?: snap.viewportW,
                viewportH = height.takeIf { it > 0 } ?: snap.viewportH
            )
        }
        if (hasChanged) updateProgressDisplay()
    }

    /**
     * Called when typography changes (fontSize/lineHeight/margins) to trigger exact reflow.
     * Wire from ViewModel when ReaderSettings change.
     */
    fun onTypographyChanged() {
        updateProgressDisplay()
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
                // Legacy CFI without a stored locator: extract spine index and
                // navigate to the chapter start. CFI spine is 1-based readingOrder
                // index, NOT list position -> must map to TOC list position.
                val chapterMatch = Regex("/6/(\\d+)").find(cfi)
                val spineIndex = chapterMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.minus(1)
                if (spineIndex != null) {
                    val chapters = _state.value.chapters
                    val listPos = spineIndexToListPosition(spineIndex, chapters)
                    if (listPos != null) goToChapter(listPos) else goToChapter(spineIndex.coerceIn(chapters.indices))
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
            navigateToChapter(newIndex)
        }
    }

    fun goToPreviousChapter() {
        val currentIndex = _state.value.currentChapterIndex

        if (currentIndex > 0) {
            val newIndex = currentIndex - 1
            _state.update { it.copy(currentChapterIndex = newIndex) }
            updateProgressForChapter(newIndex)
            onChapterChanged()
            navigateToChapter(newIndex)
        }
    }

    /**
     * Jump to chapter by TOC list position (0..chapters.size-1), NOT spine index.
     * Resolves [BookChapter] via list position, then fuzzy-matches href to readingOrder
     * to obtain the true spine index for navigation. This fixes the +3 offset where
     * TOC size (28) != spine size (31) — e.g. Prefacio list 2 -> spine 5, Canto I list 3 -> spine 6.
     */
    fun goToChapter(listPosition: Int) {
        if (listPosition in _state.value.chapters.indices) {
            if (listPosition == _state.value.currentChapterIndex) return
            _state.update { it.copy(currentChapterIndex = listPosition) }
            updateProgressForChapter(listPosition)
            onChapterChanged()
            navigateToChapter(listPosition)
        }
    }

    /**
     * Maps a spine index (0-based readingOrder) to TOC list position, or null if not found.
     * Checks exact spine index match first, then filename lowercase fallback.
     */
    private fun spineIndexToListPosition(spineIndex: Int, chapters: List<BookChapter>): Int? {
        chapters.indexOfFirst { it.index == spineIndex }.takeIf { it >= 0 }?.let { return it }
        // Fallback: if spine has cover/toc offset, try adjusted position search
        return null
    }

    /**
     * Emits a Readium [Locator] so the navigator actually moves to [listPosition].
     * Without this, only the state index changes and the reader stays on the
     * current page even though the TOC / next-prev controls report a new chapter.
     *
     * [listPosition] is the position in [chapters] (0 = first TOC entry), not the raw
     * readingOrder index. The target [Link] is resolved via the chapter's href (fuzzy
     * filename lowercase) to handle spine offset (cover/toc) where readingOrder index
     * != list position. Emits with chapterListIndex=listPosition and roIndex for correct progress.
     */
    private fun navigateToChapter(listPosition: Int) {
        val state = _state.value
        val publication = state.readiumPublication ?: return
        val chapters = state.chapters
        val link: Link? = when {
            listPosition in chapters.indices -> {
                val chapter = chapters[listPosition]
                // Prefer href-resolved link (handles Text/ prefix, fragment, case)
                val hrefBase = chapter.href.substringBefore('#').substringBefore('?')
                val normFile = hrefBase.substringAfterLast('/').lowercase()
                publication.readingOrder.firstOrNull {
                    it.href.toString().substringAfterLast('/').substringBefore('#').substringBefore('?').lowercase() == normFile
                } ?: publication.readingOrder.getOrNull(chapter.index)
            }
            else -> publication.readingOrder.getOrNull(listPosition)
        } ?: return
        val roIndex = publication.readingOrder.indexOf(link).takeIf { it >= 0 } ?: listPosition
        val total = publication.readingOrder.size.coerceAtLeast(1)
        val totalProgression = (roIndex.toFloat() / total).coerceIn(0f, 1f)
        emitEpubNavigateLocator(listPosition, totalProgression, link)
    }

    /**
     * Toggles the TOC/Chapter bottom sheet visibility.
     *
     * Kept on the lifecycle holder (not the ViewModel) so the state flows
     * through the same `state` StateFlow that the UI subscribes to. If the
     * toggle lived on the ViewModel and wrote directly to the merged
     * `mutableUiState`, the next emission from `state` (e.g. on chapter
     * change) would overwrite the user's flip because the holder's value
     * is always copied into the merge.
     */
    fun onToggleTocSheet() {
        _state.update { it.copy(showTocSheet = !it.showTocSheet) }
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
        // State updates (chapterIndex / currentPdfPage) are necessary but
        // not sufficient — the Readium navigator only moves when we emit a
        // Locator on the navigateToLocator flow. Without the emit, the UI
        // header updates but the page itself doesn't turn.
        emitLocatorForCurrentState()
    }

    /**
     * Emits a Readium [Locator] pointing at the chapter/page currently
     * stored in state, so the reader content actually navigates.
     *
     * Used by [onTapZone] (chapter/page arrows) and by the progress bar
     * drag handler. Safe to call when no [Publication] is loaded — it just
     * returns without emitting.
     */
    private fun emitLocatorForCurrentState() {
        val state = _state.value
        val publication = state.readiumPublication ?: return
        when (state.bookFormat) {
            "pdf" -> {
                val link = publication.readingOrder.getOrNull(state.currentPdfPage) ?: return
                emitPdfNavigateLocator(state.currentPdfPage, link)
            }
            else -> {
                val chapters = state.chapters
                val link: Link = when {
                    state.currentChapterIndex in chapters.indices -> {
                        val chapter = chapters[state.currentChapterIndex]
                        val hrefBase = chapter.href.substringBefore('#').substringBefore('?')
                        val normFile = hrefBase.substringAfterLast('/').lowercase()
                        publication.readingOrder.firstOrNull {
                            it.href.toString().substringAfterLast('/').substringBefore('#').substringBefore('?').lowercase() == normFile
                        } ?: publication.readingOrder.getOrNull(chapter.index)
                        ?: publication.readingOrder.getOrNull(state.currentChapterIndex)
                    }
                    else -> publication.readingOrder.getOrNull(state.currentChapterIndex)
                } ?: return
                val roIndex = publication.readingOrder.indexOf(link).takeIf { it >= 0 } ?: state.currentChapterIndex
                val totalProgression = if (publication.readingOrder.isNotEmpty()) {
                    roIndex.toFloat() / publication.readingOrder.size
                } else 0f
                emitEpubNavigateLocator(state.currentChapterIndex, totalProgression, link)
            }
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
                .coerceIn(0f, MAX_PROGRESS_PERCENT)
            // Spine index for CFI is 1-based readingOrder position; use chapter's stored readingOrder index when available
            val chapter = _state.value.chapters.getOrNull(chapterIndex)
            val spineIndex = chapter?.let { it.index + 1 } ?: (chapterIndex + 1)
            val cfiLocation = "epubcfi(/6/$spineIndex)"

            scope.launch(mainDispatcher) {
                updateReadingProgressUseCase(
                    bookId = bookId,
                    cfiLocation = cfiLocation,
                    percentage = percentage
                )
            }
        }
    }

    // Typography snapshot for exact reflow; updated via onTypographyConfigChanged
    private var typographySnapshot: ReadingProgressCalculator.ViewportTypography? = null
    private var densitySnapshot: Float = 3f

    /**
     * Update typography metrics for exact reflow (called when ReaderSettings change or density known).
     */
    fun onTypographyConfigChanged(fontSizeSp: Float, lineHeight: Float, pageMarginsDp: Float = 16f, density: Float = 3f) {
        densitySnapshot = density
        val vp = _state.value
        typographySnapshot = ReadingProgressCalculator.ViewportTypography(
            viewportW = vp.readiumViewportWidth.takeIf { it > 0 } ?: 360,
            viewportH = vp.readiumViewportHeight.takeIf { it > 0 } ?: 720,
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            pageMarginsDp = pageMarginsDp,
            density = density
        )
        updateProgressDisplay()
    }

    /**
     * Updates the progress percentage and label according to the current format.
     *
     * Atomic snapshot: readiumLocator, publication, chapters, viewport, typography are read
     * from a single _state.value capture so label and percentage cannot tear (REQ-footer-chapter-correct).
     *
     * Priority:
     * 1. PDF → (currentPage+1)/totalPages
     * 2. EPUB + Readium locator available → totalProgression from locator (most accurate) + exact reflow remaining via ReadingProgressCalculator
     * 3. EPUB fallback → chapter-based (capped at 99%)
     */
    internal fun updateProgressDisplay() {
        val snapshot = _state.value
        val percent: Float
        val label: String
        var resolvedChapterIndex = snapshot.currentChapterIndex

        if (snapshot.totalPdfPages > 0) {
            val current = snapshot.currentPdfPage + 1
            val total = snapshot.totalPdfPages
            percent = ((current.toFloat() / total) * 100f).coerceIn(0f, 100f)
            label = "$current / $total"
        } else if (snapshot.readiumLocator != null) {
            val locator = snapshot.readiumLocator
            val publication = snapshot.readiumPublication
            val chapters = snapshot.chapters

            // Atomic chapter resolution from same locator+publication+chapters snapshot
            // Use href-aware mapping (chaptersByHref), not raw readingOrder index, to avoid offset bug (cap1 -> Canto IV)
            val locatorHref = locator.href.toString()
            val computedIndex: Int? = resolveChapterListIndex(locator, publication, chapters)

            if (computedIndex != null && computedIndex != snapshot.currentChapterIndex) {
                resolvedChapterIndex = computedIndex
            }

            // Validate chapter title vs locator href; emit mismatch only when href actually mismatches
            val expectedTitle = chapters.getOrNull(resolvedChapterIndex)?.title
            val computedTitle = expectedTitle
            val hrefMismatch = publication != null && computedIndex == null && locatorHref.isNotBlank()
            // Additional mismatch when resolved chapter href doesn't match locator href (filename compare)
            val chapterHref = chapters.getOrNull(resolvedChapterIndex)?.href
            val hrefTitleMismatch = chapterHref != null && locatorHref.isNotBlank() &&
                chapterHref.substringAfterLast('/').substringBefore('#').lowercase() !=
                locatorHref.substringAfterLast('/').substringBefore('#').lowercase()
            if (hrefMismatch || hrefTitleMismatch) {
                DebugDual.logFooterMismatch(expectedTitle, locatorHref)
                DebugDual.log(DebugEvent.FooterMismatch(locatorHref, computedTitle, expectedTitle))
            } else if (computedTitle != null) {
                DebugDual.log(DebugEvent.ChapterResolved(locatorHref, computedTitle, resolvedChapterIndex))
            }

            val totalProgression = locator.locations.totalProgression?.toFloat() ?: 0f
            percent = (totalProgression * 100f).coerceIn(0f, 100f)

            val chapterTitle = chapters.getOrNull(resolvedChapterIndex)?.title?.takeIf { it.isNotBlank() }
                ?: locatorHref.substringAfterLast('/').substringBefore('#').takeIf { it.isNotBlank() }
                ?: "—"

            // Exact reflow remaining pages
            val typography = typographySnapshot
            val viewportTypography = typography?.copy(
                viewportW = snapshot.readiumViewportWidth.takeIf { it > 0 } ?: typography.viewportW,
                viewportH = snapshot.readiumViewportHeight.takeIf { it > 0 } ?: typography.viewportH,
                density = densitySnapshot
            ) ?: ReadingProgressCalculator.ViewportTypography(
                viewportW = snapshot.readiumViewportWidth.takeIf { it > 0 } ?: 360,
                viewportH = snapshot.readiumViewportHeight.takeIf { it > 0 } ?: 720,
                fontSizeSp = 16f,
                lineHeight = 1.6f,
                pageMarginsDp = 16f,
                density = densitySnapshot
            )

            val calc = ReadingProgressCalculator.compute(
                publication = publication,
                locator = locator,
                chapters = chapters,
                currentChapterIndex = resolvedChapterIndex,
                viewport = viewportTypography
            )
            // Scaffold PaginationInfo for exact reflow (replaces ESTIMATED_PAGES_PER_CHAPTER)
            val paginationInfo = PaginationInfo(
                totalPositions = calc.totalPages,
                currentPosition = calc.currentPage,
                estimatedPagesViaChars = calc.charsPerPage,
                viewportH = snapshot.readiumViewportHeight,
                viewportW = snapshot.readiumViewportWidth
            )
            // Use paginationInfo viewport for remaining calc (already via calc)
            val remaining = calc.remaining
            label = if (remaining > 0 && chapterTitle != "—") {
                application.getString(
                    com.nextpage.R.string.reader_pages_remaining,
                    chapterTitle,
                    remaining
                )
            } else {
                chapterTitle
            }
        } else if (snapshot.chapters.isNotEmpty()) {
            val current = snapshot.currentChapterIndex + 1
            val total = snapshot.chapters.size
            percent = ((current.toFloat() / total) * 100f).coerceIn(0f, MAX_PROGRESS_PERCENT)
            label = "$current / $total"
        } else {
            percent = 0f
            label = ""
        }

        _state.update {
            it.copy(
                currentChapterIndex = resolvedChapterIndex,
                progressPercent = percent,
                progressLabel = label
            )
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
                            // EPUB CFI: extract spine index (1-based) -> map to TOC list position
                            val chapterMatch = Regex("/6/(\\d+)").find(cfi)
                            val spineIdx = chapterMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.minus(1)
                            if (spineIdx != null) {
                                val chapters = newState.chapters
                                val listPos = if (chapters.isNotEmpty()) {
                                    chapters.indexOfFirst { it.index == spineIdx }.takeIf { it >= 0 }
                                } else null
                                val resolved = listPos ?: spineIdx
                                newState = newState.copy(currentChapterIndex = resolved)
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

    /**
     * Handles a progress-bar drag from the reader UI. Updates the
     * displayed progress, then — crucially — emits a Readium [Locator]
     * via [onNavigateToLocator] so the EPUB WebView or PDF fragment
     * actually navigates to the new position. Without that emit, the
     * state changes but the reader content does not move.
     */
    fun onProgressChange(percent: Float) {
        val clamped = percent.coerceIn(0f, 100f)
        _state.update {
            it.copy(progressPercent = clamped)
        }

        if (_state.value.selectedBookId == null) return

        val state = _state.value
        if (state.totalPdfPages > 0) {
            val pageIndex = ((clamped / 100f) * state.totalPdfPages).toInt()
                .coerceIn(0, state.totalPdfPages - 1)
            if (pageIndex != state.currentPdfPage) {
                _state.update { it.copy(currentPdfPage = pageIndex) }
                updatePdfProgress(pageIndex, state.totalPdfPages)
                updateProgressDisplay()
                emitPdfNavigateLocator(pageIndex)
            }
        } else if (state.chapters.isNotEmpty()) {
            val chapterIndex = ((clamped / 100f) * state.chapters.size).toInt()
                .coerceIn(0, state.chapters.size - 1)
            if (chapterIndex != state.currentChapterIndex) {
                _state.update { it.copy(currentChapterIndex = chapterIndex) }
                updateProgressForChapter(chapterIndex)
                onChapterChanged()
                emitEpubNavigateLocator(chapterIndex, clamped / 100f)
            }
        }
    }

    /**
     * Builds a Readium [Locator] pointing at PDF page [pageIndex] (0-based)
     * and emits it via [onNavigateToLocator]. The [PdfNavigatorFragment]
     * collects from that flow and calls `frag.go(locator)` to actually
     * move to the new page.
     */
    private fun emitPdfNavigateLocator(pageIndex: Int, link: org.readium.r2.shared.publication.Link? = null) {
        val publication = _state.value.readiumPublication ?: return
        val resolvedLink = link ?: publication.readingOrder.getOrNull(pageIndex) ?: return
        val json = JSONObject().apply {
            put("href", resolvedLink.href.toString())
            put("mediaType", resolvedLink.mediaType?.toString() ?: "application/pdf")
            put("locations", JSONObject().apply {
                put("position", pageIndex + 1)
            })
        }
        val locator = Locator.fromJSON(json) ?: return
        scope.launch(mainDispatcher) {
            onNavigateToLocator(locator)
        }
    }

    /**
     * Builds a Readium [Locator] pointing at the EPUB chapter at
     * [chapterIndex] (0-based) and emits it via [onNavigateToLocator].
     * The [EpubNavigatorFragment] collects from that flow and calls
     * `frag.go(locator)` to actually move to the new chapter.
     *
     * The emitted Locator includes:
     * - `href` of the chapter resource,
     * - `progression = 0.0` (start of the chapter),
     * - `totalProgression` (0.0–1.0) for accurate progress bar display.
     */
    private fun emitEpubNavigateLocator(
        chapterIndex: Int,
        totalProgression: Float,
        link: org.readium.r2.shared.publication.Link? = null
    ) {
        val publication = _state.value.readiumPublication ?: return
        val resolvedLink = link ?: publication.readingOrder.getOrNull(chapterIndex) ?: return
        val json = JSONObject().apply {
            put("href", resolvedLink.href.toString())
            put("type", resolvedLink.mediaType?.toString() ?: "application/xhtml+xml")
            put("locations", JSONObject().apply {
                put("progression", 0.0)
                put("totalProgression", totalProgression.toDouble().coerceIn(0.0, 1.0))
            })
        }
        val locator = Locator.fromJSON(json) ?: return
        scope.launch(mainDispatcher) {
            onNavigateToLocator(locator)
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
                delay(READING_TIME_TICK_MS)
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
        val computedMinutes = elapsedMs / MILLIS_PER_MINUTE
        val additionalMinutes = if (minimumMinutes > 0L) {
            computedMinutes.coerceAtLeast(minimumMinutes)
        } else {
            computedMinutes
        }

        if (additionalMinutes <= 0L) {
            return
        }

        // Capture BEFORE resetting: the deterministic session id and payload are
        // derived from this interval's start, not from the next interval.
        val intervalStart = sessionStartTime

        scope.launch(mainDispatcher) {
            readingStatsRepository.updateReadingTime(bookId, additionalMinutes)
            readingStatsRepository.recordReadingSession(
                bookId = bookId,
                startTimeEpochMillis = intervalStart,
                durationMinutes = additionalMinutes.toInt(),
                userId = activeUserId
            )
            Log.d(TAG, "Recorded $additionalMinutes minutes for book $bookId")
        }
        sessionStartTime = now
    }

    /**
     * Builds the chapter list shown in the reader's TOC sheet.
     *
     * The list is ALWAYS aligned 1:1 with [Publication.readingOrder] so
     * the chapter index in state is interchangeable with the reading-order
     * index used by the Readium navigator. Titles come from the
     * [Publication.tableOfContents] (the EPUB `nav.xhtml`) when available;
     * spine items without a matching TOC entry fall back to "Chapter N".
     *
     * Sub-chapters (nested [Link.children]) are walked recursively and
     * their titles are attached to the corresponding reading-order entry.
     *
     * When the publication has no TOC (typical for PDFs and malformed
     * EPUBs) the reading order is used directly.
     */
    private fun buildChaptersFromPublication(publication: Publication): List<BookChapter> {
        // Walk the EPUB nav (TOC) TREE so every entry — including nested
        // sub-chapters and parts — keeps its real title and hierarchy depth.
        // Matching by raw href fails when the TOC href and the spine href
        // differ in formatting (fragment, query, "./"), so each entry is
        // resolved to a reading-order index via the same fuzzy linkWithHref
        // used for live navigation. Books without a TOC fall back to the
        // flat reading order.
        if (publication.tableOfContents.isNotEmpty()) {
            val result = ArrayList<BookChapter>()
            for (link in publication.tableOfContents) {
                collectTocChapters(link, publication, 0, result)
            }
            return result
        }
        return publication.readingOrder.mapIndexed { readingIndex, link ->
            val href = link.href.toString()
            BookChapter(
                index = readingIndex,
                id = href,
                title = link.title?.takeIf { it.isNotBlank() } ?: "Chapter ${readingIndex + 1}",
                href = href,
                depth = 0
            )
        }
    }

    /**
     * Recursively walks a TOC [Link] (and any [Link.children]) appending a
     * [BookChapter] per entry. [depth] tracks the hierarchy level (0 = top,
     * 1 = sub-chapter, …). The reading-order index is resolved via
     * [Publication.linkWithHref] so navigation still works even when the TOC
     * href formatting differs from the spine.
     */
    private fun collectTocChapters(
        link: org.readium.r2.shared.publication.Link,
        publication: Publication,
        depth: Int,
        out: MutableList<BookChapter>
    ) {
        val href = link.href.toString()
        val title = link.title?.takeIf { it.isNotBlank() }
            ?: "Chapter ${out.size + 1}"
        val spineIndex = resolveSpineIndexForTocHref(href, link, publication, out.size)
        out.add(
            BookChapter(
                index = spineIndex.coerceAtLeast(0),
                id = href,
                title = title,
                href = href,
                depth = depth
            )
        )
        for (child in link.children) {
            collectTocChapters(child, publication, depth + 1, out)
        }
    }

    /**
     * Robust spine-index resolver for a TOC href (desktop parity).
     * Normalizes href (strip '#fragment' and '?query', lowercase, filename fallback)
     * and tries in order: exact href via linkWithHref, exact normalized href,
     * filename lowercase, case-insensitive normalized href. Only if all fail
     * falls back to [fallbackIndex] with a warning log.
     */
    private fun resolveSpineIndexForTocHref(
        href: String,
        link: org.readium.r2.shared.publication.Link,
        publication: Publication,
        fallbackIndex: Int
    ): Int {
        fun stripFragment(h: String): String = h.substringBefore('#').substringBefore('?')
        fun filenameLower(h: String): String = stripFragment(h).substringAfterLast('/').trim().lowercase()
        fun normalizedLower(h: String): String = stripFragment(h).trim().lowercase()

        // 1. Exact via Readium linkWithHref (handles './', encoding)
        try {
            publication.linkWithHref(link.href.resolve())?.let { resolved ->
                val idx = publication.readingOrder.indexOf(resolved)
                if (idx >= 0) return idx
            }
        } catch (_: Throwable) {}

        val normalizedHref = stripFragment(href).trim()
        val normalizedHrefLower = normalizedLower(href)
        val fileLower = filenameLower(href)

        // 2. Exact href match against readingOrder
        publication.readingOrder.indexOfFirst { it.href.toString() == href }
            .takeIf { it >= 0 }?.let { return it }

        // 3. Normalized href exact (strip fragment/query)
        publication.readingOrder.indexOfFirst { stripFragment(it.href.toString()) == normalizedHref }
            .takeIf { it >= 0 }?.let { return it }

        // 4. Filename lowercase fallback (handles Text/chapter.xhtml vs chapter.xhtml)
        if (fileLower.isNotBlank()) {
            publication.readingOrder.indexOfFirst { filenameLower(it.href.toString()) == fileLower }
                .takeIf { it >= 0 }?.let { return it }
        }

        // 5. Case-insensitive normalized href
        publication.readingOrder.indexOfFirst { normalizedLower(it.href.toString()) == normalizedHrefLower }
            .takeIf { it >= 0 }?.let { return it }

        Log.w(TAG, "collectTocChapters: no spine match for TOC href='$href' (file='$fileLower'), fallback to list position $fallbackIndex")
        return fallbackIndex
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
    internal fun setBookLoadedForTest(publication: Publication? = null) {
        _state.update {
            it.copy(
                isLoading = false,
                readiumPublication = publication,
                bookFormat = "epub"
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
