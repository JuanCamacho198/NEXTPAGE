package com.nextpage.presentation.screen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.WindowInsetsController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.BuildConfig
import com.nextpage.R
import com.nextpage.debug.DebugLog
import com.nextpage.debug.DebugPanel
import com.nextpage.debug.DebugPrefs
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.ui.components.molecules.ChaptersSheet
import com.nextpage.ui.components.molecules.HighlightsSheet
import com.nextpage.ui.components.molecules.HighlightAnnotationModal
import com.nextpage.ui.components.molecules.ReadingProgressBar
import com.nextpage.ui.components.molecules.SearchBottomSheet
import com.nextpage.ui.components.molecules.SelectionOverlay
import com.nextpage.ui.components.molecules.SleepTimerOverlay
import com.nextpage.ui.components.molecules.SleepTimerPreset
import com.nextpage.ui.components.molecules.SleepTimerSheet
import com.nextpage.presentation.screen.readium.buildNavigatorConfig
import com.nextpage.ui.components.molecules.SplitSettingsSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

/** Time of inactivity (ms) before the fullscreen chrome auto-hides. */
private const val FULLSCREEN_AUTOHIDE_MS = 3_000L

/**
 * Reader screen dispatcher.
 *
 * Orchestrates the reader UI by wiring the [ReaderViewModel] state and
 * events into the structural [ReaderChrome] layout and format-specific
 * content composables ([ReadiumReaderContent], [ReadiumPdfReaderContent]).
 *
 * Responsibilities:
 * - Effects: fullscreen insets, reader lifecycle, book loading
 * - Local state: go-to-page dialog, sleep timer sheet visibility
 * - Dispatches to the correct content composable based on book format
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    contentPadding: PaddingValues,
    selectedBookId: String,
    bookFilePath: String?,
    bookFormat: String = "epub",
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var showGoToPageDialog by remember { mutableStateOf(false) }
    var goToPageInput by remember { mutableStateOf("") }
    var goToPageError by remember { mutableStateOf<String?>(null) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var debugPanelVisible by remember { mutableStateOf(false) }
    var lastBookmarkTrigger by remember { mutableLongStateOf(0L) }
    var bookmarkRibbonVisible by remember { mutableStateOf(false) }

    // ── Auto-hide chrome after inactivity (only meaningful in fullscreen) ──
    var controlsVisible by remember(uiState.isFullscreen) {
        // Reset to visible when entering / leaving fullscreen.
        mutableStateOf(true)
    }
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Helper: any UI surface that owns touch input while open.
    val isSelectionActive = uiState.selectionState != com.nextpage.presentation.viewmodel.reader.ReaderSelectionState.None ||
        uiState.showTagInput ||
        uiState.showDefinitionInput ||
        uiState.showColorPickerPopover

    val onUserInteraction: () -> Unit = {
        lastInteractionAt = SystemClock.elapsedRealtime()
    }

    // Edge-tap (top/bottom 5%) callback. Only SHOWS the chrome; never
    // hides it. Hiding is handled by the inactivity auto-hide timer.
    // Gated on the same conditions as the previous tap-to-toggle so
    // that the chrome doesn't pop up over an active text-selection menu
    // or while the book is still loading.
    val onShowChrome: () -> Unit = {
        if (!uiState.isLoading && !isSelectionActive) {
            controlsVisible = true
            lastInteractionAt = SystemClock.elapsedRealtime()
        }
    }

    // Keep the latest onShowChrome reachable from the long-lived
    // pointerInput block without restarting the gesture detector on
    // every recomposition.
    val currentOnShowChrome by rememberUpdatedState(onShowChrome)

    // ── SelectionOverlay: stabilized callbacks (R5,R6) ──────────────
    // R7: derivedStateOf prevents O(n) highlight lookup on every frame
    val activeOverlayHighlightColor by remember(uiState.highlights, uiState.activeHighlightId) {
        derivedStateOf {
            uiState.activeHighlightId?.let { id ->
                uiState.highlights.firstOrNull { it.id == id }?.color
            }
        }
    }
    val onSelectionColorSelected = remember(viewModel) { { color: String -> viewModel.onReadiumHighlightColorSelected(color) } }
    val onSelectionCopy = remember(viewModel, context) { {
        viewModel.onCopySelectedText()
        uiState.selectedText?.let { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text))
        }
        @Suppress("UNUSED_EXPRESSION")
        Unit
    } }
    val onSelectionDismiss = remember(viewModel) { { viewModel.onDismissContextMenu() } }
    val onSelectionDelete = remember(viewModel) { {
        // The Delete action only fires from [FloatingContextMenu] which
        // is shown for [ReaderSelectionState.Existing]. The highlight
        // being edited lives in that state's `highlight` field —
        // `uiState.activeHighlightId` is a separate field documented as
        // "managed internally by SelectionCoordinator — set to null
        // in combine", so reading it always returns null and the
        // delete was a no-op. Pull the id from the selection state.
        val currentHighlight = (uiState.selectionState
            as? com.nextpage.presentation.viewmodel.reader.ReaderSelectionState.Existing)?.highlight
        currentHighlight?.let { viewModel.onReadiumDeleteHighlight(it.id) }
        @Suppress("UNUSED_EXPRESSION")
        Unit
    } }
    val onSelectionAddTag = remember(viewModel) { { viewModel.onShowTagInput() } }
    val onSelectionAnnotate = remember(viewModel) { { viewModel.onAnnotate() } }
    val onSelectionShare = remember(viewModel) { {
        // Same pattern as onSelectionDelete (lines 161-174): read from
        // selectionState to get the correct text — Existing highlights
        // store text in highlight.textContent, not mutableUiState.selectedText.
        when (val sel = uiState.selectionState) {
            is com.nextpage.presentation.viewmodel.reader.ReaderSelectionState.Existing ->
                viewModel.onShareSelectedText(sel.highlight.textContent)
            is com.nextpage.presentation.viewmodel.reader.ReaderSelectionState.New ->
                viewModel.onShareSelectedText(sel.text)
            com.nextpage.presentation.viewmodel.reader.ReaderSelectionState.None -> {
                @Suppress("UNUSED_EXPRESSION")
                Unit
            }
        }
    } }
    val onSelectionDictionary = remember(viewModel) { { viewModel.onAddToDictionary() } }
    val onSelectionShowColorPicker = remember(viewModel) { { viewModel.onShowColorPickerPopover() } }
    val onSelectionDismissColorPicker = remember(viewModel) { { viewModel.onDismissColorPickerPopover() } }
    val onSelectionTagTextChanged = remember(viewModel) { { text: String -> viewModel.onTagTextChanged(text) } }
    val onSelectionSaveTag = remember(viewModel) { { viewModel.onSaveTag(uiState.activeTagText) } }
    val onSelectionDismissTag = remember(viewModel) { { viewModel.onDismissTagInput() } }
    val onSelectionDefTextChanged = remember(viewModel) { { text: String -> viewModel.onDefinitionTextChanged(text) } }
    val onSelectionSaveDef = remember(viewModel) { { viewModel.onSaveDefinition(uiState.activeDefinitionText) } }
    val onSelectionDismissDef = remember(viewModel) { { viewModel.onDismissDefinitionInput() } }

    // ── Debug action triggers: panel button → ReadiumReaderContent ──
    val inspectHighlightsHtmlTrigger = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val logWebViewTreeTrigger = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Collect UiEvents (toasts + snackbars) ─────────
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.nextpage.presentation.UiEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is com.nextpage.presentation.UiEvent.ShowSnackbar -> {
                    android.widget.Toast.makeText(
                        context,
                        event.message,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                is com.nextpage.presentation.UiEvent.ShareText -> {
                    val shareIntent = android.content.Intent(
                        android.content.Intent.ACTION_SEND
                    ).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, event.text)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, null)
                    )
                }
                else -> {}
            }
        }
    }

    // ── Orientation restore on dispose ──
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.let { it.requestedOrientation = originalOrientation }
        }
    }

    // ── Fullscreen: apply WindowInsetsController when state changes ──
    DisposableEffect(uiState.isFullscreen) {
        if (uiState.isFullscreen) {
            view.windowInsetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            view.windowInsetsController?.let { controller ->
                controller.show(android.view.WindowInsets.Type.systemBars())
            }
        }
        onDispose {
            view.windowInsetsController?.let { controller ->
                controller.show(android.view.WindowInsets.Type.systemBars())
            }
        }
    }

    // ── Reader lifecycle ────────────────────────────────────────────
    DisposableEffect(selectedBookId) {
        viewModel.onReaderOpened()
        onDispose {
            viewModel.onReaderPaused()
        }
    }

    // ── Load book ──────────────────────────────────────────────────
    LaunchedEffect(selectedBookId, bookFilePath, bookFormat) {
        if (!selectedBookId.isBlank() && bookFilePath != null) {
            viewModel.loadBook(selectedBookId, bookFilePath, bookFormat)
        }
    }

    // ── Inactivity auto-hide (fullscreen only) ─────────────────────
    LaunchedEffect(uiState.isFullscreen, lastInteractionAt) {
        if (!uiState.isFullscreen) return@LaunchedEffect
        val snapshot = lastInteractionAt
        delay(FULLSCREEN_AUTOHIDE_MS)
        // If no new interaction registered since the timer started, hide.
        if (lastInteractionAt == snapshot && uiState.isFullscreen) {
            controlsVisible = false
        }
    }

    // ── Reset inactivity timer on page turn (chapter / PDF page) ──
    LaunchedEffect(uiState.currentChapterIndex, uiState.currentPdfPage) {
        if (uiState.isFullscreen) {
            lastInteractionAt = SystemClock.elapsedRealtime()
        }
    }

    // ── Bookmark ribbon feedback animation ──────────────────────
    LaunchedEffect(lastBookmarkTrigger) {
        if (lastBookmarkTrigger != 0L) {
            bookmarkRibbonVisible = true
            delay(2_200L)
            bookmarkRibbonVisible = false
        }
    }

    // ── Render via ReaderChrome ─────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        ReaderChrome(
        contentPadding = contentPadding,
        controlsVisible = controlsVisible,
        // The tap-to-toggle-chrome detector lives inside ReadiumReaderContent
        // (and the PDF reader) as a transparent overlay on top of the
        // WebView/PDF view. Putting it on a parent Box here was ineffective
        // because the native WebView consumed the down event before the
        // Compose pointerInput saw it. The overlay is placed at a higher
        // z-layer so it receives the tap first; the WebView still gets
        // long-press for text selection.
        header = {
            ReaderHeader(
                uiState = uiState,
                onNavigateBack = onNavigateBack,
                onToggleSearch = { viewModel.onToggleSearch() },
                onToggleHighlights = { viewModel.onToggleHighlightsPanel() },
                onCreateBookmark = {
                    viewModel.createBookmarkFromCurrentPosition()
                    lastBookmarkTrigger = SystemClock.elapsedRealtime()
                },
                onToggleSplitSettings = { viewModel.onToggleSplitSettings() },
                onToggleToc = { viewModel.onToggleTocSheet() },
                onToggleDebugPanel = { debugPanelVisible = !debugPanelVisible }
            )
        },
        footer = {
            ReadingProgressBar(
                progressPercent = uiState.progressPercent,
                label = uiState.progressLabel,
                onRotateScreen = {
                    val activity = context as Activity
                    val portrait = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                    activity.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                onProgressChange = { viewModel.onProgressChange(it) }
            )
        },
        content = {
            val error = uiState.error
            val readiumPublication = uiState.readiumPublication
            when {
                uiState.isLoading -> {
                    LoadingContent(loadTimeMs = uiState.loadTimeMs)
                }

                error != null -> {
                    ErrorContent(
                        error = error,
                        onRetry = {
                            bookFilePath?.let {
                                viewModel.loadBook(selectedBookId, it, bookFormat)
                            }
                        }
                    )
                }

                uiState.bookFormat == "pdf" && readiumPublication != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ReadiumPdfReaderContent(
                            publication = readiumPublication,
                            highlights = uiState.highlights,
                            readerSettings = uiState.readerSettings,
                            viewModel = viewModel,
                            onShowChrome = { currentOnShowChrome() },
                            modifier = Modifier.fillMaxSize()
                        )

                        // SelectionOverlay with stabilized callbacks (R5,R6,R7)
                        SelectionOverlay(
                            selectionState = uiState.selectionState,
                            showColorPickerPopover = uiState.showColorPickerPopover,
                            showTagInput = uiState.showTagInput,
                            tagSuggestions = uiState.tagSuggestions,
                            activeTagText = uiState.activeTagText,
                            showDefinitionInput = uiState.showDefinitionInput,
                            activeDefinitionText = uiState.activeDefinitionText,
                            selectionRect = uiState.selectionRect,
                            selectedText = uiState.selectedText,
                            highlights = uiState.highlights,
                            activeHighlightColor = activeOverlayHighlightColor,
                            customHighlightColors = uiState.readerSettings.customHighlightColors,
                            onColorSelected = onSelectionColorSelected,
                            onCopy = onSelectionCopy,
                            onDismissContextMenu = onSelectionDismiss,
                            onDelete = onSelectionDelete,
                            onAddTag = onSelectionAddTag,
                            onAnnotate = onSelectionAnnotate,
                            onShare = onSelectionShare,
                            onDictionary = onSelectionDictionary,
                            onShowColorPickerPopover = onSelectionShowColorPicker,
                            onDismissColorPickerPopover = onSelectionDismissColorPicker,
                            onTagTextChanged = onSelectionTagTextChanged,
                            onSaveTag = onSelectionSaveTag,
                            onDismissTagInput = onSelectionDismissTag,
                            onDefinitionTextChanged = onSelectionDefTextChanged,
                            onSaveDefinition = onSelectionSaveDef,
                            onDismissDefinitionInput = onSelectionDismissDef
                        )
                    }
                }

                uiState.chapters.isNotEmpty() && readiumPublication != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ReadiumReaderContent(
                            publication = readiumPublication,
                            navigatorConfig = buildNavigatorConfig(uiState.readerSettings),
                            highlights = uiState.highlights,
                            readerSettings = uiState.readerSettings,
                            viewModel = viewModel,
                            initialLocator = uiState.readiumLocator,
                            inspectHighlightsHtmlTrigger = inspectHighlightsHtmlTrigger,
                            logWebViewTreeTrigger = logWebViewTreeTrigger,
                            onShowChrome = { currentOnShowChrome() },
                            modifier = Modifier.fillMaxSize()
                        )

                        // SelectionOverlay with stabilized callbacks (R5,R6,R7)
                        SelectionOverlay(
                            selectionState = uiState.selectionState,
                            showColorPickerPopover = uiState.showColorPickerPopover,
                            showTagInput = uiState.showTagInput,
                            tagSuggestions = uiState.tagSuggestions,
                            activeTagText = uiState.activeTagText,
                            showDefinitionInput = uiState.showDefinitionInput,
                            activeDefinitionText = uiState.activeDefinitionText,
                            selectionRect = uiState.selectionRect,
                            selectedText = uiState.selectedText,
                            highlights = uiState.highlights,
                            activeHighlightColor = activeOverlayHighlightColor,
                            customHighlightColors = uiState.readerSettings.customHighlightColors,
                            onColorSelected = onSelectionColorSelected,
                            onCopy = onSelectionCopy,
                            onDismissContextMenu = onSelectionDismiss,
                            onDelete = onSelectionDelete,
                            onAddTag = onSelectionAddTag,
                            onAnnotate = onSelectionAnnotate,
                            onShare = onSelectionShare,
                            onDictionary = onSelectionDictionary,
                            onShowColorPickerPopover = onSelectionShowColorPicker,
                            onDismissColorPickerPopover = onSelectionDismissColorPicker,
                            onTagTextChanged = onSelectionTagTextChanged,
                            onSaveTag = onSelectionSaveTag,
                            onDismissTagInput = onSelectionDismissTag,
                            onDefinitionTextChanged = onSelectionDefTextChanged,
                            onSaveDefinition = onSelectionSaveDef,
                            onDismissDefinitionInput = onSelectionDismissDef
                        )
                    }
                }
            }
        },
        overlays = {
            // ── Fullscreen side arrows (page nav) ───────────────
            // Visible only for EPUB paginated/scroll readers. Hidden for
            // PDF (the floating buttons cover the document) and while
            // loading or during an active selection (so arrow hit areas
            // don't steal the drag gesture). Page nav still works via the
            // edge-tap zones.
            if (!uiState.isLoading && !isSelectionActive &&
                !(uiState.readerSettings.verticalScroll && uiState.bookFormat == "epub") &&
                uiState.bookFormat != "pdf"
            ) {
                ReaderFullscreenArrows(
                    onPrevious = {
                        onUserInteraction()
                        viewModel.onTapZone(isLeftZone = true)
                    },
                    onNext = {
                        onUserInteraction()
                        viewModel.onTapZone(isLeftZone = false)
                    }
                )
            }

            // ── Bookmark ribbon feedback (animated, transient) ──
            BookmarkRibbonOverlay(
                visible = bookmarkRibbonVisible,
                onAnimationEnd = { bookmarkRibbonVisible = false }
            )

            // ── Search Bottom Sheet ─────────────────────────────
            if (uiState.isSearchActive) {
                SearchBottomSheet(
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    onQueryChange = { viewModel.onSearchQuery(it) },
                    onClearQuery = { viewModel.onClearSearch() },
                    onResultSelected = { viewModel.onSearchResultSelected(it) },
                    onDismiss = { viewModel.onDismissSearch() }
                )
            }

            // ── Highlights Sheet ────────────────────────────────
            if (uiState.showHighlightsSheet) {
                HighlightsSheet(
                    highlights = uiState.highlights,
                    onHighlightSelected = { viewModel.onHighlightSelected(it) },
                    onDismiss = { viewModel.onToggleHighlightsPanel() }
                )
            }

            // ── Chapters / TOC Sheet ───────────────────────────
            if (uiState.showTocSheet) {
                ChaptersSheet(
                    chapters = uiState.chapters,
                    currentChapterIndex = uiState.currentChapterIndex,
                    onChapterSelected = { idx -> viewModel.goToChapter(idx) },
                    onDismiss = { viewModel.onToggleTocSheet() }
                )
            }

            // ── Split Settings Sheet ────────────────────────────
            if (uiState.showSplitSettings && uiState.chapters.isNotEmpty()) {
                // Prefer the real current-chapter text (populated from the
                // book by the ViewModel); fall back to the selected text,
                // then the chapter title, then blank. Kept as a remember so
                // the Compose preview (no book) still renders lorem-ipsum.
                val previewText = remember(uiState.previewText, uiState.selectedText, uiState.currentChapterIndex, uiState.chapters) {
                    uiState.previewText.ifBlank {
                        uiState.selectedText?.takeIf { it.isNotBlank() }
                            ?: uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title
                            ?: ""
                    }
                }
                SplitSettingsSheet(
                    settings = uiState.readerSettings,
                    previewText = previewText,
                    onSettingsChanged = { viewModel.updateReaderSettings(it) },
                    onDismiss = { viewModel.onToggleSplitSettings() }
                )
            }

            // ── Sleep Timer Sheet ───────────────────────────────
            if (showSleepTimerSheet) {
                SleepTimerSheet(
                    isActive = uiState.sleepTimerActive,
                    remainingFormatted = viewModel.formatSleepTimerRemaining(
                        uiState.sleepTimerRemainingSecs
                    ),
                    presets = listOf(
                        SleepTimerPreset("5", 5),
                        SleepTimerPreset("10", 10),
                        SleepTimerPreset("15", 15),
                        SleepTimerPreset("30", 30),
                        SleepTimerPreset(
                            label = stringResource(R.string.reader_sleep_timer_end_of_chapter),
                            minutes = Int.MIN_VALUE,
                            isEndOfChapter = true
                        )
                    ),
                    onPresetSelected = { minutes: Int ->
                        viewModel.startSleepTimer(minutes)
                        showSleepTimerSheet = false
                    },
                    onCancel = {
                        viewModel.cancelSleepTimer()
                        showSleepTimerSheet = false
                    },
                    onDismiss = { showSleepTimerSheet = false }
                )
            }

            // ── Sleep Timer Overlay ─────────────────────────────
            if (uiState.sleepTimerFinished) {
                SleepTimerOverlay(
                    onDismiss = { viewModel.dismissSleepTimerOverlay() }
                )
            }

            // ── Note Modal (GshXP) ──────────────────────────────
            if (uiState.showNoteModal) {
                HighlightAnnotationModal(
                    titleRes = R.string.note_modal_title,
                    hintRes = R.string.annotation_textarea_note_hint,
                    snippetLabelRes = R.string.annotation_snippet_label,
                    selectedText = uiState.selectedText,
                    initialText = uiState.activeNoteText,
                    onSave = { viewModel.onSaveNote(it) },
                    onDismiss = { viewModel.onDismissNoteModal() }
                )
            }

            // ── Go To Page Dialog (PDF only) ────────────────────
            if (showGoToPageDialog && uiState.totalPdfPages > 0) {
                AlertDialog(
                    onDismissRequest = {
                        showGoToPageDialog = false
                        goToPageInput = ""
                        goToPageError = null
                    },
                    title = { Text(text = stringResource(R.string.reader_go_to_page)) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = goToPageInput,
                                onValueChange = {
                                    goToPageInput = it
                                    goToPageError = null
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            R.string.reader_go_to_page_input_label
                                        )
                                    )
                                },
                                singleLine = true,
                                isError = goToPageError != null,
                                supportingText = {
                                    val error = goToPageError
                                    if (error != null) {
                                        Text(text = error)
                                    } else {
                                        Text(
                                            text = stringResource(
                                                R.string.reader_go_to_page_input_hint,
                                                uiState.totalPdfPages
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val pageNumber = goToPageInput.toIntOrNull()
                                if (pageNumber == null ||
                                    pageNumber !in 1..uiState.totalPdfPages
                                ) {
                                    goToPageError =
                                        "Enter a value between 1 and ${uiState.totalPdfPages}"
                                } else {
                                    viewModel.goToPdfPage(pageNumber - 1)
                                    showGoToPageDialog = false
                                    goToPageInput = ""
                                    goToPageError = null
                                }
                            }
                        ) {
                            Text(text = stringResource(R.string.reader_go))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showGoToPageDialog = false
                                goToPageInput = ""
                                goToPageError = null
                            }
                        ) {
                            Text(text = stringResource(R.string.reader_cancel))
                        }
                    }
                )
            }
        }
    )

        // ── Debug chip (bottom-end, debug builds only) ────────────
        if (BuildConfig.DEBUG && DebugPrefs.isEnabled(context)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 12.dp)
                    .clickable { debugPanelVisible = !debugPanelVisible },
                color = Color(0xFFEF4444),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_chip_label),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // ── Debug panel (debug builds only) ──────────────────────
        if (BuildConfig.DEBUG && DebugPrefs.isEnabled(context)) {
            DebugPanel(
                visible = debugPanelVisible,
                state = uiState,
                onClose = { debugPanelVisible = false },
                onForceColorPicker = { viewModel.onDebugForceColorPicker() },
                onForceContextMenu = { viewModel.onDebugForceMenu() },
                onSimulateHighlightTap = {
                    val first = uiState.highlights.firstOrNull { it.locatorJson != null }
                    if (first == null) {
                        DebugLog.warn("Debug", "Simulate-tap: no highlights to simulate")
                    } else {
                        val fakeRect = android.graphics.RectF(
                            uiState.selectionRect?.left?.toFloat() ?: 200f,
                            uiState.selectionRect?.top?.toFloat() ?: 200f,
                            (uiState.selectionRect?.right ?: 600).toFloat(),
                            (uiState.selectionRect?.bottom ?: 250).toFloat()
                        )
                        DebugLog.info(
                            "Debug",
                            "Simulate-tap: forcing onHighlightTapped for id=${first.id}"
                        )
                        viewModel.onHighlightTapped(first, fakeRect)
                    }
                },
                onClearLog = { DebugLog.clear() },
                onCopyLog = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("debug_log", DebugLog.toText()))
                    DebugLog.success("Debug", "Log copied to clipboard")
                },
                onInspectHighlightsHtml = {
                    inspectHighlightsHtmlTrigger.tryEmit(Unit)
                },
                onLogWebViewTree = {
                    logWebViewTreeTrigger.tryEmit(Unit)
                }
            )
        }

        // ── Snackbar host (for "Copied to clipboard" etc.) ─────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── Loading Content ─────────────────────────────────────────────────

@Composable
private fun LoadingContent(
    loadTimeMs: Long?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Color(0xFFADC6FF))
        if (loadTimeMs != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.reader_loaded_in, loadTimeMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF718096)
            )
        }
    }
}

// ── Error Content ───────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFEF4444)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.reader_retry))
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ReaderScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        LoadingContent(loadTimeMs = 150L)
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        LoadingContent(loadTimeMs = 150L)
    }
}
