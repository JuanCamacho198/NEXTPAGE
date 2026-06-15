package com.nextpage.presentation.screen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.ui.components.molecules.HighlightsSheet
import com.nextpage.ui.components.molecules.PdfWebView
import com.nextpage.ui.components.molecules.ReadingProgressBar
import com.nextpage.ui.components.molecules.SearchBottomSheet
import com.nextpage.ui.components.molecules.SelectionOverlay
import com.nextpage.ui.components.molecules.SleepTimerOverlay
import com.nextpage.ui.components.molecules.SleepTimerPreset
import com.nextpage.ui.components.molecules.SleepTimerSheet
import com.nextpage.ui.components.molecules.SplitSettingsSheet

/**
 * Reader screen dispatcher.
 *
 * Orchestrates the reader UI by wiring the [ReaderViewModel] state and
 * events into the structural [ReaderChrome] layout and format-specific
 * content composables ([EpubReaderContent], [PdfWebView]).
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

    // ── Footer navigation state ─────────────────────────────────────
    val canGoPrev = if (uiState.totalPdfPages > 0) {
        uiState.currentPdfPage > 0
    } else {
        uiState.currentChapterIndex > 0
    }
    val canGoNext = if (uiState.totalPdfPages > 0) {
        uiState.currentPdfPage < uiState.totalPdfPages - 1
    } else {
        uiState.currentChapterIndex < uiState.chapters.size - 1
    }

    // ── Render via ReaderChrome ─────────────────────────────────────
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
                onToggleSplitSettings = { viewModel.onToggleSplitSettings() }
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
                onPreviousChapter = {
                    if (uiState.totalPdfPages > 0) viewModel.goToPreviousPdfPage()
                    else viewModel.goToPreviousChapter()
                },
                onNextChapter = {
                    if (uiState.totalPdfPages > 0) viewModel.goToNextPdfPage()
                    else viewModel.goToNextChapter()
                },
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

                uiState.totalPdfPages > 0 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PdfWebView(
                            filePath = bookFilePath ?: "",
                            currentPage = uiState.currentPdfPage,
                            searchQuery = uiState.searchQuery,
                            highlights = uiState.highlights,
                            onPageChanged = { page -> viewModel.goToPdfPage(page - 1) },
                            onDocumentLoaded = { pages -> viewModel.onPdfDocumentLoaded(pages) },
                            onTextSelectionEvent = { text, left, top, right, bottom ->
                                viewModel.onTextSelectionEvent(text, left, top, right, bottom)
                            },
                            onSearchResults = { json -> viewModel.onPdfSearchResults(json) },
                            onHighlightTapped = viewModel::onHighlightTapped,
                            modifier = Modifier.fillMaxSize()
                        )

                        SelectionOverlay(
                            showColorPicker = uiState.showColorPicker,
                            showContextMenu = uiState.showContextMenu,
                            selectionRect = uiState.selectionRect,
                            selectedText = uiState.selectedText,
                            highlights = uiState.highlights,
                            onColorSelected = { color -> viewModel.onSelectHighlightColor(color) },
                            onCopy = {
                                viewModel.onCopySelectedText()
                                uiState.selectedText?.let { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text))
                                }
                            },
                            onShowContextMenu = { viewModel.onShowContextMenu() },
                            onDismissContextMenu = { viewModel.onDismissContextMenu() },
                            onAddTag = {},
                            onAddNote = {},
                            onAddComment = {},
                            onShare = {}
                        )
                    }
                }

                uiState.chapters.isNotEmpty() -> {
                    val currentChapter = uiState.chapters.getOrNull(uiState.currentChapterIndex)
                    EpubReaderContent(
                        htmlContent = uiState.chapterHtmlContent,
                        settings = uiState.readerSettings,
                        filePath = bookFilePath,
                        epubContentLoader = viewModel.epubContentLoader,
                        chapterHref = currentChapter?.href,
                        showColorPicker = uiState.showColorPicker,
                        showContextMenu = uiState.showContextMenu,
                        selectionRect = uiState.selectionRect,
                        selectedText = uiState.selectedText,
                        highlights = uiState.highlights,
                        onTapZone = { isLeft -> viewModel.onTapZone(isLeft) },
                        onTextSelectionEvent = { text, left, top, right, bottom ->
                            viewModel.onTextSelectionEvent(text, left, top, right, bottom)
                        },
                        onColorSelected = { color ->
                            viewModel.onSelectHighlightColor(color)
                        },
                        onCopy = {
                            viewModel.onCopySelectedText()
                            uiState.selectedText?.let { text ->
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("highlight", text)
                                )
                            }
                        },
                        onShowContextMenu = { viewModel.onShowContextMenu() },
                        onDismissContextMenu = { viewModel.onDismissContextMenu() },
                        onHighlightTapped = viewModel::onHighlightTapped,
                        modifier = Modifier.fillMaxSize()
                    )
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
