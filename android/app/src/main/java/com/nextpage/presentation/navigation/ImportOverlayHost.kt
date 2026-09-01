package com.nextpage.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.presentation.viewmodel.library.BookImportState
import com.nextpage.ui.components.atoms.NextPageImportOverlay
import kotlinx.coroutines.delay

private const val IMPORT_OVERLAY_WATCHDOG_TIMEOUT_MS = 120_000L

/**
 * Collects [LibraryViewModel.importState] → [NextPageImportOverlay] + 120s watchdog.
 *
 * Verbatim from host:
 * ```
 * val importState by libraryViewModel.importState.collectAsStateWithLifecycle()
 * LaunchedEffect(importState) {
 *   if (importState !is BookImportState.Idle) { delay(120_000); libraryViewModel.resetImportState() }
 * }
 * if (importState !is BookImportState.Idle) { NextPageImportOverlay(...) }
 * ```
 */
@Composable
fun ImportOverlayHost(
    libraryViewModel: LibraryViewModel
) {
    val importState by libraryViewModel.importState.collectAsStateWithLifecycle()

    LaunchedEffect(importState) {
        if (importState !is BookImportState.Idle) {
            delay(IMPORT_OVERLAY_WATCHDOG_TIMEOUT_MS)
            libraryViewModel.resetImportState()
        }
    }

    if (importState !is BookImportState.Idle) {
        NextPageImportOverlay(
            importState = importState,
            modifier = Modifier.fillMaxSize()
        )
    }
}
