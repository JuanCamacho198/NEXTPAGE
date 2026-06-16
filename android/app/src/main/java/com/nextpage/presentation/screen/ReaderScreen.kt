package com.nextpage.presentation.screen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.BuildConfig
import com.nextpage.R
import com.nextpage.debug.DebugLog
import com.nextpage.debug.DebugPanel
import com.nextpage.debug.DebugPrefs
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.ui.components.molecules.HighlightsSheet
import com.nextpage.ui.components.molecules.HighlightAnnotationModal
import com.nextpage.ui.components.molecules.HighlightTagDialog
import com.nextpage.ui.components.molecules.ReadingProgressBar
import com.nextpage.ui.components.molecules.SearchBottomSheet
import com.nextpage.ui.components.molecules.SelectionOverlay
import com.nextpage.ui.components.molecules.SleepTimerOverlay
import com.nextpage.ui.components.molecules.SleepTimerPreset
import com.nextpage.ui.components.molecules.SleepTimerSheet
import com.nextpage.ui.components.molecules.SplitSettingsSheet
import kotlinx.coroutines.flow.MutableSharedFlow

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
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    var showGoToPageDialog by remember { mutableStateOf(false) }
    var goToPageInput by remember { mutableStateOf("") }
    var goToPageError by remember { mutableStateOf<String?>(null) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var debugPanelVisible by remember { mutableStateOf(false) }

    // ── Debug action triggers: panel button → ReadiumReaderContent ──
    val inspectHighlightsHtmlTrigger = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val logWebViewTreeTrigger = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    // ── Collect UiEvents (toasts) ──────────────────
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.nextpage.presentation.UiEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
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

    // ── Footer navigation state (uses chapters for EPUB and PDF) ────
    val canGoPrev = uiState.currentChapterIndex > 0
    val canGoNext = uiState.currentChapterIndex < uiState.chapters.size - 1

    // ── Render via ReaderChrome ─────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        ReaderChrome(
        isFullscreen = uiState.isFullscreen || uiState.isLoading,
        onToggleFullscreen = { viewModel.onToggleFullscreen() },
        contentPadding = contentPadding,
        header = {
            ReaderHeader(
                uiState = uiState,
                onNavigateBack = onNavigateBack,
                onToggleFullscreen = { viewModel.onToggleFullscreen() },
                onToggleSearch = { viewModel.onToggleSearch() },
                onToggleHighlights = { viewModel.onToggleHighlightsPanel() },
                onCreateBookmark = { viewModel.createBookmarkFromCurrentPosition() },
                onToggleSplitSettings = { viewModel.onToggleSplitSettings() },
                onDebugToggle = { viewModel.onDebugForceMenu() }
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
                onProgressChange = { viewModel.onProgressChange(it) },
                onPreviousChapter = { viewModel.goToPreviousChapter() },
                onNextChapter = { viewModel.goToNextChapter() },
                canGoPrevious = canGoPrev,
                canGoNext = canGoNext
            )
        },
        content = {
            when {
                uiState.isLoading -> {
                    LoadingContent(loadTimeMs = uiState.loadTimeMs)
                }

                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = {
                            bookFilePath?.let {
                                viewModel.loadBook(selectedBookId, it, bookFormat)
                            }
                        }
                    )
                }

                uiState.bookFormat == "pdf" && uiState.readiumPublication != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ReadiumPdfReaderContent(
                            publication = uiState.readiumPublication!!,
                            highlights = uiState.highlights,
                            readerSettings = uiState.readerSettings,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        SelectionOverlay(
                            showColorPicker = uiState.showColorPicker,
                            showContextMenu = uiState.showContextMenu,
                            showColorPickerPopover = uiState.showColorPickerPopover,
                            selectionRect = uiState.selectionRect,
                            selectedText = uiState.selectedText,
                            highlights = uiState.highlights,
                            activeHighlightColor = uiState.activeHighlightId?.let { id ->
                                uiState.highlights.firstOrNull { it.id == id }?.color
                            },
                            customHighlightColors = uiState.readerSettings.customHighlightColors,
                            onColorSelected = { color -> viewModel.onReadiumHighlightColorSelected(color) },
                            onCopy = {
                                viewModel.onCopySelectedText()
                                uiState.selectedText?.let { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text))
                                }
                            },
                            onShowContextMenu = { viewModel.onShowContextMenu() },
                            onDismissContextMenu = { viewModel.onDismissContextMenu() },
                            onDelete = {
                                uiState.activeHighlightId?.let { viewModel.onReadiumDeleteHighlight(it) }
                            },
                            onAddTag = { viewModel.onShowTagDialog() },
                            onAddNote = { viewModel.onShowNoteModal() },
                            onAddComment = { viewModel.onShowCommentModal() },
                            onShare = { viewModel.onShareSelectedText() },
                            onShowColorPickerPopover = { viewModel.onShowColorPickerPopover() },
                            onDismissColorPickerPopover = { viewModel.onDismissColorPickerPopover() }
                        )
                    }
                }

                uiState.chapters.isNotEmpty() && uiState.readiumPublication != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ReadiumReaderContent(
                            publication = uiState.readiumPublication!!,
                            navigatorConfig = buildNavigatorConfig(uiState.readerSettings),
                            highlights = uiState.highlights,
                            readerSettings = uiState.readerSettings,
                            viewModel = viewModel,
                            inspectHighlightsHtmlTrigger = inspectHighlightsHtmlTrigger,
                            logWebViewTreeTrigger = logWebViewTreeTrigger,
                            modifier = Modifier.fillMaxSize()
                        )

                        SelectionOverlay(
                            showColorPicker = uiState.showColorPicker,
                            showContextMenu = uiState.showContextMenu,
                            showColorPickerPopover = uiState.showColorPickerPopover,
                            selectionRect = uiState.selectionRect,
                            selectedText = uiState.selectedText,
                            highlights = uiState.highlights,
                            activeHighlightColor = uiState.activeHighlightId?.let { id ->
                                uiState.highlights.firstOrNull { it.id == id }?.color
                            },
                            customHighlightColors = uiState.readerSettings.customHighlightColors,
                            onColorSelected = { color -> viewModel.onReadiumHighlightColorSelected(color) },
                            onCopy = {
                                viewModel.onCopySelectedText()
                                uiState.selectedText?.let { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text))
                                }
                            },
                            onShowContextMenu = { viewModel.onShowContextMenu() },
                            onDismissContextMenu = { viewModel.onDismissContextMenu() },
                            onDelete = {
                                uiState.activeHighlightId?.let { viewModel.onReadiumDeleteHighlight(it) }
                            },
                            onAddTag = { viewModel.onShowTagDialog() },
                            onAddNote = { viewModel.onShowNoteModal() },
                            onAddComment = { viewModel.onShowCommentModal() },
                            onShare = { viewModel.onShareSelectedText() },
                            onShowColorPickerPopover = { viewModel.onShowColorPickerPopover() },
                            onDismissColorPickerPopover = { viewModel.onDismissColorPickerPopover() }
                        )
                    }
                }
            }
        },
        overlays = {
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

            // ── Split Settings Sheet ────────────────────────────
            if (uiState.showSplitSettings && uiState.chapters.isNotEmpty()) {
                SplitSettingsSheet(
                    settings = uiState.readerSettings,
                    previewText = uiState.previewText,
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

            // ── Comment Modal (GshXP) ───────────────────────────
            if (uiState.showCommentModal) {
                HighlightAnnotationModal(
                    titleRes = R.string.comment_modal_title,
                    hintRes = R.string.annotation_textarea_comment_hint,
                    snippetLabelRes = R.string.annotation_snippet_label,
                    selectedText = uiState.selectedText,
                    initialText = uiState.activeCommentText,
                    onSave = { viewModel.onSaveComment(it) },
                    onDismiss = { viewModel.onDismissCommentModal() }
                )
            }

            // ── Tag Dialog ──────────────────────────────────────
            if (uiState.showTagDialog) {
                HighlightTagDialog(
                    initialTag = uiState.activeTagText,
                    onSave = { viewModel.onSaveTag(it) },
                    onDismiss = { viewModel.onDismissTagDialog() }
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
