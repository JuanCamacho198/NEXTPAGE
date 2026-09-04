package com.nextpage.presentation.screen.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.presentation.viewmodel.reader.AnnotationUiState
import com.nextpage.presentation.viewmodel.reader.ReaderSelectionState

data class ReaderSelectionCallbacks(
    val onColorSelected: (String) -> Unit,
    val onCopy: () -> Unit,
    val onDismiss: () -> Unit,
    val onDelete: () -> Unit,
    val onAddTag: () -> Unit,
    val onAnnotate: () -> Unit,
    val onShare: () -> Unit,
    val onDictionary: () -> Unit,
    val onShowColorPicker: () -> Unit,
    val onDismissColorPicker: () -> Unit,
    val onTagTextChanged: (String) -> Unit,
    val onSaveTag: () -> Unit,
    val onDismissTag: () -> Unit,
    val onDefinitionTextChanged: (String) -> Unit,
    val onSaveDefinition: () -> Unit,
    val onDismissDefinition: () -> Unit,
    val activeOverlayHighlightColor: State<String?>
)

/**
 * Selection-menu callbacks + active-highlight tint for [SelectionOverlay].
 *
 * All reads come from the annotation slice ([AnnotationUiState]); this file
 * must not read `ReaderViewModel.uiState` (SDD reader-uiState-cleanup S2).
 *
 * Product note (tint decision, S2): the previous `uiState.activeHighlightId`
 * read was dead — the aggregate field has zero writers and is always null, so
 * the overlay tint always fell back to yellow. The active id is derived live
 * from the coordinator-owned selection (`selectionState as? Existing`), which
 * ACTIVATES the tint: tapping an existing highlight now tints its context menu
 * with that highlight's color instead of the fallback. This matches the clear
 * mirror intent of the original lookup; flag to product if bug-compatible-null
 * (always-yellow) is ever preferred.
 */
@Composable
fun rememberReaderSelectionCallbacks(
    viewModel: ReaderViewModel,
    context: Context,
    annotationUiState: AnnotationUiState
): ReaderSelectionCallbacks {
    // Expose-coordinator path (probe DECIDED, S1): NO AnnotationUiState
    // extension — the id derives synchronously from the selection the
    // coordinator already owns, which S2 receives via the slice.
    val activeHighlightId = (annotationUiState.selectionState as? ReaderSelectionState.Existing)?.highlight?.id
    val activeOverlayHighlightColor = remember(annotationUiState.highlights, activeHighlightId) {
        derivedStateOf {
            activeHighlightId?.let { id ->
                annotationUiState.highlights.firstOrNull { it.id == id }?.color
            }
        }
    }
    val onColorSelected = remember(viewModel) { { color: String -> viewModel.onReadiumHighlightColorSelected(color) } }
    val onCopy = remember(viewModel, context) {
        {
            viewModel.onCopySelectedText()
            annotationUiState.selectedText?.let { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text))
            }
            Unit
        }
    }
    val onDismiss = remember(viewModel) { { viewModel.onDismissContextMenu() } }
    val onDelete = remember(viewModel) {
        {
            val currentHighlight = (annotationUiState.selectionState as? ReaderSelectionState.Existing)?.highlight
            currentHighlight?.let { viewModel.onReadiumDeleteHighlight(it.id) }
            Unit
        }
    }
    val onAddTag = remember(viewModel) { { viewModel.onShowTagInput() } }
    val onAnnotate = remember(viewModel) { { viewModel.onAnnotate() } }
    val onShare = remember(viewModel) {
        {
            when (val sel = annotationUiState.selectionState) {
                is ReaderSelectionState.Existing -> viewModel.onShareSelectedText(sel.highlight.textContent)
                is ReaderSelectionState.New -> viewModel.onShareSelectedText(sel.text)
                ReaderSelectionState.None -> Unit
            }
        }
    }
    val onDictionary = remember(viewModel) { { viewModel.onAddToDictionary() } }
    val onShowColorPicker = remember(viewModel) { { viewModel.onShowColorPickerPopover() } }
    val onDismissColorPicker = remember(viewModel) { { viewModel.onDismissColorPickerPopover() } }
    val onTagTextChanged = remember(viewModel) { { text: String -> viewModel.onTagTextChanged(text) } }
    val onSaveTag = remember(viewModel) { { viewModel.onSaveTag(annotationUiState.activeTagText) } }
    val onDismissTag = remember(viewModel) { { viewModel.onDismissTagInput() } }
    val onDefTextChanged = remember(viewModel) { { text: String -> viewModel.onDefinitionTextChanged(text) } }
    val onSaveDef = remember(viewModel) { { viewModel.onSaveDefinition(annotationUiState.activeDefinitionText) } }
    val onDismissDef = remember(viewModel) { { viewModel.onDismissDefinitionInput() } }

    return ReaderSelectionCallbacks(
        onColorSelected = onColorSelected,
        onCopy = onCopy,
        onDismiss = onDismiss,
        onDelete = onDelete,
        onAddTag = onAddTag,
        onAnnotate = onAnnotate,
        onShare = onShare,
        onDictionary = onDictionary,
        onShowColorPicker = onShowColorPicker,
        onDismissColorPicker = onDismissColorPicker,
        onTagTextChanged = onTagTextChanged,
        onSaveTag = onSaveTag,
        onDismissTag = onDismissTag,
        onDefinitionTextChanged = onDefTextChanged,
        onSaveDefinition = onSaveDef,
        onDismissDefinition = onDismissDef,
        activeOverlayHighlightColor = activeOverlayHighlightColor
    )
}
