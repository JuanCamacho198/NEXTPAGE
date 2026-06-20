package com.nextpage.presentation.screen.library

import androidx.compose.runtime.Composable
import com.nextpage.ui.components.molecules.FilterBottomSheet

@Composable
fun FilterSheetContent(
    showFilterSheet: Boolean,
    filterFormat: String,
    onFormatSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (showFilterSheet) {
        FilterBottomSheet(
            selectedFormat = filterFormat,
            onFormatSelected = onFormatSelected,
            onDismiss = onDismiss
        )
    }
}
