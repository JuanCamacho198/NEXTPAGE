package com.nextpage.presentation.viewmodel.reader

import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.VisibleForTesting
import android.os.SystemClock
import android.util.Log
import com.nextpage.debug.DebugLog
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.viewmodel.CfiMigrator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import java.util.UUID

/**
 * State holder that encapsulates ALL reader interaction responsibilities:
 * selection, highlights CRUD, annotations, bookmarks, share/copy, color
 * picker, and debug overlays.
 *
 * Owns a [ReaderInteractionState] exposed as [state] and a
 * [SelectionCoordinator] state machine for coordinating the selection and
 * context menu lifecycle.
 *
 * @param readerRepository Repository for persistence of highlights and bookmarks
 * @param dictionaryRepository Optional repository for adding words to personal dictionary
 * @param scope CoroutineScope for all async work (e.g. viewModelScope)
 * @param onEvent Callback for UI-side effects (snackbar, toast, share intent)
 * @param mainDispatcher Dispatcher for UI-side state updates (default: Dispatchers.Main)
 */
class ReaderInteractionStateHolder(
    private val readerRepository: ReaderRepository,
    private val dictionaryRepository: DictionaryRepository?,
    private val scope: CoroutineScope,
    private val onEvent: (UiEvent) -> Unit,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val _state = MutableStateFlow(ReaderInteractionState())
    val state: StateFlow<ReaderInteractionState> = _state.asStateFlow()

    private var coordinator: SelectionCoordinator = SelectionCoordinator.Idle

    private val _clearSelectionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearSelectionEvent: SharedFlow<Unit> = _clearSelectionEvent.asSharedFlow()

    private var observeHighlightsJob: Job? = null
    private var observeBookmarksJob: Job? = null

    companion object {
        private const val TAG = "ReaderInteractionStateHolder"

        private val DEFAULT_TAG_SUGGESTIONS = listOf(
            "cita", "pasaje", "idea", "ficción", "no-ficción", "favoritos"
        )
    }

    // ──────────────────────────────────────────────────────────────
    // Testing support
    // ──────────────────────────────────────────────────────────────

    /** Replaces internal state highlights/bookmarks for test injection.
     *  Callers must advance the dispatcher after this call. */
    @VisibleForTesting
    internal fun testSetInitialHighlights(highlights: List<Highlight>) {
        _state.update { it.copy(highlights = highlights) }
    }

    @VisibleForTesting
    internal fun testStopObserving() {
        observeHighlightsJob?.cancel()
        observeBookmarksJob?.cancel()
    }

    // ──────────────────────────────────────────────────────────────
    // Book observation
    // ──────────────────────────────────────────────────────────────

    /**
     * Starts observing highlights and bookmarks flows for the given book.
     * Cancels any previous observation jobs first.
     */
    fun observeBook(bookId: String) {
        observeHighlightsJob?.cancel()
        observeHighlightsJob = scope.launch(mainDispatcher) {
            readerRepository.observeHighlights(bookId).collect { highlights ->
                _state.update { it.copy(highlights = highlights) }
            }
        }

        observeBookmarksJob?.cancel()
        observeBookmarksJob = scope.launch(mainDispatcher) {
            readerRepository.observeBookmarks(bookId).collect { bookmarks ->
                _state.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Selection pipeline
    // ──────────────────────────────────────────────────────────────

    /**
     * Called by [ReadiumReaderContent] when the [SelectableNavigator]
     * returns a non-null [Selection]. Uses the [SelectionCoordinator] to
     * determine how to handle the event based on the current state of
     * the selection/menu lifecycle.
     */
    fun onReadiumSelection(
        locator: Locator,
        rect: RectF,
        text: String,
        existingHighlights: List<Highlight>,
        currentActiveHighlightId: String?,
        currentHighlightTapDebounceUntil: Long,
        currentMenuJustClosedAt: Long
    ) {
        Log.d("SelectionDebug", "VM.onReadiumSelection: text='${text.take(50)}', " +
            "rect=[${rect.left},${rect.top},${rect.right},${rect.bottom}], " +
            "locator.href=${locator.href}")

        val now = SystemClock.elapsedRealtime()

        when (val current = coordinator) {
            is SelectionCoordinator.MenuClosed -> {
                if (now - current.closedAt < MENU_CLOSE_IGNORE_MS) {
                    Log.d("SelectionDebug", "Ignoring selection after menu close (${now - current.closedAt}ms ago)")
                    return
                }
                // Debounce expired — reset and fall through to handle as new selection
                coordinator = SelectionCoordinator.Idle
            }
            is SelectionCoordinator.ExistingHighlight -> {
                if (now < current.debounceUntil) {
                    Log.d("SelectionDebug", "Ignoring selection during highlight-tap debounce")
                    DebugLog.warn(TAG, "onReadiumSelection IGNORED (debounce active until=${current.debounceUntil}, now=$now)")
                    return
                }
                // Debounce expired — fall through
            }
            is SelectionCoordinator.Idle,
            is SelectionCoordinator.NewSelection -> {
                // Proceed with normal handling
            }
        }

        // Check if the current selection sits inside an existing highlight.
        val matchingHighlight = existingHighlights.firstOrNull { highlight ->
            highlight.textContent.isNotBlank() &&
                (text == highlight.textContent ||
                    text.contains(highlight.textContent) ||
                    highlight.textContent.contains(text))
        }
        if (matchingHighlight != null && currentActiveHighlightId == matchingHighlight.id) {
            DebugLog.info(TAG, "Selection inside existing highlight: id=${matchingHighlight.id}, opening FaPN3")
            onHighlightTapped(matchingHighlight, rect)
            return
        }

        val selectionRect = try {
            Rect(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
        } catch (e: Throwable) {
            Log.e("SelectionDebug", "Rect creation THREW: ${e::class.simpleName}: ${e.message}", e)
            Rect(0, 0, 100, 50) // Safe fallback rect
        }

        try {
            val normalizedText = text.trim().replace(Regex("\\s+"), " ")
            coordinator = SelectionCoordinator.NewSelection(normalizedText, selectionRect, locator)
            _state.update {
                it.copy(
                    selectionState = ReaderSelectionState.New(
                        rect = selectionRect,
                        text = normalizedText,
                        locator = locator
                    ),
                    selectedText = normalizedText,
                    selectionRect = selectionRect
                )
            }
            DebugLog.info(TAG, "onReadiumSelection: selectionState=New")
            Log.d("SelectionDebug", "VM.onReadiumSelection state update OK")
        } catch (e: Throwable) {
            Log.e("SelectionDebug", "VM.onReadiumSelection state update THREW: ${e::class.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Called by [ReadiumReaderContent] when selection is cleared.
     * Uses the [SelectionCoordinator] to respect debounce windows.
     */
    fun onSelectionCleared() {
        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        }
        val debounceUntil = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.debounceUntil
            else -> 0L
        }
        onSelectionCleared(activeId, debounceUntil)
    }

    /**
     * Overload that allows overriding the debounce parameters (used by
     * [ReaderLifecycleStateHolder] bridge and [onSelectionCleared()] itself).
     */
    fun onSelectionCleared(
        currentActiveHighlightId: String?,
        currentHighlightTapDebounceUntil: Long
    ) {
        Log.d("SelectionDebug", "VM.onSelectionCleared — resetting selection state")
        try {
            val now = SystemClock.elapsedRealtime()
            if (now < currentHighlightTapDebounceUntil && currentActiveHighlightId != null) {
                Log.d("SelectionDebug", "Ignoring selection-clear during highlight-tap debounce")
                DebugLog.warn(TAG, "onSelectionCleared IGNORED (debounce active until=$currentHighlightTapDebounceUntil, now=$now)")
                return
            }
            DebugLog.info(TAG, "onSelectionCleared (debounce not active)")
            coordinator = SelectionCoordinator.Idle
            _state.update {
                it.copy(
                    selectionState = ReaderSelectionState.None,
                    selectedText = null,
                    selectionRect = null,
                    showColorPickerPopover = false,
                    showNoteModal = false,
                    showTagInput = false,
                    showDefinitionInput = false,
                    activeNoteText = "",
                    activeTagText = "",
                    activeDefinitionText = "",
                    tagSuggestions = emptyList(),
                    debugForceMenu = false
                )
            }
        } catch (e: Throwable) {
            Log.e("SelectionDebug", "VM.onSelectionCleared THREW: ${e::class.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Called from the reader content composable with raw selection coordinates.
     * Delegates to [onTextSelection] for state update.
     */
    fun onTextSelectionEvent(text: String, left: Float, top: Float, right: Float, bottom: Float) {
        onTextSelection(text, Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
    }

    fun onTextSelection(text: String, rect: Rect) {
        Log.d("ReaderVM", "onTextSelection: \"${text.take(50)}\" rect=$rect")
        coordinator = SelectionCoordinator.NewSelection(text, rect, null)
        _state.update {
            it.copy(
                selectionState = ReaderSelectionState.New(rect, text, null),
                selectedText = text,
                selectionRect = rect
            )
        }
    }

    /**
     * Called by [ReadiumReaderContent] when the user taps an existing
     * highlight decoration. Opens the [FloatingContextMenu] anchored to the
     * highlight rect, so the user can change color, copy, or delete it.
     */
    fun onHighlightTapped(highlight: Highlight, rect: RectF) {
        DebugLog.info(TAG, "onHighlightTapped id=${highlight.id} t=${SystemClock.elapsedRealtime()}")
        val selectionRect = Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
        coordinator = SelectionCoordinator.ExistingHighlight(
            highlight = highlight,
            rect = selectionRect,
            debounceUntil = SystemClock.elapsedRealtime() + HIGHLIGHT_TAP_DEBOUNCE_MS
        )
        DebugLog.info(TAG, "Highlight tapped: id=${highlight.id}, rect=[${selectionRect.left},${selectionRect.top},${selectionRect.right},${selectionRect.bottom}]")
        _state.update {
            it.copy(
                selectionState = ReaderSelectionState.Existing(highlight, selectionRect),
                selectedText = highlight.textContent,
                selectionRect = selectionRect
            )
        }
        DebugLog.info(TAG, "onHighlightTapped: debounce until set, selectionState=Existing")
    }

    // ──────────────────────────────────────────────────────────────
    // Highlights CRUD
    // ──────────────────────────────────────────────────────────────

    /**
     * Creates a new highlight and persists it via the repository.
     */
    fun createHighlight(
        bookId: String,
        cfiRange: String,
        textContent: String,
        note: String? = null,
        color: String = HighlightColor.YELLOW.hex,
        locatorJson: String? = null
    ) {
        scope.launch(mainDispatcher) {
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

    /**
     * Called when the user picks a highlight colour for the current
     * Readium selection.
     */
    fun onReadiumHighlightColorSelected(
        color: String,
        selectedBookId: String?,
        readiumSelectionLocator: Locator?,
        selectedText: String?,
        bookFormat: String?,
        currentPdfPage: Int,
        currentChapterIndex: Int
    ) {
        val bookId = selectedBookId ?: return
        val locator = readiumSelectionLocator ?: return
        val text = selectedText ?: return

        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        }

        DebugLog.info(TAG, "Color selected: $color for id=${activeId ?: "<new>"}")

        if (activeId != null) {
            onReadiumUpdateHighlightColor(activeId, color)
            dismissMenuAndClearSelection()
            return
        }

        val locatorJson = CfiMigrator.locatorToJson(locator)
        createHighlight(
            bookId = bookId,
            cfiRange = "readium:${locator.href}",
            textContent = text,
            color = color,
            locatorJson = locatorJson
        )

        DebugLog.info(TAG, "Color selected: $color, menu closed")
        dismissMenuAndClearSelection()
    }

    /**
     * Soft-deletes a highlight by setting [Highlight.deletedAtEpochMillis].
     */
    fun onReadiumDeleteHighlight(highlightId: String) {
        val existing = _state.value.highlights.find { it.id == highlightId } ?: return
        val updated = existing.copy(
            deletedAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        scope.launch(mainDispatcher) {
            readerRepository.upsertHighlight(updated)
        }
        dismissMenuAndClearSelection()
    }

    /**
     * Updates the colour of an existing highlight in-place.
     */
    fun onReadiumUpdateHighlightColor(highlightId: String, color: String) {
        val existing = _state.value.highlights.find { it.id == highlightId } ?: return
        val updated = existing.copy(
            color = color,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        scope.launch(mainDispatcher) {
            readerRepository.upsertHighlight(updated)
        }
    }

    /**
     * Handles color selection from the palette. If an existing highlight
     * is active, updates its color. Otherwise, creates a new highlight.
     */
    fun onSelectHighlightColor(
        color: String,
        selectedBookId: String?,
        readiumSelectionLocator: Locator?,
        selectedText: String?,
        bookFormat: String?,
        currentPdfPage: Int,
        currentChapterIndex: Int
    ) {
        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        }

        if (activeId != null) {
            onReadiumUpdateHighlightColor(activeId, color)
        } else {
            val bookId = selectedBookId ?: return
            val text = selectedText ?: return
            val cfiRange = if (bookFormat == "pdf") {
                "pdfpage:$currentPdfPage"
            } else {
                "epubcfi(/6/${currentChapterIndex + 1})"
            }
            createHighlight(
                bookId = bookId,
                cfiRange = cfiRange,
                textContent = text,
                color = color
            )
        }

        // After picking a color, clear selection state so the menu doesn't
        // reappear over the new/edited highlight.
        dismissMenuAndClearSelection()
    }

    // ──────────────────────────────────────────────────────────────
    // Annotations
    // ──────────────────────────────────────────────────────────────

    /** Opens the note modal. Pre-fills text from the active highlight's
     *  [Highlight.note] if one exists. No-op when no highlight is active. */
    fun onShowNoteModal() {
        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        } ?: return
        val existingText = _state.value.highlights.find { it.id == activeId }?.note ?: ""
        _state.update {
            it.copy(
                showNoteModal = true,
                activeNoteText = existingText,
                showTagInput = false,
                showDefinitionInput = false
            )
        }
    }

    fun onDismissNoteModal() {
        _state.update { it.copy(showNoteModal = false, activeNoteText = "") }
    }

    /** Persists the note to the active highlight and dismisses the modal. */
    fun onSaveNote(text: String) {
        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        } ?: return
        val existing = _state.value.highlights.find { it.id == activeId } ?: return
        val updated = existing.copy(
            note = text.ifBlank { null },
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        scope.launch(mainDispatcher) {
            readerRepository.upsertHighlight(updated)
        }
        coordinator = SelectionCoordinator.MenuClosed()
        _state.update {
            it.copy(
                selectionState = ReaderSelectionState.None,
                showNoteModal = false,
                activeNoteText = "",
                selectedText = null,
                selectionRect = null
            )
        }
    }

    /** Handles the unified "Anotar" action.
     *
     * For an existing highlight it opens the note modal. For a new text
     * selection it first creates a default highlight and then opens the modal.
     */
    fun onAnnotate(
        selectedBookId: String?,
        bookFormat: String?,
        currentChapterIndex: Int,
        currentPdfPage: Int,
        chapters: List<BookChapter>
    ) {
        val selection = _state.value.selectionState
        when (selection) {
            is ReaderSelectionState.Existing -> onShowNoteModal()
            is ReaderSelectionState.New -> {
                val bookId = selectedBookId ?: return
                val text = selection.text
                val locatorJson = selection.locator?.let { CfiMigrator.locatorToJson(it) }
                val cfiRange = if (bookFormat == "pdf") {
                    "pdfpage:$currentPdfPage"
                } else {
                    "readium:${selection.locator?.href ?: "epubcfi(/6/${currentChapterIndex + 1})"}"
                }
                val newId = UUID.randomUUID().toString()
                val highlight = Highlight(
                    id = newId,
                    bookId = bookId,
                    cfiRange = cfiRange,
                    textContent = text,
                    note = null,
                    color = HighlightColor.YELLOW.hex,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    deletedAtEpochMillis = null,
                    locatorJson = locatorJson
                )
                scope.launch(mainDispatcher) {
                    readerRepository.upsertHighlight(highlight)
                }
                coordinator = SelectionCoordinator.ExistingHighlight(
                    highlight = highlight,
                    rect = selection.rect,
                    debounceUntil = SystemClock.elapsedRealtime() + HIGHLIGHT_TAP_DEBOUNCE_MS
                )
                _state.update {
                    it.copy(
                        selectionState = ReaderSelectionState.Existing(highlight, selection.rect),
                        selectedText = text,
                        selectionRect = selection.rect
                    )
                }
                onShowNoteModal()
            }
            else -> {}
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Tag Input
    // ──────────────────────────────────────────────────────────────

    fun onShowTagInput() {
        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        } ?: return
        val existingTag = _state.value.highlights.find { it.id == activeId }?.tag ?: ""
        val existingTags = _state.value.highlights
            .mapNotNull { it.tag }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val suggestions = (DEFAULT_TAG_SUGGESTIONS + existingTags)
            .distinct()
            .filter { it != existingTag }
        _state.update {
            it.copy(
                showTagInput = true,
                activeTagText = existingTag,
                tagSuggestions = suggestions,
                showNoteModal = false,
                showDefinitionInput = false
            )
        }
    }

    fun onDismissTagInput() {
        _state.update { it.copy(showTagInput = false, activeTagText = "", tagSuggestions = emptyList()) }
    }

    fun onTagTextChanged(text: String) {
        _state.update { it.copy(activeTagText = text) }
    }

    /** Persists the tag to the active highlight (null if empty) and dismisses. */
    fun onSaveTag(text: String) {
        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        } ?: return
        val existing = _state.value.highlights.find { it.id == activeId } ?: return
        val updated = existing.copy(
            tag = text.ifBlank { null },
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        scope.launch(mainDispatcher) {
            readerRepository.upsertHighlight(updated)
        }
        dismissMenuAndClearSelection()
    }

    // ──────────────────────────────────────────────────────────────
    // Definition Input
    // ──────────────────────────────────────────────────────────────

    fun onShowDefinitionInput() {
        _state.update {
            it.copy(
                showDefinitionInput = true,
                activeDefinitionText = "",
                showNoteModal = false,
                showTagInput = false
            )
        }
    }

    fun onDismissDefinitionInput() {
        _state.update { it.copy(showDefinitionInput = false, activeDefinitionText = "") }
    }

    fun onDefinitionTextChanged(text: String) {
        _state.update { it.copy(activeDefinitionText = text) }
    }

    fun onSaveDefinition(definition: String) {
        val repo = dictionaryRepository ?: return
        val text = _state.value.selectedText?.trim()
        if (text.isNullOrBlank()) return

        val trimmedDefinition = definition.trim().takeIf { it.isNotBlank() }
        scope.launch(mainDispatcher) {
            if (repo.exists(text)) {
                onEvent(UiEvent.ShowSnackbar("Already in dictionary"))
            } else {
                repo.save(text, trimmedDefinition).fold(
                    onSuccess = {
                        onEvent(UiEvent.ShowSnackbar("Added to dictionary"))
                    },
                    onFailure = { e ->
                        onEvent(UiEvent.ShowSnackbar(
                            e.message ?: "Failed to add to dictionary"
                        ))
                    }
                )
            }
        }
        dismissMenuAndClearSelection()
    }

    /** Opens the anchored definition input so the user can save the selected
     *  word with an optional definition. */
    fun onAddToDictionary() {
        onShowDefinitionInput()
    }

    // ──────────────────────────────────────────────────────────────
    // Share / Copy
    // ──────────────────────────────────────────────────────────────

    /** Emits a [UiEvent] that the ReaderScreen will use to launch
     *  [android.content.Intent.ACTION_SEND] with the selected text. */
    fun onShareSelectedText(selectedText: String?) {
        val text = selectedText
        if (text.isNullOrBlank()) {
            onEvent(UiEvent.ShowToast("No text selected"))
            return
        }
        onEvent(UiEvent.ShareText(text))
        dismissMenuAndClearSelection()
    }

    fun onCopySelectedText() {
        if (_state.value.selectedText == null) return
        DebugLog.info(TAG, "Copy selected text")
        onEvent(UiEvent.ShowSnackbar("Copiado al portapapeles"))
        dismissMenuAndClearSelection()
    }

    // ──────────────────────────────────────────────────────────────
    // Color Picker Popover
    // ──────────────────────────────────────────────────────────────

    fun onShowColorPickerPopover() {
        _state.update { it.copy(showColorPickerPopover = true) }
    }

    fun onDismissColorPickerPopover() {
        _state.update { it.copy(showColorPickerPopover = false) }
    }

    // ──────────────────────────────────────────────────────────────
    // Panel Toggles
    // ──────────────────────────────────────────────────────────────

    fun onToggleHighlightsPanel() {
        _state.update { it.copy(showHighlightsSheet = !it.showHighlightsSheet) }
    }

    // ──────────────────────────────────────────────────────────────
    // Bookmarks
    // ──────────────────────────────────────────────────────────────

    fun createBookmark(bookId: String, cfiLocation: String, titleOrSnippet: String) {
        scope.launch(mainDispatcher) {
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

    fun createBookmarkFromCurrentPosition(
        selectedBookId: String?,
        bookFormat: String?,
        currentPdfPage: Int,
        chapters: List<BookChapter>,
        currentChapterIndex: Int
    ) {
        val bookId = selectedBookId ?: return
        val format = bookFormat

        when (format) {
            "pdf" -> {
                val cfiLocation = "pdfpage:$currentPdfPage"
                val titleOrSnippet = "Page ${currentPdfPage + 1}"
                createBookmark(bookId, cfiLocation, titleOrSnippet)
            }
            else -> {
                val chapter = chapters.getOrNull(currentChapterIndex) ?: return
                val cfiLocation = "epubcfi(/6/${currentChapterIndex + 1})"
                val titleOrSnippet = "Chapter ${currentChapterIndex + 1}: ${chapter.title}"
                createBookmark(bookId, cfiLocation, titleOrSnippet)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Dismiss Context Menu
    // ──────────────────────────────────────────────────────────────

    fun onDismissContextMenu() {
        DebugLog.info(TAG, "Menu dismissed")
        dismissMenuAndClearSelection()
    }

    // ──────────────────────────────────────────────────────────────
    // Debug
    // ──────────────────────────────────────────────────────────────

    fun onDebugForceMenu() {
        val current = _state.value.debugForceMenu
        if (current) {
            onSelectionCleared(
                currentActiveHighlightId = null,
                currentHighlightTapDebounceUntil = 0L
            )
            return
        }
        val rect = Rect(200, 200, 600, 250)
        val highlight = Highlight(
            id = "debug-highlight",
            bookId = "debug-book",
            cfiRange = "epubcfi(/6/1)",
            textContent = "Texto de prueba debug",
            note = null,
            color = HighlightColor.YELLOW.hex,
            updatedAtEpochMillis = System.currentTimeMillis(),
            deletedAtEpochMillis = null
        )
        coordinator = SelectionCoordinator.ExistingHighlight(
            highlight = highlight,
            rect = rect,
            debounceUntil = SystemClock.elapsedRealtime() + HIGHLIGHT_TAP_DEBOUNCE_MS
        )
        _state.update {
            it.copy(
                selectedText = "Texto de prueba debug",
                selectionRect = rect,
                selectionState = ReaderSelectionState.Existing(
                    highlight,
                    rect
                ),
                debugForceMenu = true
            )
        }
    }

    fun onDebugForceColorPicker() {
        val current = _state.value.debugForceMenu
        if (current) {
            onSelectionCleared(
                currentActiveHighlightId = null,
                currentHighlightTapDebounceUntil = 0L
            )
            return
        }
        val rect = Rect(200, 200, 600, 250)
        coordinator = SelectionCoordinator.NewSelection("Texto de prueba debug", rect, null)
        _state.update {
            it.copy(
                selectedText = "Texto de prueba debug",
                selectionRect = rect,
                selectionState = ReaderSelectionState.New(
                    rect = rect,
                    text = "Texto de prueba debug",
                    locator = null
                ),
                debugForceMenu = true
            )
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Lifecycle bridge
    // ──────────────────────────────────────────────────────────────

    /**
     * Bridge method called from [ReaderLifecycleStateHolder.onSelectionCleared]
     * when the page changes. Derives the debounce state from the current
     * [SelectionCoordinator] and delegates to [onSelectionCleared].
     */
    fun onSelectionClearedFromLifecycle() {
        val activeId = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.activeHighlightId
            else -> null
        }
        val debounceUntil = when (val c = coordinator) {
            is SelectionCoordinator.ExistingHighlight -> c.debounceUntil
            else -> 0L
        }
        onSelectionCleared(activeId, debounceUntil)
    }

    // ──────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Dismisses the floating context menu and clears all selection state.
     * Sets coordinator to [SelectionCoordinator.MenuClosed] and emits a
     * clear event on [clearSelectionEvent].
     */
    private fun dismissMenuAndClearSelection() {
        coordinator = SelectionCoordinator.MenuClosed()
        _state.update {
            it.copy(
                selectionState = ReaderSelectionState.None,
                selectedText = null,
                selectionRect = null,
                showColorPickerPopover = false,
                showNoteModal = false,
                showTagInput = false,
                showDefinitionInput = false,
                activeNoteText = "",
                activeTagText = "",
                activeDefinitionText = "",
                tagSuggestions = emptyList()
            )
        }
        _clearSelectionEvent.tryEmit(Unit)
    }
}
