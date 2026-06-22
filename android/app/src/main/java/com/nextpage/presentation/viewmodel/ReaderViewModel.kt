package com.nextpage.presentation.viewmodel

import android.app.Application
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.session.ReaderPreferences
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.SearchResult
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.viewmodel.reader.FullscreenManager
import com.nextpage.presentation.viewmodel.reader.ReaderInteractionStateHolder
import com.nextpage.presentation.viewmodel.reader.ReaderLifecycleStateHolder
import com.nextpage.presentation.viewmodel.reader.ReaderSelectionState
import com.nextpage.presentation.viewmodel.reader.ReaderSettingsManager
import com.nextpage.presentation.viewmodel.reader.SearchStateHolder
import com.nextpage.presentation.viewmodel.reader.SleepTimerManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

typealias BookChapter = com.nextpage.presentation.viewmodel.reader.BookChapter

/**
 * UI state for the Reader screen.
 *
 * NOTE: Not a `data class` because [selectionRect] is an Android [Rect]
 * whose `equals()` is not available in JVM unit tests. Custom
 * [equals]/[hashCode]/[toString] skip [selectionRect] — the rect is
 * transient UI-positioning data that should not influence state-flow
 * deduplication.
 */
@Immutable
class ReaderUiState(
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
    val selectionState: ReaderSelectionState = ReaderSelectionState.None,
    val selectedText: String? = null,
    val selectionRect: Rect? = null,
    /** Managed internally by SelectionCoordinator — set to null in combine. */
    val activeHighlightId: String? = null,
    /** Managed internally by SelectionCoordinator — set to 0L in combine. */
    val highlightTapDebounceUntil: Long = 0L,
    /** Managed internally by SelectionCoordinator — set to 0L in combine. */
    val menuJustClosedAt: Long = 0L,

    // ── Highlights Panel (Gap 5) ────────────────────────────────────
    val showHighlightsSheet: Boolean = false,

    // ── Chapters / TOC sheet ────────────────────────────────────────
    val showTocSheet: Boolean = false,

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

    // ── Floating menus / anchored inputs ────────────────────────────
    val showColorPickerPopover: Boolean = false,
    val showNoteModal: Boolean = false,
    val activeNoteText: String = "",
    val showTagInput: Boolean = false,
    val activeTagText: String = "",
    val tagSuggestions: List<String> = emptyList(),
    val showDefinitionInput: Boolean = false,
    val activeDefinitionText: String = "",

    val isLoading: Boolean = true,
    val loadTimeMs: Long? = null,
    val error: String? = null
) {
    fun copy(
        selectedBookId: String? = this.selectedBookId,
        bookFilePath: String? = this.bookFilePath,
        bookFormat: String? = this.bookFormat,
        chapters: List<BookChapter> = this.chapters,
        currentChapterIndex: Int = this.currentChapterIndex,
        previewText: String = this.previewText,
        currentPdfPage: Int = this.currentPdfPage,
        totalPdfPages: Int = this.totalPdfPages,
        readingProgress: ReadingProgress? = this.readingProgress,
        highlights: List<Highlight> = this.highlights,
        bookmarks: List<Bookmark> = this.bookmarks,
        readerSettings: ReaderSettings = this.readerSettings,
        sleepTimerActive: Boolean = this.sleepTimerActive,
        sleepTimerRemainingSecs: Int = this.sleepTimerRemainingSecs,
        sleepTimerFinished: Boolean = this.sleepTimerFinished,
        sleepTimerPresetMinutes: Int? = this.sleepTimerPresetMinutes,
        sleepTimerEndOfChapterMode: Boolean = this.sleepTimerEndOfChapterMode,
        progressPercent: Float = this.progressPercent,
        progressLabel: String = this.progressLabel,
        isSearchActive: Boolean = this.isSearchActive,
        searchQuery: String = this.searchQuery,
        searchResults: List<SearchResult> = this.searchResults,
        isSearching: Boolean = this.isSearching,
        selectionState: ReaderSelectionState = this.selectionState,
        selectedText: String? = this.selectedText,
        selectionRect: Rect? = this.selectionRect,
        activeHighlightId: String? = this.activeHighlightId,
        highlightTapDebounceUntil: Long = this.highlightTapDebounceUntil,
        menuJustClosedAt: Long = this.menuJustClosedAt,
        showHighlightsSheet: Boolean = this.showHighlightsSheet,
        showTocSheet: Boolean = this.showTocSheet,
        showSplitSettings: Boolean = this.showSplitSettings,
        isFullscreen: Boolean = this.isFullscreen,
        readiumPublication: Publication? = this.readiumPublication,
        readiumLocator: Locator? = this.readiumLocator,
        readiumSelectionLocator: Locator? = this.readiumSelectionLocator,
        readiumViewportHeight: Int = this.readiumViewportHeight,
        debugForceMenu: Boolean = this.debugForceMenu,
        showColorPickerPopover: Boolean = this.showColorPickerPopover,
        showNoteModal: Boolean = this.showNoteModal,
        activeNoteText: String = this.activeNoteText,
        showTagInput: Boolean = this.showTagInput,
        activeTagText: String = this.activeTagText,
        tagSuggestions: List<String> = this.tagSuggestions,
        showDefinitionInput: Boolean = this.showDefinitionInput,
        activeDefinitionText: String = this.activeDefinitionText,
        isLoading: Boolean = this.isLoading,
        loadTimeMs: Long? = this.loadTimeMs,
        error: String? = this.error
    ): ReaderUiState {
        return ReaderUiState(
            selectedBookId = selectedBookId,
            bookFilePath = bookFilePath,
            bookFormat = bookFormat,
            chapters = chapters,
            currentChapterIndex = currentChapterIndex,
            previewText = previewText,
            currentPdfPage = currentPdfPage,
            totalPdfPages = totalPdfPages,
            readingProgress = readingProgress,
            highlights = highlights,
            bookmarks = bookmarks,
            readerSettings = readerSettings,
            sleepTimerActive = sleepTimerActive,
            sleepTimerRemainingSecs = sleepTimerRemainingSecs,
            sleepTimerFinished = sleepTimerFinished,
            sleepTimerPresetMinutes = sleepTimerPresetMinutes,
            sleepTimerEndOfChapterMode = sleepTimerEndOfChapterMode,
            progressPercent = progressPercent,
            progressLabel = progressLabel,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            searchResults = searchResults,
            isSearching = isSearching,
            selectionState = selectionState,
            selectedText = selectedText,
            selectionRect = selectionRect,
            activeHighlightId = activeHighlightId,
            highlightTapDebounceUntil = highlightTapDebounceUntil,
            menuJustClosedAt = menuJustClosedAt,
            showHighlightsSheet = showHighlightsSheet,
            showTocSheet = showTocSheet,
            showSplitSettings = showSplitSettings,
            isFullscreen = isFullscreen,
            readiumPublication = readiumPublication,
            readiumLocator = readiumLocator,
            readiumSelectionLocator = readiumSelectionLocator,
            readiumViewportHeight = readiumViewportHeight,
            debugForceMenu = debugForceMenu,
            showColorPickerPopover = showColorPickerPopover,
            showNoteModal = showNoteModal,
            activeNoteText = activeNoteText,
            showTagInput = showTagInput,
            activeTagText = activeTagText,
            tagSuggestions = tagSuggestions,
            showDefinitionInput = showDefinitionInput,
            activeDefinitionText = activeDefinitionText,
            isLoading = isLoading,
            loadTimeMs = loadTimeMs,
            error = error
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReaderUiState) return false
        return selectedBookId == other.selectedBookId &&
            bookFilePath == other.bookFilePath &&
            bookFormat == other.bookFormat &&
            chapters == other.chapters &&
            currentChapterIndex == other.currentChapterIndex &&
            previewText == other.previewText &&
            currentPdfPage == other.currentPdfPage &&
            totalPdfPages == other.totalPdfPages &&
            readingProgress == other.readingProgress &&
            highlights == other.highlights &&
            bookmarks == other.bookmarks &&
            readerSettings == other.readerSettings &&
            sleepTimerActive == other.sleepTimerActive &&
            sleepTimerRemainingSecs == other.sleepTimerRemainingSecs &&
            sleepTimerFinished == other.sleepTimerFinished &&
            sleepTimerPresetMinutes == other.sleepTimerPresetMinutes &&
            sleepTimerEndOfChapterMode == other.sleepTimerEndOfChapterMode &&
            progressPercent == other.progressPercent &&
            progressLabel == other.progressLabel &&
            isSearchActive == other.isSearchActive &&
            searchQuery == other.searchQuery &&
            searchResults == other.searchResults &&
            isSearching == other.isSearching &&
            selectionState == other.selectionState &&
            selectedText == other.selectedText &&
            activeHighlightId == other.activeHighlightId &&
            highlightTapDebounceUntil == other.highlightTapDebounceUntil &&
            menuJustClosedAt == other.menuJustClosedAt &&
            showHighlightsSheet == other.showHighlightsSheet &&
            showTocSheet == other.showTocSheet &&
            showSplitSettings == other.showSplitSettings &&
            isFullscreen == other.isFullscreen &&
            readiumPublication == other.readiumPublication &&
            readiumLocator == other.readiumLocator &&
            readiumSelectionLocator == other.readiumSelectionLocator &&
            readiumViewportHeight == other.readiumViewportHeight &&
            debugForceMenu == other.debugForceMenu &&
            showColorPickerPopover == other.showColorPickerPopover &&
            showNoteModal == other.showNoteModal &&
            activeNoteText == other.activeNoteText &&
            showTagInput == other.showTagInput &&
            activeTagText == other.activeTagText &&
            tagSuggestions == other.tagSuggestions &&
            showDefinitionInput == other.showDefinitionInput &&
            activeDefinitionText == other.activeDefinitionText &&
            isLoading == other.isLoading &&
            loadTimeMs == other.loadTimeMs &&
            error == other.error
        // selectionRect intentionally omitted — not mockable in JVM unit tests
    }

    override fun hashCode(): Int {
        var result = selectedBookId?.hashCode() ?: 0
        result = 31 * result + (bookFilePath?.hashCode() ?: 0)
        result = 31 * result + (bookFormat?.hashCode() ?: 0)
        result = 31 * result + chapters.hashCode()
        result = 31 * result + currentChapterIndex
        result = 31 * result + previewText.hashCode()
        result = 31 * result + currentPdfPage
        result = 31 * result + totalPdfPages
        result = 31 * result + (readingProgress?.hashCode() ?: 0)
        result = 31 * result + highlights.hashCode()
        result = 31 * result + bookmarks.hashCode()
        result = 31 * result + readerSettings.hashCode()
        result = 31 * result + sleepTimerActive.hashCode()
        result = 31 * result + sleepTimerRemainingSecs
        result = 31 * result + sleepTimerFinished.hashCode()
        result = 31 * result + (sleepTimerPresetMinutes ?: 0)
        result = 31 * result + sleepTimerEndOfChapterMode.hashCode()
        result = 31 * result + progressPercent.hashCode()
        result = 31 * result + progressLabel.hashCode()
        result = 31 * result + isSearchActive.hashCode()
        result = 31 * result + searchQuery.hashCode()
        result = 31 * result + searchResults.hashCode()
        result = 31 * result + isSearching.hashCode()
        result = 31 * result + selectionState.hashCode()
        result = 31 * result + (selectedText?.hashCode() ?: 0)
        result = 31 * result + (activeHighlightId?.hashCode() ?: 0)
        result = 31 * result + highlightTapDebounceUntil.hashCode()
        result = 31 * result + menuJustClosedAt.hashCode()
        result = 31 * result + showHighlightsSheet.hashCode()
        result = 31 * result + showTocSheet.hashCode()
        result = 31 * result + showSplitSettings.hashCode()
        result = 31 * result + isFullscreen.hashCode()
        result = 31 * result + (readiumPublication?.hashCode() ?: 0)
        result = 31 * result + (readiumLocator?.hashCode() ?: 0)
        result = 31 * result + (readiumSelectionLocator?.hashCode() ?: 0)
        result = 31 * result + readiumViewportHeight
        result = 31 * result + debugForceMenu.hashCode()
        result = 31 * result + showColorPickerPopover.hashCode()
        result = 31 * result + showNoteModal.hashCode()
        result = 31 * result + activeNoteText.hashCode()
        result = 31 * result + showTagInput.hashCode()
        result = 31 * result + activeTagText.hashCode()
        result = 31 * result + tagSuggestions.hashCode()
        result = 31 * result + showDefinitionInput.hashCode()
        result = 31 * result + activeDefinitionText.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (loadTimeMs?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "ReaderUiState(" +
            "selectedBookId='$selectedBookId', " +
            "bookFilePath='$bookFilePath', " +
            "bookFormat='$bookFormat', " +
            "chapters.size=${chapters.size}, " +
            "currentChapterIndex=$currentChapterIndex, " +
            "previewText='$previewText', " +
            "currentPdfPage=$currentPdfPage, " +
            "totalPdfPages=$totalPdfPages, " +
            "readingProgress=$readingProgress, " +
            "highlights.size=${highlights.size}, " +
            "bookmarks.size=${bookmarks.size}, " +
            "readerSettings=$readerSettings, " +
            "sleepTimerActive=$sleepTimerActive, " +
            "sleepTimerRemainingSecs=$sleepTimerRemainingSecs, " +
            "sleepTimerFinished=$sleepTimerFinished, " +
            "sleepTimerPresetMinutes=$sleepTimerPresetMinutes, " +
            "sleepTimerEndOfChapterMode=$sleepTimerEndOfChapterMode, " +
            "progressPercent=$progressPercent, " +
            "progressLabel='$progressLabel', " +
            "isSearchActive=$isSearchActive, " +
            "searchQuery='$searchQuery', " +
            "searchResults.size=${searchResults.size}, " +
            "isSearching=$isSearching, " +
            "selectionState=$selectionState, " +
            "selectedText='$selectedText', " +
            "selectionRect=$selectionRect, " +
            "activeHighlightId=$activeHighlightId, " +
            "highlightTapDebounceUntil=$highlightTapDebounceUntil, " +
            "menuJustClosedAt=$menuJustClosedAt, " +
            "showHighlightsSheet=$showHighlightsSheet, " +
            "showTocSheet=$showTocSheet, " +
            "showSplitSettings=$showSplitSettings, " +
            "isFullscreen=$isFullscreen, " +
            "readiumPublication=$readiumPublication, " +
            "readiumLocator=$readiumLocator, " +
            "readiumSelectionLocator=$readiumSelectionLocator, " +
            "readiumViewportHeight=$readiumViewportHeight, " +
            "debugForceMenu=$debugForceMenu, " +
            "showColorPickerPopover=$showColorPickerPopover, " +
            "showNoteModal=$showNoteModal, " +
            "activeNoteText='$activeNoteText', " +
            "showTagInput=$showTagInput, " +
            "activeTagText='$activeTagText', " +
            "tagSuggestions=$tagSuggestions, " +
            "showDefinitionInput=$showDefinitionInput, " +
            "activeDefinitionText='$activeDefinitionText', " +
            "isLoading=$isLoading, " +
            "loadTimeMs=$loadTimeMs, " +
            "error='$error'" +
            ")"
    }
}

class ReaderViewModel(
    application: Application,
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    private val readerPreferences: ReaderPreferences? = null,
    defaultBookId: String?,
    private val dictionaryRepository: DictionaryRepository? = null,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : AndroidViewModel(application) {
    private val mutableUiState = MutableStateFlow(
        ReaderUiState(selectedBookId = defaultBookId)
    )
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    val sleepTimerManager = SleepTimerManager(viewModelScope)

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val _navigateToLocator = MutableSharedFlow<Locator>()
    val navigateToLocator: SharedFlow<Locator> = _navigateToLocator.asSharedFlow()

    // ── Cluster C state holders (extracted responsibilities) ──────────

    private val searchStateHolder = SearchStateHolder(
        scope = viewModelScope,
        onNavigateToLocator = { loc -> viewModelScope.launch { _navigateToLocator.emit(loc) } },
        onGoToChapter = { this.goToChapter(it) },
        onGoToPdfPage = { this.goToPdfPage(it) }
    )
    private val fullscreenManager = FullscreenManager()
    private val settingsManager = ReaderSettingsManager(readerPreferences)

    // ── Cluster A state holder ────────────────────────────────────────

    @VisibleForTesting
    internal val lifecycleHolder = ReaderLifecycleStateHolder(
        application = application,
        readerRepository = readerRepository,
        updateReadingProgressUseCase = updateReadingProgressUseCase,
        readingStatsRepository = readingStatsRepository,
        scope = viewModelScope,
        onChapterChanged = { sleepTimerManager.onChapterChanged() },
        onErrorEvent = { _uiEvent.tryEmit(it) },
        onSelectionCleared = { interactionHolder.onSelectionClearedFromLifecycle() },
        onNavigateToLocator = { loc -> viewModelScope.launch { _navigateToLocator.emit(loc) } },
        onBookLoaded = { bookId -> interactionHolder.observeBook(bookId) },
        mainDispatcher = mainDispatcher
    )

    // ── Cluster B state holder ────────────────────────────────────────

    private val interactionHolder = ReaderInteractionStateHolder(
        readerRepository = readerRepository,
        dictionaryRepository = dictionaryRepository,
        scope = viewModelScope,
        onEvent = { _uiEvent.tryEmit(it) },
        mainDispatcher = mainDispatcher
    )

    /** Emitted when the WebView selection should be cleared. Delegated to interaction holder. */
    val clearSelectionEvent: SharedFlow<Unit> = interactionHolder.clearSelectionEvent

    init {
        // Merge Cluster C state holders into ReaderUiState
        viewModelScope.launch(mainDispatcher) {
            combine(
                searchStateHolder.state,
                fullscreenManager.state,
                settingsManager.state
            ) { search, fs, s -> Triple(search, fs, s) }
                .collect { (search, fs, s) ->
                    mutableUiState.update { current ->
                        current.copy(
                            isSearchActive = search.isSearchActive,
                            searchQuery = search.searchQuery,
                            searchResults = search.searchResults,
                            isSearching = search.isSearching,
                            isFullscreen = fs.isFullscreen,
                            readerSettings = s.readerSettings,
                            showSplitSettings = s.showSplitSettings
                        )
                    }
                }
        }

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
            lifecycleHolder.restoreProgressForBook(defaultBookId)
        } else {
            mutableUiState.update { it.copy(isLoading = false) }
        }

        // Merge Cluster A lifecycle state into ReaderUiState
        viewModelScope.launch(mainDispatcher) {
            lifecycleHolder.state.collect { lifecycle ->
                mutableUiState.update { current ->
                    current.copy(
                        selectedBookId = lifecycle.selectedBookId,
                        bookFilePath = lifecycle.bookFilePath,
                        bookFormat = lifecycle.bookFormat,
                        chapters = lifecycle.chapters,
                        currentChapterIndex = lifecycle.currentChapterIndex,
                        currentPdfPage = lifecycle.currentPdfPage,
                        totalPdfPages = lifecycle.totalPdfPages,
                        readingProgress = lifecycle.readingProgress,
                        readiumPublication = lifecycle.readiumPublication,
                        readiumLocator = lifecycle.readiumLocator,
                        readiumViewportHeight = lifecycle.readiumViewportHeight,
                        readiumSelectionLocator = lifecycle.readiumSelectionLocator,
                        progressPercent = lifecycle.progressPercent,
                        progressLabel = lifecycle.progressLabel,
                        showTocSheet = lifecycle.showTocSheet,
                        previewText = lifecycle.previewText,
                        isLoading = lifecycle.isLoading,
                        loadTimeMs = lifecycle.loadTimeMs,
                        error = lifecycle.error
                    )
                }
            }
        }

        // Merge Cluster B interaction state into ReaderUiState
        viewModelScope.launch(mainDispatcher) {
            interactionHolder.state.collect { interaction ->
                mutableUiState.update { current ->
                    current.copy(
                        highlights = interaction.highlights,
                        bookmarks = interaction.bookmarks,
                        selectionState = interaction.selectionState,
                        selectedText = interaction.selectedText,
                        selectionRect = interaction.selectionRect,
                        showColorPickerPopover = interaction.showColorPickerPopover,
                        showNoteModal = interaction.showNoteModal,
                        activeNoteText = interaction.activeNoteText,
                        showTagInput = interaction.showTagInput,
                        activeTagText = interaction.activeTagText,
                        tagSuggestions = interaction.tagSuggestions,
                        showDefinitionInput = interaction.showDefinitionInput,
                        activeDefinitionText = interaction.activeDefinitionText,
                        showHighlightsSheet = interaction.showHighlightsSheet,
                        debugForceMenu = interaction.debugForceMenu,
                        // These fields are managed internally by SelectionCoordinator
                        activeHighlightId = null,
                        highlightTapDebounceUntil = 0L,
                        menuJustClosedAt = 0L
                    )
                }
            }
        }
    }

    // ── Book Loading ──────────────────────────────────────────────────

    fun loadBook(bookId: String, filePath: String, format: String = "epub") {
        mutableUiState.update {
            it.copy(
                highlights = emptyList(),
                bookmarks = emptyList(),
                isFullscreen = false
            )
        }
        fullscreenManager.reset()
        lifecycleHolder.loadBook(bookId, filePath, format)
    }

    // ── Readium Bridge ──────────────────────────────────────────────

    fun onReadiumLocatorChanged(locator: Locator) {
        lifecycleHolder.onReadiumLocatorChanged(locator)
    }

    fun onReadiumViewportChanged(height: Int) {
        lifecycleHolder.onReadiumViewportChanged(height)
    }

    fun onPdfDocumentLoaded(pages: Int) {
        lifecycleHolder.onPdfDocumentLoaded(pages)
    }

    // ── Search (Gap 3) ──────────────────────────────────────────────

    fun onToggleSearch() = searchStateHolder.onToggleSearch()

    fun onSearchQuery(query: String) {
        val state = mutableUiState.value
        searchStateHolder.onSearchQuery(query, state.readiumPublication, state.bookFormat)
    }

    fun onClearSearch() = searchStateHolder.onClearSearch()

    fun onDismissSearch() = searchStateHolder.onDismissSearch()

    fun onPdfSearchResults(json: String) = searchStateHolder.onPdfSearchResults(json)

    fun onSearchResultSelected(result: SearchResult) {
        val state = mutableUiState.value
        searchStateHolder.onSearchResultSelected(
            result = result,
            publication = state.readiumPublication,
            bookFormat = state.bookFormat,
            currentChapterIndex = state.currentChapterIndex
        )
    }

    // ── Text Selection (Gap 4) — delegated to Cluster B ─────────────

    fun onHighlightTapped(highlight: Highlight, rect: RectF) =
        interactionHolder.onHighlightTapped(highlight, rect)

    fun onTextSelectionEvent(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        interactionHolder.onTextSelectionEvent(text, left, top, right, bottom)

    fun onTextSelection(text: String, rect: Rect) =
        interactionHolder.onTextSelection(text, rect)

    fun onSelectHighlightColor(color: String) {
        val state = mutableUiState.value
        interactionHolder.onSelectHighlightColor(
            color = color,
            selectedBookId = state.selectedBookId,
            readiumSelectionLocator = state.readiumSelectionLocator,
            selectedText = state.selectedText,
            bookFormat = state.bookFormat,
            currentPdfPage = state.currentPdfPage,
            currentChapterIndex = state.currentChapterIndex
        )
    }

    fun onCopySelectedText() = interactionHolder.onCopySelectedText()

    fun onDismissContextMenu() = interactionHolder.onDismissContextMenu()

    fun onReadiumSelection(locator: Locator, rect: RectF, text: String) {
        val state = mutableUiState.value
        interactionHolder.onReadiumSelection(
            locator, rect, text,
            existingHighlights = state.highlights,
            currentActiveHighlightId = state.activeHighlightId,
            currentHighlightTapDebounceUntil = state.highlightTapDebounceUntil,
            currentMenuJustClosedAt = state.menuJustClosedAt
        )
    }

    fun onSelectionCleared() {
        interactionHolder.onSelectionCleared()
    }

    // ── Colour Picker Popover (Phase 2) ───────────────────────────

    fun onShowColorPickerPopover() = interactionHolder.onShowColorPickerPopover()

    fun onDismissColorPickerPopover() = interactionHolder.onDismissColorPickerPopover()

    // ── Note Modal ───────────────────────────────────────────────

    fun onShowNoteModal() = interactionHolder.onShowNoteModal()

    fun onDismissNoteModal() = interactionHolder.onDismissNoteModal()

    fun onSaveNote(text: String) = interactionHolder.onSaveNote(text)

    // ── Annotate (unified note/comment) ──────────────────────────

    fun onAnnotate() {
        val state = mutableUiState.value
        interactionHolder.onAnnotate(
            selectedBookId = state.selectedBookId,
            bookFormat = state.bookFormat,
            currentChapterIndex = state.currentChapterIndex,
            currentPdfPage = state.currentPdfPage,
            chapters = state.chapters
        )
    }

    // ── Anchored Tag Input ───────────────────────────────────────

    fun onShowTagInput() = interactionHolder.onShowTagInput()

    fun onDismissTagInput() = interactionHolder.onDismissTagInput()

    fun onTagTextChanged(text: String) = interactionHolder.onTagTextChanged(text)

    fun onSaveTag(text: String) = interactionHolder.onSaveTag(text)

    // ── Anchored Definition Input ─────────────────────────────────

    fun onShowDefinitionInput() = interactionHolder.onShowDefinitionInput()

    fun onDismissDefinitionInput() = interactionHolder.onDismissDefinitionInput()

    fun onDefinitionTextChanged(text: String) = interactionHolder.onDefinitionTextChanged(text)

    fun onSaveDefinition(definition: String) = interactionHolder.onSaveDefinition(definition)

    fun onAddToDictionary() = interactionHolder.onAddToDictionary()

    // ── Share ────────────────────────────────────────────────────

    fun onShareSelectedText() {
        interactionHolder.onShareSelectedText(mutableUiState.value.selectedText)
    }

    // ── Readium Highlights (Phase 3+) ──────────────────────────────

    fun onReadiumHighlightColorSelected(color: String) {
        val state = mutableUiState.value
        interactionHolder.onReadiumHighlightColorSelected(
            color = color,
            selectedBookId = state.selectedBookId,
            readiumSelectionLocator = state.readiumSelectionLocator,
            selectedText = state.selectedText,
            bookFormat = state.bookFormat,
            currentPdfPage = state.currentPdfPage,
            currentChapterIndex = state.currentChapterIndex
        )
    }

    fun onReadiumDeleteHighlight(highlightId: String) =
        interactionHolder.onReadiumDeleteHighlight(highlightId)

    fun onReadiumUpdateHighlightColor(highlightId: String, color: String) =
        interactionHolder.onReadiumUpdateHighlightColor(highlightId, color)

    // ── Debug: Force menu visibility ──────────────────────────────

    fun onDebugForceMenu() = interactionHolder.onDebugForceMenu()

    fun onDebugForceColorPicker() = interactionHolder.onDebugForceColorPicker()

    // ── Highlights Panel (Gap 5) ────────────────────────────────────

    fun onToggleHighlightsPanel() = interactionHolder.onToggleHighlightsPanel()

    fun onToggleTocSheet() {
        mutableUiState.update {
            it.copy(showTocSheet = !it.showTocSheet)
        }
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
                viewModelScope.launch(mainDispatcher) {
                    _navigateToLocator.emit(locator)
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
        mutableUiState.update { it.copy(showHighlightsSheet = false) }
    }

    // ── Bookmarks ─────────────────────────────────────────────────

    fun createBookmark(bookId: String, cfiLocation: String, titleOrSnippet: String) =
        interactionHolder.createBookmark(bookId, cfiLocation, titleOrSnippet)

    fun createBookmarkFromCurrentPosition() {
        val state = mutableUiState.value
        interactionHolder.createBookmarkFromCurrentPosition(
            selectedBookId = state.selectedBookId,
            bookFormat = state.bookFormat,
            currentPdfPage = state.currentPdfPage,
            chapters = state.chapters,
            currentChapterIndex = state.currentChapterIndex
        )
    }

    companion object {
        private const val TAG = "ReaderViewModel"
    }

    // ── aA Settings ──────────────────────────────────────────────────

    fun onToggleSplitSettings() = settingsManager.onToggleSplitSettings()

    // ── Fullscreen ───────────────────────────────────────────────────

    fun onToggleFullscreen() = fullscreenManager.onToggleFullscreen()

    // ── Custom Highlight Palette (Phase 4) ───────────────────────

    fun onUpdateCustomHighlightColor(index: Int, hex: String) =
        settingsManager.onUpdateCustomHighlightColor(index, hex)

    fun onResetCustomHighlightColors() = settingsManager.onResetCustomHighlightColors()

    // ── Sleep Timer (delegated to SleepTimerManager) ─────────────────

    fun startSleepTimer(minutes: Int) = sleepTimerManager.startTimer(minutes)

    fun cancelSleepTimer() = sleepTimerManager.cancel()

    fun dismissSleepTimerOverlay() = sleepTimerManager.dismissOverlay()

    fun formatSleepTimerRemaining(secs: Int): String = sleepTimerManager.formatRemaining(secs)

    // ── Reader Settings ──────────────────────────────────────────────

    fun updateReaderSettings(settings: ReaderSettings) = settingsManager.updateReaderSettings(settings)

    // ── Cluster A — Delegated to ReaderLifecycleStateHolder ─────────

    fun goToNextChapter() = lifecycleHolder.goToNextChapter()

    fun goToPreviousChapter() = lifecycleHolder.goToPreviousChapter()

    fun goToChapter(index: Int) = lifecycleHolder.goToChapter(index)

    fun goToNextPdfPage() = lifecycleHolder.goToNextPdfPage()

    fun goToPreviousPdfPage() = lifecycleHolder.goToPreviousPdfPage()

    fun goToPage(pageNumber: Int) = lifecycleHolder.goToPage(pageNumber)

    fun goToPdfPage(pageIndex: Int) = lifecycleHolder.goToPdfPage(pageIndex)

    fun onTapZone(isLeftZone: Boolean) = lifecycleHolder.onTapZone(isLeftZone)

    fun onProgressChange(percent: Float) = lifecycleHolder.onProgressChange(percent)

    fun restoreProgressForBook(bookId: String) = lifecycleHolder.restoreProgressForBook(bookId)

    fun updateProgress(bookId: String, cfiLocation: String, percentage: Float) =
        lifecycleHolder.updateProgress(bookId, cfiLocation, percentage)

    fun onReaderOpened() = lifecycleHolder.onReaderOpened()

    fun onReaderPaused() = lifecycleHolder.onReaderPaused()

    fun onReaderBackgrounded() = lifecycleHolder.onReaderBackgrounded()

    override fun onCleared() {
        lifecycleHolder.onCleared()
        super.onCleared()
    }
}

class ReaderViewModelFactory(
    private val application: Application,
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val readerPreferences: ReaderPreferences,
    private val defaultBookId: String?,
    private val dictionaryRepository: DictionaryRepository? = null
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
                defaultBookId = defaultBookId,
                dictionaryRepository = dictionaryRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
