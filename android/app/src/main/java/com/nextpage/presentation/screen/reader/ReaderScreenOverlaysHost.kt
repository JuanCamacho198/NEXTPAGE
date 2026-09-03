package com.nextpage.presentation.screen.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.nextpage.R
import com.nextpage.presentation.feature.highlights.HighlightsSheet
import com.nextpage.presentation.screen.BookmarkRibbonOverlay
import com.nextpage.presentation.screen.ReaderFullscreenArrows
import com.nextpage.presentation.viewmodel.ReaderUiState
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.presentation.viewmodel.reader.SearchUiState
import com.nextpage.presentation.viewmodel.reader.SettingsUiState
import com.nextpage.presentation.viewmodel.reader.SleepTimerUiState
import com.nextpage.ui.components.molecules.ChaptersSheet
import com.nextpage.ui.components.molecules.HighlightAnnotationModal
import com.nextpage.ui.components.molecules.SearchBottomSheet
import com.nextpage.ui.components.molecules.SleepTimerOverlay
import com.nextpage.ui.components.molecules.SleepTimerPreset
import com.nextpage.ui.components.molecules.SleepTimerSheet
import com.nextpage.ui.components.molecules.SplitSettingsSheet

@Composable
fun ReaderScreenOverlaysHost(
    uiState: ReaderUiState,
    searchUiState: SearchUiState,
    settingsUiState: SettingsUiState,
    sleepTimerUiState: SleepTimerUiState,
    viewModel: ReaderViewModel,
    showSleepTimerSheet: Boolean,
    onDismissSleepTimerSheet: () -> Unit,
    showGoToPageDialog: Boolean,
    goToPageInput: String,
    goToPageError: String?,
    onGoToPageInputChange: (String) -> Unit,
    onGoToPageConfirm: () -> Unit,
    onDismissGoToPage: () -> Unit,
    bookmarkRibbonVisible: Boolean,
    onBookmarkRibbonEnd: () -> Unit,
    isSelectionActive: Boolean,
    onUserInteraction: () -> Unit
) {
    if (!uiState.isLoading && !isSelectionActive &&
        !(settingsUiState.readerSettings.verticalScroll && uiState.bookFormat == "epub") &&
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

    BookmarkRibbonOverlay(
        visible = bookmarkRibbonVisible,
        onAnimationEnd = onBookmarkRibbonEnd
    )

    if (searchUiState.isSearchActive) {
        SearchBottomSheet(
            query = searchUiState.searchQuery,
            results = searchUiState.searchResults,
            isSearching = searchUiState.isSearching,
            onQueryChange = {
                viewModel.searchStateHolder.onSearchQuery(
                    it,
                    uiState.readiumPublication,
                    uiState.bookFormat
                )
            },
            onClearQuery = { viewModel.onClearSearch() },
            onResultSelected = { viewModel.onSearchResultSelected(it) },
            onDismiss = { viewModel.onDismissSearch() }
        )
    }

    if (uiState.showHighlightsSheet) {
        HighlightsSheet(
            highlights = uiState.highlights,
            onHighlightSelected = { viewModel.onHighlightSelected(it) },
            onDismiss = { viewModel.onToggleHighlightsPanel() }
        )
    }

    if (uiState.showTocSheet) {
        ChaptersSheet(
            chapters = uiState.chapters,
            currentChapterIndex = uiState.currentChapterIndex,
            onChapterSelected = { idx -> viewModel.goToChapter(idx) },
            onDismiss = { viewModel.onToggleTocSheet() }
        )
    }

    if (settingsUiState.showSplitSettings && uiState.chapters.isNotEmpty()) {
        val previewText = remember(uiState.previewText, uiState.selectedText, uiState.currentChapterIndex, uiState.chapters) {
            uiState.previewText.ifBlank {
                uiState.selectedText?.takeIf { it.isNotBlank() }
                    ?: uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title
                    ?: ""
            }
        }
        SplitSettingsSheet(
            settings = settingsUiState.readerSettings,
            previewText = previewText,
            onSettingsChanged = { viewModel.settingsManager.updateReaderSettings(it) },
            onDismiss = { viewModel.settingsManager.onToggleSplitSettings() }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            isActive = sleepTimerUiState.isActive,
            remainingFormatted = viewModel.sleepTimerManager.formatRemaining(sleepTimerUiState.remainingSecs),
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
                viewModel.sleepTimerManager.startTimer(minutes)
                onDismissSleepTimerSheet()
            },
            onCancel = {
                viewModel.sleepTimerManager.cancel()
                onDismissSleepTimerSheet()
            },
            onDismiss = onDismissSleepTimerSheet
        )
    }

    if (sleepTimerUiState.isFinished) {
        SleepTimerOverlay(
            onDismiss = { viewModel.sleepTimerManager.dismissOverlay() }
        )
    }

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

    if (showGoToPageDialog && uiState.totalPdfPages > 0) {
        AlertDialog(
            onDismissRequest = onDismissGoToPage,
            title = { Text(text = stringResource(R.string.reader_go_to_page)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = goToPageInput,
                        onValueChange = {
                            onGoToPageInputChange(it)
                        },
                        label = { Text(text = stringResource(R.string.reader_go_to_page_input_label)) },
                        singleLine = true,
                        isError = goToPageError != null,
                        supportingText = {
                            val error = goToPageError
                            if (error != null) {
                                Text(text = error)
                            } else {
                                Text(
                                    text = stringResource(R.string.reader_go_to_page_input_hint, uiState.totalPdfPages)
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onGoToPageConfirm) {
                    Text(text = stringResource(R.string.reader_go))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissGoToPage) {
                    Text(text = stringResource(R.string.reader_cancel))
                }
            }
        )
    }
}
