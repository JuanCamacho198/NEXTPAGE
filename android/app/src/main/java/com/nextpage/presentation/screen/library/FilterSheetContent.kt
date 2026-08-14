package com.nextpage.presentation.screen.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nextpage.presentation.theme.NextPageTheme
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

// ─── Previews ─────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun FilterSheetContentDarkPreview() {
    NextPageTheme(darkTheme = true) {
        FilterSheetContent(
            showFilterSheet = true,
            filterFormat = "epub",
            onFormatSelected = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FilterSheetContentLightPreview() {
    NextPageTheme(darkTheme = false) {
        FilterSheetContent(
            showFilterSheet = true,
            filterFormat = "epub",
            onFormatSelected = {},
            onDismiss = {}
        )
    }
}
