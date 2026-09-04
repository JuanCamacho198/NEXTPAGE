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
import com.nextpage.debug.DebugLog
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.SearchResult
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.data.remote.supabase.SupabaseProgressSync
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

typealias BookChapter = com.nextpage.presentation.viewmodel.reader.BookChapter

/**
 * ReaderUiState — Aggregate UI state for the Reader screen.
 *
 * This is the single state object consumed by the Reader composables.
 * It is **not** a `data class` because [selectionRect] is an Android
 * [Rect] whose `equals()` is not available in JVM unit tests — the
 * custom [equals]/[hashCode]/[toString] skip [selectionRect] since
 * it is transient UI-positioning data that should not influence
 * state-flow deduplication.
 *
 * **Used by**: ReaderScreen
 * **Mutated by**: [ReaderViewModel] init block (merges state from
 *                 `lifecycleHolder`, `interactionHolder`, `searchStateHolder`,
 *                 `fullscreenManager`, `settingsManager`, and `sleepTimerManager`).
 *
 * Field groups (see constructor below for the full list):
 * - **Book identity & format**: [selectedBookId], [bookFilePath], [bookFormat]
 * - **Pagination**: [chapters], [currentChapterIndex], [previewText],
 *   [currentPdfPage], [totalPdfPages], [readingProgress]
 * - **User data**: [highlights], [bookmarks], [readerSettings]
 * - **Sleep timer**: [sleepTimerActive], [sleepTimerRemainingSecs], [sleepTimerFinished], [sleepTimerPresetMinutes], [sleepTimerEndOfChapterMode]
 * - **Progress**: [progressPercent], [progressLabel]
 * - **Search**: [isSearchActive], [searchQuery], [searchResults], [isSearching]
 * - **Text selection**: [selectionState], [selectedText], [selectionRect],
 *   [activeHighlightId], [highlightTapDebounceUntil], [menuJustClosedAt]
 * - **Sheets / panels**: [showHighlightsSheet], [showTocSheet], [showSplitSettings]
 * - **Fullscreen**: [isFullscreen]
 * - **Readium (EPUB)**: [readiumPublication], [readiumLocator], [readiumSelectionLocator], [readiumViewportHeight]
 * - **Anchored inputs**: [showColorPickerPopover], [showNoteModal], [activeNoteText],
 *   [showTagInput], [activeTagText], [tagSuggestions],
 *   [showDefinitionInput], [activeDefinitionText]
 * - **Load status**: [isLoading], [loadTimeMs], [error]
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

/**
 * ReaderViewModel — Owns the entire state of the Reader screen: book loading,
 * pagination, text selection, highlights, bookmarks, search, sleep timer,
 * settings, and fullscreen. Delegates focused sub-domains to dedicated
 * state holders (`lifecycleHolder`, `interactionHolder`, `searchStateHolder`,
 * `fullscreenManager`, `settingsManager`, `sleepTimerManager`) and merges
 * their state streams into a single [ReaderUiState] in `init`.
 *
 * Public actions are grouped by responsibility — see the section comments
 * throughout this file. The largest public surface is the text-selection
 * flow ([onTextSelection], [onSelectHighlightColor], [onAnnotate], etc.),
 * followed by EPUB/PDF navigation ([goToNextChapter], [goToPdfPage], etc.).
 *
 * @param application Application context (needed for the Android `ViewModel` superclass).
 * @param readerRepository Source of book data, progress, and locator storage.
 * @param readingStatsRepository Source of reading-time statistics.
 * @param updateReadingProgressUseCase Use case that persists reading progress.
 * @param readerPreferences Persistent user settings (font, theme, etc.). May be `null` in tests.
 * @param defaultBookId Book to restore progress for on construction. May be `null`.
 * @param dictionaryRepository Optional dictionary backing for the "add to dictionary" flow.
 * @param mainDispatcher Dispatcher for state-collection coroutines. Defaults to [Dispatchers.Main].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    application: Application,
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    private val readerPreferences: ReaderPreferences? = null,
    defaultBookId: String?,
    private val dictionaryRepository: DictionaryRepository? = null,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val supabaseProgressSync: SupabaseProgressSync? = null
) : AndroidViewModel(application) {
    private val mutableUiState = MutableStateFlow(
        ReaderUiState(selectedBookId = defaultBookId)
    )
    /**
     * Aggregate reader UI state consumed by the ReaderScreen.
     *
     * **Emits when**: any underlying state holder (`lifecycleHolder`,
     *                `interactionHolder`, `searchStateHolder`,
     *                `fullscreenManager`, `settingsManager`, or
     *                `sleepTimerManager`) emits a new value, or any
     *                public action mutates state directly.
     * **Initial value**: [ReaderUiState] with `selectedBookId = defaultBookId`
     *                    and `isLoading = true` (or `false` if no
     *                    `defaultBookId` was supplied).
     * **Lifecycle**: hot, lifetime-scoped to the ViewModel.
     */
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    /**
     * Sleep timer manager — exposes the timer state and controls.
     * Most callers should use the convenience functions
     * ([startSleepTimer], [cancelSleepTimer], etc.) rather than touching
     * this directly.
     */
    val sleepTimerManager = SleepTimerManager(viewModelScope)

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    /**
     * One-shot UI events for the reader (snackbars, toasts).
     *
     * **Emits when**: lifecycle or interaction holders emit a UI event
     *                (e.g. import errors, save confirmations).
     * **Backpressure**: default SharedFlow buffer; no replay.
     */
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val _navigateToLocator = MutableSharedFlow<Locator>()
    /**
     * One-shot navigation events carrying a Readium [Locator] to jump to
     * (used by search-result taps and highlight-tap navigation).
     *
     * **Emits when**: a search result is selected (EPUB path) or a
     *                highlight is tapped that has a stored locator.
     * **Backpressure**: default SharedFlow buffer; no replay.
     */
    val navigateToLocator: SharedFlow<Locator> = _navigateToLocator.asSharedFlow()

    /**
     * Pending CFI range to navigate to once the book finishes loading.
     * Set by [navigateToCfiAfterLoad] (called from NavHost before navigation),
     * consumed in [loadBook] after the lifecycle holder reports the book is ready.
     * Cleared after application or on ViewModel destruction.
     */
    @VisibleForTesting
    internal var pendingCfiAfterLoad: String? = null

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
        mainDispatcher = mainDispatcher,
        supabaseProgressSync = supabaseProgressSync
    )

    // ── Cluster B state holder ────────────────────────────────────────

    private val interactionHolder = ReaderInteractionStateHolder(
        readerRepository = readerRepository,
        dictionaryRepository = dictionaryRepository,
        scope = viewModelScope,
        onEvent = { _uiEvent.tryEmit(it) },
        mainDispatcher = mainDispatcher
    )

    /**
     * Emitted when the WebView/Readium selection should be cleared.
     *
     * **Emits when**: the lifecycle holder detects a book swap that should
     *                wipe the in-flight selection (delegated to
     *                [ReaderInteractionStateHolder]).
     * **Backpressure**: default SharedFlow buffer; no replay.
     */
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
                        isLoading = lifecycle.isLoading,
                        loadTimeMs = lifecycle.loadTimeMs,
                        error = lifecycle.error
                    )
                }
            }
        }

                // Typography -> exact reflow wiring: when fontSize/lineHeight/margins change, recompute footer remaining pages within one frame
        viewModelScope.launch(mainDispatcher) {
            settingsManager.state.collect { s ->
                val rs = s.readerSettings
                val fontSizeSp = rs.fontSize.sizePx.toFloat()
                val lineH = rs.lineHeight.value
                val marginsVal = 16f
                val density = getApplication<android.app.Application>().resources.displayMetrics.density
                lifecycleHolder.onTypographyConfigChanged(fontSizeSp, lineH, marginsVal, density)
            }
        }

// Derive the split-settings preview text from the current chapter's
        // real content (EPUB only). Refreshes when the book or chapter
        // changes; PDFs have no extractable HTML text, so `previewText`
        // falls back to blank and the UI shows the selection/title instead.
        viewModelScope.launch(mainDispatcher) {
            lifecycleHolder.state
                .map { Triple(it.bookFormat, it.readiumPublication, it.currentChapterIndex) }
                .distinctUntilChanged()
                .collect { (bookFormat, publication, chapterIndex) ->
                    val excerpt = extractChapterPreviewText(publication, bookFormat, chapterIndex)
                    mutableUiState.update { current ->
                        current.copy(previewText = excerpt ?: "")
                    }
                }
        }

        // Merge Cluster B interaction state into ReaderUiState
        viewModelScope.launch(mainDispatcher) {
            interactionHolder.state.collect { interaction ->
                DebugLog.info("ReaderVM", "merge Cluster B: highlights=${interaction.highlights.size}")
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
                        debugForceMenu = interaction.debugForceMenu
                    )
                }
            }
        }

        // Direct highlights observation — belt-and-suspenders for the merge
        // above. The Cluster B merge depends on the interaction holder's
        // StateFlow updating after observeBook() is called; when the reader
        // is reopened there is a window where the holder has the list but the
        // merge has not propagated it (the composable then renders with 0
        // highlights and the decorations are never applied). This direct
        // collect guarantees the persisted highlights reach ReaderUiState
        // regardless of merge timing.
        //
        // flatMapLatest restarts the Room highlights flow on EVERY lifecycle
        // emission of selectedBookId (including reopening the same book),
        // which the merge path misses.
        viewModelScope.launch(mainDispatcher) {
            lifecycleHolder.state
                .map { it.selectedBookId }
                .mapNotNull { it }
                .distinctUntilChanged()
                .flatMapLatest { bookId ->
                    readerRepository.observeHighlights(bookId).distinctUntilChanged()
                }
                .collect { highlights ->
                    DebugLog.info("ReaderVM", "direct highlights collect: ${highlights.size}")
                    mutableUiState.update { it.copy(highlights = highlights) }
                }
        }
    }

    // ── Book Loading ──────────────────────────────────────────────────

    /**
     * Stores a CFI range that should be navigated to once the book finishes
     * loading. Called by the NavHost before navigating to the Reader route.
     *
     * The pending CFI survives the [loadBook] call (which starts async loading)
     * and is applied in [applyPendingCfi] after the lifecycle holder reports
     * that the publication is ready.
     */
    fun navigateToCfiAfterLoad(cfiRange: String) {
        pendingCfiAfterLoad = cfiRange
        // If the book is already loaded (same-book highlight tap), apply
        // the pending CFI immediately instead of waiting for loadBook.
        val state = lifecycleHolder.state.value
        if (!state.isLoading && state.readiumPublication != null) {
            applyPendingCfi()
        }
    }

    /**
     * Loads a new book into the reader, replacing any current selection.
     *
     * Side effects:
     * 1. Clears `highlights` and `bookmarks` in [uiState].
     * 2. Enters immersive (fullscreen) reading mode so the reader opens
     *    with the chrome auto-hidden.
     * 3. Delegates to `lifecycleHolder.loadBook` — the lifecycle state holder
     *    will emit a new [ReaderUiState] with the book metadata, chapters,
     *    publication (EPUB), and `isLoading = true` until the book is ready.
     *
     * @param bookId Database id of the book to load.
     * @param filePath Absolute filesystem path to the book file.
     * @param format `"epub"` or `"pdf"`. Defaults to `"epub"`.
     */
    fun loadBook(bookId: String, filePath: String, format: String = "epub") {
        mutableUiState.update {
            it.copy(
                highlights = emptyList(),
                bookmarks = emptyList()
            )
        }
        interactionHolder.resetCoordinator()
        fullscreenManager.enterFullscreen()
        lifecycleHolder.loadBook(bookId, filePath, format)

        // When loading completes, apply any pending CFI navigation
        // (set by NavHost via navigateToCfiAfterLoad before navigating).
        viewModelScope.launch(mainDispatcher) {
            lifecycleHolder.state.first { !it.isLoading }
            applyPendingCfi()
        }
    }

    /**
     * Applies the pending CFI navigation (if any) after the book has
     * finished loading. Uses the same logic as [onHighlightSelected]:
     * - PDF: `cfiRange` is `"pdfpage:<N>"` → [goToPdfPage]
     * - EPUB: extract chapter index from CFI via `Regex("/6/(\\d+)")` → [goToChapter]
     *
     * The pending CFI is cleared after application so it does not re-fire
     * on subsequent book loads.
     */
    @VisibleForTesting
    internal fun applyPendingCfi() {
        val cfiRange = pendingCfiAfterLoad ?: return
        pendingCfiAfterLoad = null

        if (cfiRange.startsWith("pdfpage:")) {
            val page = cfiRange.removePrefix("pdfpage:").toIntOrNull()
            if (page != null) goToPdfPage(page)
        } else {
            val chapterMatch = Regex("/6/(\\d+)").find(cfiRange)
            val spineIndex = chapterMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.minus(1)
            if (spineIndex != null) {
                val chapters = lifecycleHolder.state.value.chapters
                val listPos = chapters.indexOfFirst { it.index == spineIndex }.takeIf { it >= 0 }
                if (listPos != null) goToChapter(listPos) else goToChapter(spineIndex.coerceIn(chapters.indices))
            }
        }
    }

    // ── Readium Bridge ──────────────────────────────────────────────

    /**
     * Extracts a plain-text excerpt (~600 chars) of the chapter at
     * [chapterIndex] from the current Readium [Publication], mirroring
     * [SearchStateHolder]'s extraction pattern (HTML stripped, whitespace
     * collapsed). Returns `null` for PDFs and any resource that cannot be
     * read — callers fall back to the selected text / chapter title.
     */
    private suspend fun extractChapterPreviewText(
        publication: Publication?,
        bookFormat: String?,
        chapterIndex: Int
    ): String? {
        if (publication == null || bookFormat != "epub") return null
        return withContext(Dispatchers.IO) {
            try {
                // chapterIndex is TOC list position, NOT spine index — resolve via chapters mapping
                val chapters = lifecycleHolder.state.value.chapters
                val link = if (chapterIndex in chapters.indices) {
                    val ch = chapters[chapterIndex]
                    val normFile = ch.href.substringBefore('#').substringBefore('?').substringAfterLast('/').lowercase()
                    publication.readingOrder.firstOrNull {
                        it.href.toString().substringAfterLast('/').substringBefore('#').substringBefore('?').lowercase() == normFile
                    } ?: publication.readingOrder.getOrNull(ch.index)
                    ?: publication.readingOrder.getOrNull(chapterIndex)
                } else {
                    publication.readingOrder.getOrNull(chapterIndex)
                } ?: return@withContext null
                val resource = publication.get(link) ?: return@withContext null
                val readResult = resource.read()
                val bytes = readResult.getOrNull() ?: return@withContext null
                bytes.decodeToString()
                    .replace(Regex("<[^>]*>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(CHAPTER_TEXT_LIMIT)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Notifies the ViewModel that the Readium navigator moved to [locator]
     * (e.g. user paginated, tapped a link). Persists reading progress and
     * updates [ReaderUiState.readiumLocator].
     */
    fun onReadiumLocatorChanged(locator: Locator) {
        lifecycleHolder.onReadiumLocatorChanged(locator)
    }

    /** Notifies the ViewModel that the Readium viewport dimensions changed; used for exact reflow calc. */
    fun onReadiumViewportChanged(height: Int, width: Int = 0) {
        lifecycleHolder.onReadiumViewportChanged(height, width)
    }

    /** Notifies the ViewModel that a PDF document finished loading with [pages] total pages. */
    fun onPdfDocumentLoaded(pages: Int) {
        lifecycleHolder.onPdfDocumentLoaded(pages)
    }

    // ── Search (Gap 3) ──────────────────────────────────────────────

    /**
     * Toggles the in-reader search panel visibility. Delegates to
     * [SearchStateHolder]; the resulting [ReaderUiState.isSearchActive]
     * is merged back into [uiState].
     */
    fun onToggleSearch() = searchStateHolder.onToggleSearch()

    /**
     * Updates the search query and triggers a debounced search.
     *
     * Side effects: delegates to [SearchStateHolder.onSearchQuery] which
     * debounces input and (for EPUB) uses the active [Publication] to
     * resolve matches; results land in [ReaderUiState.searchResults].
     *
     * @param query The new search text.
     */
    fun onSearchQuery(query: String) {
        val state = mutableUiState.value
        searchStateHolder.onSearchQuery(query, state.readiumPublication, state.bookFormat)
    }

    /** Clears the current search query and results without closing the search panel. */
    fun onClearSearch() = searchStateHolder.onClearSearch()

    /** Dismisses the search panel and clears any in-flight search state. */
    fun onDismissSearch() = searchStateHolder.onDismissSearch()

    /**
     * Receives search results from the native PDF layer (JSON-encoded) and
     * surfaces them as [SearchResult]s in [ReaderUiState.searchResults].
     *
     * @param json JSON payload produced by the PDF reader's search API.
     */
    fun onPdfSearchResults(json: String) = searchStateHolder.onPdfSearchResults(json)

    /**
     * Jumps the reader to the location of [result].
     *
     * Side effects:
     * 1. For EPUB: emits a [Locator] on [navigateToLocator] to scroll to the match.
     * 2. For PDF: calls [goToPdfPage] with the match's page index.
     * 3. Updates `currentChapterIndex` in [uiState] for the EPUB path.
     */
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

    /**
     * Handles a tap on an existing [highlight], opening the highlight
     * action menu anchored to [rect].
     */
    fun onHighlightTapped(highlight: Highlight, rect: RectF) =
        interactionHolder.onHighlightTapped(highlight, rect)

    /**
     * Low-level text-selection event from the WebView (coordinates + text).
     * Prefer [onTextSelection] for new code paths.
     */
    fun onTextSelectionEvent(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        interactionHolder.onTextSelectionEvent(text, left, top, right, bottom)

    /**
     * Handles a text selection in the reader: shows the selection context
     * menu anchored to [rect] and exposes the selected text via
     * [ReaderUiState.selectedText].
     */
    fun onTextSelection(text: String, rect: Rect) =
        interactionHolder.onTextSelection(text, rect)

    /**
     * Persists a new highlight with the given [color] for the active
     * selection. Uses the current locator (Readium) or page (PDF) to
     * store the position.
     */
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

    /** Copies the current [ReaderUiState.selectedText] to the system clipboard. */
    fun onCopySelectedText() = interactionHolder.onCopySelectedText()

    /** Dismisses the active selection context menu without committing an action. */
    fun onDismissContextMenu() = interactionHolder.onDismissContextMenu()

    /**
     * Handles a Readium-flavoured text selection: stores the [locator] and
     * [text] in [ReaderUiState.readiumSelectionLocator] / [selectedText]
     * and shows the context menu anchored to [rect].
     */
    fun onReadiumSelection(locator: Locator, rect: RectF, text: String) {
        interactionHolder.onReadiumSelection(
            locator = locator,
            rect = rect,
            text = text,
            existingHighlights = mutableUiState.value.highlights
        )
    }

    /**
     * Clears the in-flight text selection (both UI state and any
     * Readium-side highlight selection). Emits on [clearSelectionEvent]
     * so the WebView/Readium layer can clear its native selection.
     */
    fun onSelectionCleared() {
        interactionHolder.onSelectionCleared()
    }

    // ── Colour Picker Popover (Phase 2) ───────────────────────────

    /** Shows the color-picker popover anchored near the current selection. */
    fun onShowColorPickerPopover() = interactionHolder.onShowColorPickerPopover()

    /** Dismisses the color-picker popover. */
    fun onDismissColorPickerPopover() = interactionHolder.onDismissColorPickerPopover()

    // ── Note Modal ───────────────────────────────────────────────

    /** Opens the note modal for the active selection. */
    fun onShowNoteModal() = interactionHolder.onShowNoteModal()

    /** Dismisses the note modal without saving. */
    fun onDismissNoteModal() = interactionHolder.onDismissNoteModal()

    /**
     * Saves the note [text] attached to the current selection and dismisses
     * the note modal.
     */
    fun onSaveNote(text: String) = interactionHolder.onSaveNote(text)

    // ── Annotate (unified note/comment) ──────────────────────────

    /**
     * Persists the current selection as a free-form annotation (highlight
     * with a note, no specific category). Uses the current Readium locator
     * or PDF page to anchor the annotation.
     */
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

    /** Opens the tag input anchored near the current selection. */
    fun onShowTagInput() = interactionHolder.onShowTagInput()

    /** Dismisses the tag input. */
    fun onDismissTagInput() = interactionHolder.onDismissTagInput()

    /** Updates the in-progress tag text and refreshes tag suggestions. */
    fun onTagTextChanged(text: String) = interactionHolder.onTagTextChanged(text)

    /** Saves the current tag text against the active selection. */
    fun onSaveTag(text: String) = interactionHolder.onSaveTag(text)

    // ── Anchored Definition Input ─────────────────────────────────

    /** Opens the dictionary-definition input anchored near the current selection. */
    fun onShowDefinitionInput() = interactionHolder.onShowDefinitionInput()

    /** Dismisses the definition input. */
    fun onDismissDefinitionInput() = interactionHolder.onDismissDefinitionInput()

    /** Updates the in-progress definition text. */
    fun onDefinitionTextChanged(text: String) = interactionHolder.onDefinitionTextChanged(text)

    /** Saves the typed definition against the active selection. */
    fun onSaveDefinition(definition: String) = interactionHolder.onSaveDefinition(definition)

    /**
     * Adds the selected word (with its definition) to the user's dictionary.
     * Requires [dictionaryRepository] to be wired at construction time.
     */
    fun onAddToDictionary() = interactionHolder.onAddToDictionary()

    // ── Share ────────────────────────────────────────────────────

    /**
     * Fires a share intent for the currently selected text. Delegates to
     * the interaction holder, which routes through [uiEvent].
     */
    fun onShareSelectedText(text: String? = null) {
        val shareText = text ?: mutableUiState.value.selectedText
        interactionHolder.onShareSelectedText(shareText)
    }

    // ── Readium Highlights (Phase 3+) ──────────────────────────────

    /**
     * Persists a Readium highlight with the given [color] for the active
     * EPUB selection. Mirrors [onSelectHighlightColor] but is invoked from
     * the Readium-native highlight menu.
     */
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

    /**
     * Deletes the Readium highlight identified by [highlightId].
     */
    fun onReadiumDeleteHighlight(highlightId: String) =
        interactionHolder.onReadiumDeleteHighlight(highlightId)

    /**
     * Updates the color of an existing Readium highlight to [color].
     */
    fun onReadiumUpdateHighlightColor(highlightId: String, color: String) =
        interactionHolder.onReadiumUpdateHighlightColor(highlightId, color)

    // ── Debug: Force menu visibility ──────────────────────────────

    /** Debug-only: forces the selection context menu to open for UI testing. */
    fun onDebugForceMenu() = interactionHolder.onDebugForceMenu()

    /** Debug-only: forces the color picker popover to open for UI testing. */
    fun onDebugForceColorPicker() = interactionHolder.onDebugForceColorPicker()

    // ── Highlights Panel (Gap 5) ────────────────────────────────────

    /** Toggles the highlights panel sheet visibility. */
    fun onToggleHighlightsPanel() = interactionHolder.onToggleHighlightsPanel()

    /**
     * Toggles the table-of-contents sheet visibility.
     *
     * Delegates to the lifecycle holder so the state is owned by a single
     * StateFlow. Mutating the merged `mutableUiState` directly caused a
     * race where the next lifecycle emission would reset the sheet flag.
     */
    fun onToggleTocSheet() = lifecycleHolder.onToggleTocSheet()

    /**
     * Navigates to the position of [highlight].
     *
     * Side effects:
     * 1. For PDF highlights (`cfiRange` starts with `pdfpage:`): jumps to the
     *    stored page via [goToPdfPage].
     * 2. For EPUB highlights with a stored Readium locator: emits a
     *    [Locator] on [navigateToLocator] for precise position.
     * 3. For legacy EPUB highlights without a locator: extracts the chapter
     *    index from the CFI string and navigates to the chapter start.
     * 4. Closes the highlights sheet.
     */
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
                // Legacy CFI without a stored locator: extract spine index and
                // map to TOC list position (spine offset fix).
                val chapterMatch = Regex("/6/(\\d+)").find(cfi)
                val spineIndex = chapterMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.minus(1)
                if (spineIndex != null) {
                    val chapters = lifecycleHolder.state.value.chapters
                    val listPos = chapters.indexOfFirst { it.index == spineIndex }.takeIf { it >= 0 }
                    if (listPos != null) goToChapter(listPos) else goToChapter(spineIndex.coerceIn(chapters.indices))
                }
            }
        }
        mutableUiState.update { it.copy(showHighlightsSheet = false) }
    }

    // ── Bookmarks ─────────────────────────────────────────────────

    /**
     * Creates a bookmark at [cfiLocation] for [bookId] with the given [titleOrSnippet].
     *
     * @param bookId Database id of the book.
     * @param cfiLocation CFI string (EPUB) or `"pdfpage:<n>"` marker.
     * @param titleOrSnippet User-supplied title or snippet to label the bookmark.
     */
    fun createBookmark(bookId: String, cfiLocation: String, titleOrSnippet: String) =
        interactionHolder.createBookmark(bookId, cfiLocation, titleOrSnippet)

    /**
     * Creates a bookmark at the reader's current position using the
     * active Readium locator (EPUB) or PDF page as the anchor.
     */
    fun createBookmarkFromCurrentPosition() {
        val state = mutableUiState.value
        interactionHolder.createBookmarkFromCurrentPosition(
            selectedBookId = state.selectedBookId,
            bookFormat = state.bookFormat,
            currentPdfPage = state.currentPdfPage,
            chapters = state.chapters,
            currentChapterIndex = state.currentChapterIndex,
            readiumLocator = state.readiumLocator
        )
    }

    companion object {
        private const val TAG = "ReaderViewModel"
        private const val CHAPTER_TEXT_LIMIT = 600
    }

    // ── aA Settings ──────────────────────────────────────────────────

    /** Toggles the "aA" font/spacing settings sheet. */
    fun onToggleSplitSettings() = settingsManager.onToggleSplitSettings()

    // ── Fullscreen ───────────────────────────────────────────────────

    /** Toggles immersive fullscreen mode (hides system bars + reader chrome). */
    fun onToggleFullscreen() = fullscreenManager.onToggleFullscreen()

    // ── Custom Highlight Palette (Phase 4) ───────────────────────

    /**
     * Updates one of the user's custom highlight palette slots.
     *
     * @param index Palette slot index (0-based).
     * @param hex Hex color string (e.g. `"#FFAA00"`).
     */
    fun onUpdateCustomHighlightColor(index: Int, hex: String) =
        settingsManager.onUpdateCustomHighlightColor(index, hex)

    /** Resets the custom highlight palette to its default values. */
    fun onResetCustomHighlightColors() = settingsManager.onResetCustomHighlightColors()

    // ── Sleep Timer (delegated to SleepTimerManager) ─────────────────

    /**
     * Starts a sleep timer that will close the reader (or finish at the
     * end of the current chapter) after [minutes].
     *
     * @param minutes Duration in minutes. Pass `0` for end-of-chapter mode.
     */
    fun startSleepTimer(minutes: Int) = sleepTimerManager.startTimer(minutes)

    /** Cancels the active sleep timer. */
    fun cancelSleepTimer() = sleepTimerManager.cancel()

    /** Dismisses the sleep-timer-finished overlay without cancelling. */
    fun dismissSleepTimerOverlay() = sleepTimerManager.dismissOverlay()

    /**
     * Formats the remaining seconds as a `mm:ss` / `h:mm:ss` string.
     * @param secs Remaining seconds.
     * @return Human-readable countdown string.
     */
    fun formatSleepTimerRemaining(secs: Int): String = sleepTimerManager.formatRemaining(secs)

    // ── Reader Settings ──────────────────────────────────────────────

    /**
     * Replaces the entire [ReaderSettings] (font, size, theme, line height, etc.).
     * Use this from a settings sheet "apply" action.
     */
    fun updateReaderSettings(settings: ReaderSettings) = settingsManager.updateReaderSettings(settings)

    // ── Cluster A — Delegated to ReaderLifecycleStateHolder ─────────

    /**
     * Navigates to the next chapter in the EPUB TOC (no-op if already at the last chapter).
     */
    fun goToNextChapter() = lifecycleHolder.goToNextChapter()

    /** Navigates to the previous chapter in the EPUB TOC (no-op if already at the first chapter). */
    fun goToPreviousChapter() = lifecycleHolder.goToPreviousChapter()

    /**
     * Navigates to the chapter at [index] in the EPUB TOC (list position, 0..chapters.size-1).
     * The index is the TOC list position, NOT the spine index, to avoid the +3 offset.
     * @param index Zero-based TOC list position.
     */
    fun goToChapter(index: Int) = lifecycleHolder.goToChapter(index)

    /** Navigates to the next PDF page (no-op if already at the last page). */
    fun goToNextPdfPage() = lifecycleHolder.goToNextPdfPage()

    /** Navigates to the previous PDF page (no-op if already at the first page). */
    fun goToPreviousPdfPage() = lifecycleHolder.goToPreviousPdfPage()

    /**
     * Jumps to [pageNumber] (1-based) in the current book.
     * For EPUB this is the Readium position; for PDF it is the literal page.
     */
    fun goToPage(pageNumber: Int) = lifecycleHolder.goToPage(pageNumber)

    /**
     * Jumps to [pageIndex] (1-based) in the current PDF.
     * @param pageIndex 1-based page number.
     */
    fun goToPdfPage(pageIndex: Int) = lifecycleHolder.goToPdfPage(pageIndex)

    /**
     * Handles a tap on a screen-edge tap-zone.
     * @param isLeftZone `true` for the left third (page back), `false` for the right third (page forward).
     */
    fun onTapZone(isLeftZone: Boolean) = lifecycleHolder.onTapZone(isLeftZone)

    /**
     * Persists reading progress at [percent] (0.0–1.0) without changing the locator.
     * Used by the slider drag handler.
     */
    fun onProgressChange(percent: Float) = lifecycleHolder.onProgressChange(percent)

    /**
     * Restores the last-saved progress for [bookId] from the repository and
     * jumps the reader to that position. Called on screen open and on pull-to-refresh.
     */
    fun restoreProgressForBook(bookId: String) = lifecycleHolder.restoreProgressForBook(bookId)

    /**
     * Drives the user id stamped on recorded reading sessions
     * (REQ-reading-sessions-sync-1). Called from the NavHost session effect
     * alongside `HomeViewModel.setActiveSession`; blank keeps sessions local-only.
     */
    fun setActiveUserId(userId: String) = lifecycleHolder.setActiveUserId(userId)

    /**
     * Persists the current reading position.
     * @param bookId Database id of the book.
     * @param cfiLocation CFI string (EPUB) or `"pdfpage:<n>"` marker.
     * @param percentage Progress as a 0.0–1.0 float.
     */
    fun updateProgress(bookId: String, cfiLocation: String, percentage: Float) =
        lifecycleHolder.updateProgress(bookId, cfiLocation, percentage)

    /** Notifies the lifecycle holder that the reader screen was opened (starts the reading-time tracker). */
    fun onReaderOpened() = lifecycleHolder.onReaderOpened()

    /** Notifies the lifecycle holder that the reader screen was paused (resumes the tracker when reopened). */
    fun onReaderPaused() = lifecycleHolder.onReaderPaused()

    /**
     * Notifies the lifecycle holder that the app went to background
     * (flushes pending progress writes immediately).
     */
    fun onReaderBackgrounded() = lifecycleHolder.onReaderBackgrounded()

    override fun onCleared() {
        lifecycleHolder.onCleared()
        interactionHolder.onCleared()
        super.onCleared()
    }
}

class ReaderViewModelFactory(
    private val application: Application,
    private val readerRepository: ReaderRepository,
    private val readingStatsRepository: ReadingStatsRepository,
    private val readerPreferences: ReaderPreferences,
    private val defaultBookId: String?,
    private val dictionaryRepository: DictionaryRepository? = null,
    private val supabaseProgressSync: SupabaseProgressSync? = null
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
                dictionaryRepository = dictionaryRepository,
                supabaseProgressSync = supabaseProgressSync
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
