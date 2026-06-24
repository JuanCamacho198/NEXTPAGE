package com.nextpage.presentation.screen

import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.presentation.screen.library.BookGridSection
import com.nextpage.presentation.screen.library.FilterSheetContent
import com.nextpage.presentation.screen.library.LibraryDialogs
import com.nextpage.presentation.screen.library.LibraryToolbar
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.util.getContentDisplayName
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageEmptyState
import com.nextpage.ui.components.atoms.SyncStatusIndicator

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onBookSelected: (String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchedBooks by viewModel.searchedBooks.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editCoverUri by remember { mutableStateOf<Uri?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        editCoverUri = uri
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult

            scope.launch {
                runCatching {
                    val fileName = getContentDisplayName(context, uri)
                        ?: uri.lastPathSegment
                        ?: "imported_${System.currentTimeMillis()}"
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val isPdf = fileName.endsWith(".pdf", true) || mimeType == "application/pdf"
                    val isEpub = fileName.endsWith(".epub", true) || mimeType == "application/epub+zip"

                    if (!isPdf && !isEpub) return@runCatching

                    if (isPdf) {
                        val pdfDir = File(context.filesDir, "pdfs")
                        if (!pdfDir.exists()) pdfDir.mkdirs()
                        val pdfFile = File(pdfDir, fileName)
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                pdfFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        viewModel.importPdfBook(
                            sourcePath = pdfFile.absolutePath,
                            fallbackTitle = fileName.removeSuffix(".pdf"),
                            pdfFile = pdfFile
                        )
                    } else {
                        val epubDir = File(context.filesDir, "epubs")
                        if (!epubDir.exists()) epubDir.mkdirs()
                        val epubFile = File(epubDir, fileName)
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                epubFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        viewModel.importBookFromEpub(
                            sourcePath = epubFile.absolutePath,
                            fallbackTitle = fileName.removeSuffix(".epub"),
                            inputStreamProvider = {
                                epubFile.inputStream()
                            }
                        )
                    }
                }.onFailure { /* Errors handled globally */ }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.books.isEmpty()) {
            EmptyLibrary(
                contentPadding = contentPadding,
                isImporting = uiState.isImporting,
                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) }
            )
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onPullToRefresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    LibraryToolbar(
                        showSearch = uiState.showSearch,
                        onSearchToggle = { viewModel.onToggleSearch() },
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onFilterToggle = { viewModel.onToggleFilterSheet() },
                        statusFilter = uiState.statusFilter,
                        onStatusFilterChanged = { viewModel.onStatusFilterChanged(it) },
                        sortBy = uiState.sortBy,
                        onSortByChanged = { viewModel.onSortByChanged(it) },
                        isGridView = uiState.isGridView,
                        onViewToggle = { viewModel.onToggleView() }
                    )

                    BookGridSection(
                        books = searchedBooks,
                        readingMinutesByBook = uiState.readingMinutesByBook,
                        isGridView = uiState.isGridView,
                        onBookSelected = onBookSelected,
                        onBookLongPress = { book -> viewModel.requestDeleteBook(book) },
                        onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) },
                        onEdit = { book -> viewModel.requestEditBook(book) },
                        onMarkCompleted = { book -> viewModel.onMenuMarkCompleted(book) },
                        onMarkPlanToRead = { book -> viewModel.onMenuMarkPlanToRead(book) },
                        onShare = { book -> viewModel.onMenuShare(book) }
                    )
                }
            }
        }

        LibraryDialogs(
            bookToDelete = uiState.bookToDelete,
            onDismissDelete = { viewModel.dismissDeleteDialog() },
            onConfirmDelete = { viewModel.confirmDeleteBook() },
            bookToEdit = uiState.bookToEdit,
            editCoverUri = editCoverUri,
            onDismissEdit = { viewModel.dismissEditDialog() },
            onSaveEdit = { book, title, author, description ->
                scope.launch {
                    val coverBytes = editCoverUri?.let { uri ->
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                input.readBytes()
                            }
                        }
                    }
                    editCoverUri = null
                    viewModel.confirmEditBook(
                        book = book,
                        title = title,
                        author = author,
                        description = description,
                        coverBytes = coverBytes
                    )
                }
            },
            onChangeCover = { coverPickerLauncher.launch("image/*") }
        )

        // ── Sync status indicator (top-right; collects own state) ──
        LibrarySyncStatus(viewModel = viewModel)

        if (uiState.isImporting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        FilterSheetContent(
            showFilterSheet = uiState.showFilterSheet,
            filterFormat = uiState.filterFormat,
            onFormatSelected = { viewModel.onFilterFormatChanged(it) },
            onDismiss = { viewModel.onToggleFilterSheet() }
        )
    }
}

/**
 * Sync status indicator that collects its own state from [viewModel],
 * isolating recomposition to only the indicator when sync state changes.
 */
@Composable
private fun LibrarySyncStatus(viewModel: LibraryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState = when {
        uiState.syncError != null -> SyncState.Error(uiState.syncError!!)
        uiState.isSyncing -> SyncState.Running
        else -> SyncState.Idle
    }
    Box(modifier = Modifier.fillMaxSize()) {
        SyncStatusIndicator(
            syncState = syncState,
            pendingCount = uiState.pendingCount,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp)
        )
    }
}

@Composable
private fun EmptyLibrary(
    contentPadding: PaddingValues,
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        NextPageEmptyState(
            icon = Icons.AutoMirrored.Outlined.LibraryBooks,
            title = stringResource(R.string.library_empty),
            subtitle = stringResource(R.string.library_import_formats),
            modifier = Modifier.padding(NextPageDimens.spacingLg),
            action = {
                NextPageButton(
                    onClick = onImportClick,
                    enabled = !isImporting,
                    variant = NextPageButtonVariant.TEXT
                ) {
                    Text(text = stringResource(R.string.library_import_book))
                }
            }
        )
    }
}
