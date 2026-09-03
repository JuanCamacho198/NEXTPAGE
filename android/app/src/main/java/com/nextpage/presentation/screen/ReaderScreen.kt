package com.nextpage.presentation.screen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.nextpage.presentation.screen.reader.ReaderScreenContentHost
import com.nextpage.presentation.screen.reader.ReaderScreenEffects
import com.nextpage.presentation.screen.reader.ReaderScreenOverlaysHost
import com.nextpage.presentation.screen.reader.rememberReaderSelectionCallbacks
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.ui.components.molecules.ReadingProgressBar
import kotlinx.coroutines.flow.MutableSharedFlow

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val chromeUiState by viewModel.chromeUiState.collectAsStateWithLifecycle()
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var showGoToPageDialog by remember { mutableStateOf(false) }
    var goToPageInput by remember { mutableStateOf("") }
    var goToPageError by remember { mutableStateOf<String?>(null) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var debugPanelVisible by remember { mutableStateOf(false) }
    var lastBookmarkTrigger by remember { mutableLongStateOf(0L) }
    var bookmarkRibbonVisible by remember { mutableStateOf(false) }

    var controlsVisible by remember(chromeUiState.isFullscreen) { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val isSelectionActive = uiState.selectionState != com.nextpage.presentation.viewmodel.reader.ReaderSelectionState.None ||
        uiState.showTagInput ||
        uiState.showDefinitionInput ||
        uiState.showColorPickerPopover

    val onUserInteraction: () -> Unit = { lastInteractionAt = SystemClock.elapsedRealtime() }

    val onShowChrome: () -> Unit = {
        if (!uiState.isLoading && !isSelectionActive) {
            controlsVisible = true
            lastInteractionAt = SystemClock.elapsedRealtime()
        }
    }

    val selectionCallbacks = rememberReaderSelectionCallbacks(viewModel, context, uiState)

    val inspectHighlightsHtmlTrigger = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val logWebViewTreeTrigger = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.nextpage.presentation.UiEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is com.nextpage.presentation.UiEvent.ShowSnackbar -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is com.nextpage.presentation.UiEvent.ShareText -> {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, event.text)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, null))
                }
                else -> {}
            }
        }
    }

    ReaderScreenEffects(
        isFullscreen = chromeUiState.isFullscreen,
        selectedBookId = selectedBookId,
        bookFilePath = bookFilePath,
        bookFormat = bookFormat,
        lastInteractionAt = lastInteractionAt,
        currentChapterIndex = uiState.currentChapterIndex,
        currentPdfPage = uiState.currentPdfPage,
        lastBookmarkTrigger = lastBookmarkTrigger,
        viewModel = viewModel,
        view = view,
        controlsVisible = controlsVisible,
        onControlsVisibleChange = { controlsVisible = it },
        onLastInteractionChange = { lastInteractionAt = it },
        onBookmarkRibbonVisibleChange = { bookmarkRibbonVisible = it },
        onShowChrome = onShowChrome
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ReaderChrome(
            contentPadding = contentPadding,
            controlsVisible = controlsVisible,
            header = {
                ReaderHeader(
                    uiState = uiState,
                    onNavigateBack = onNavigateBack,
                    onToggleSearch = { viewModel.searchStateHolder.onToggleSearch() },
                    onToggleHighlights = { viewModel.onToggleHighlightsPanel() },
                    onCreateBookmark = {
                        viewModel.createBookmarkFromCurrentPosition()
                        lastBookmarkTrigger = SystemClock.elapsedRealtime()
                    },
                    onToggleSplitSettings = { viewModel.settingsManager.onToggleSplitSettings() },
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
                ReaderScreenContentHost(
                    uiState = uiState,
                    settingsUiState = settingsUiState,
                    viewModel = viewModel,
                    selectionCallbacks = selectionCallbacks,
                    onShowChrome = onShowChrome,
                    inspectHighlightsHtmlTrigger = inspectHighlightsHtmlTrigger,
                    logWebViewTreeTrigger = logWebViewTreeTrigger,
                    onRetry = {
                        bookFilePath?.let { viewModel.loadBook(selectedBookId, it, bookFormat) }
                    }
                )
            },
            overlays = {
                ReaderScreenOverlaysHost(
                    uiState = uiState,
                    searchUiState = searchUiState,
                    settingsUiState = settingsUiState,
                    viewModel = viewModel,
                    showSleepTimerSheet = showSleepTimerSheet,
                    onDismissSleepTimerSheet = { showSleepTimerSheet = false },
                    showGoToPageDialog = showGoToPageDialog,
                    goToPageInput = goToPageInput,
                    goToPageError = goToPageError,
                    onGoToPageInputChange = {
                        goToPageInput = it
                        goToPageError = null
                    },
                    onGoToPageConfirm = {
                        val pageNumber = goToPageInput.toIntOrNull()
                        if (pageNumber == null || pageNumber !in 1..uiState.totalPdfPages) {
                            goToPageError = "Enter a value between 1 and ${uiState.totalPdfPages}"
                        } else {
                            viewModel.goToPdfPage(pageNumber - 1)
                            showGoToPageDialog = false
                            goToPageInput = ""
                            goToPageError = null
                        }
                    },
                    onDismissGoToPage = {
                        showGoToPageDialog = false
                        goToPageInput = ""
                        goToPageError = null
                    },
                    bookmarkRibbonVisible = bookmarkRibbonVisible,
                    onBookmarkRibbonEnd = { bookmarkRibbonVisible = false },
                    isSelectionActive = isSelectionActive,
                    onUserInteraction = onUserInteraction
                )
            }
        )

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
                        DebugLog.info("Debug", "Simulate-tap: forcing onHighlightTapped for id=${first.id}")
                        viewModel.onHighlightTapped(first, fakeRect)
                    }
                },
                onClearLog = { DebugLog.clear() },
                onCopyLog = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("debug_log", DebugLog.toText()))
                    DebugLog.success("Debug", "Log copied to clipboard")
                },
                onInspectHighlightsHtml = { inspectHighlightsHtmlTrigger.tryEmit(Unit) },
                onLogWebViewTree = { logWebViewTreeTrigger.tryEmit(Unit) }
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
