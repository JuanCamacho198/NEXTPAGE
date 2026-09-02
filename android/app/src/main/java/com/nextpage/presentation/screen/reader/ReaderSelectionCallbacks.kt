package com.nextpage.presentation.screen.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.nextpage.presentation.viewmodel.ReaderViewModel
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

@Composable
fun rememberReaderSelectionCallbacks(
    viewModel: ReaderViewModel,
    context: Context,
    uiState: com.nextpage.presentation.viewmodel.ReaderUiState
): ReaderSelectionCallbacks {
    val activeOverlayHighlightColor = remember(uiState.highlights, uiState.activeHighlightId) {
        derivedStateOf {
            uiState.activeHighlightId?.let { id ->
                uiState.highlights.firstOrNull { it.id == id }?.color
            }
        }
    }
    val onColorSelected = remember(viewModel) { { color: String -> viewModel.onReadiumHighlightColorSelected(color) } }
    val onCopy = remember(viewModel, context) {
        {
            viewModel.onCopySelectedText()
            uiState.selectedText?.let { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text))
            }
            Unit
        }
    }
    val onDismiss = remember(viewModel) { { viewModel.onDismissContextMenu() } }
    val onDelete = remember(viewModel) {
        {
            val currentHighlight = (uiState.selectionState as? ReaderSelectionState.Existing)?.highlight
            currentHighlight?.let { viewModel.onReadiumDeleteHighlight(it.id) }
            Unit
        }
    }
    val onAddTag = remember(viewModel) { { viewModel.onShowTagInput() } }
    val onAnnotate = remember(viewModel) { { viewModel.onAnnotate() } }
    val onShare = remember(viewModel) {
        {
            when (val sel = uiState.selectionState) {
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
    val onSaveTag = remember(viewModel) { { viewModel.onSaveTag(uiState.activeTagText) } }
    val onDismissTag = remember(viewModel) { { viewModel.onDismissTagInput() } }
    val onDefTextChanged = remember(viewModel) { { text: String -> viewModel.onDefinitionTextChanged(text) } }
    val onSaveDef = remember(viewModel) { { viewModel.onSaveDefinition(uiState.activeDefinitionText) } }
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
