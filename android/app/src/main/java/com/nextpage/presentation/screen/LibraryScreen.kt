package com.nextpage.presentation.screen

import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.data.remote.supabase.UserBookRow
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.domain.model.Book
import com.nextpage.presentation.screen.library.BookGridSection
import com.nextpage.ui.icons.NextPageIcons
import com.nextpage.presentation.screen.library.FilterSheetContent
import com.nextpage.presentation.screen.library.LibraryDialogs
import com.nextpage.presentation.screen.library.LibraryToolbar
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.util.getContentDisplayName
import com.nextpage.presentation.viewmodel.DownloadState
import com.nextpage.presentation.viewmodel.LibraryUiState
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.ui.components.atoms.CoverThumbnail
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
    val firstDownloadError by viewModel.firstDownloadError.collectAsState()

    LibraryScreenContent(
        uiState = uiState,
        searchedBooks = searchedBooks,
        firstDownloadError = firstDownloadError,
        contentPadding = contentPadding,
        onBookSelected = onBookSelected,
        onRefresh = viewModel::onPullToRefresh,
        onSearchToggle = viewModel::onToggleSearch,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onFilterToggle = viewModel::onToggleFilterSheet,
        onStatusFilterChanged = viewModel::onStatusFilterChanged,
        onSortByChanged = viewModel::onSortByChanged,
        onViewToggle = viewModel::onToggleView,
        onRequestDeleteBook = viewModel::requestDeleteBook,
        onRequestEditBook = viewModel::requestEditBook,
        onMarkCompleted = viewModel::onMenuMarkCompleted,
        onMarkPlanToRead = viewModel::onMenuMarkPlanToRead,
        onShare = viewModel::onMenuShare,
        onDownload = viewModel::downloadBook,
        onDismissDownloadError = viewModel::dismissDownloadError,
        onDismissDelete = viewModel::dismissDeleteDialog,
        onConfirmDelete = viewModel::confirmDeleteBook,
        onDismissEdit = viewModel::dismissEditDialog,
        onConfirmEditBook = viewModel::confirmEditBook,
        onFormatSelected = viewModel::onFilterFormatChanged,
        onImportPdf = viewModel::importPdfBook,
        onImportEpub = viewModel::importBookFromEpub
    )
}

@Composable
private fun LibraryScreenContent(
    uiState: LibraryUiState,
    searchedBooks: List<Book>,
    firstDownloadError: DownloadState.Error?,
    contentPadding: PaddingValues,
    onBookSelected: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterToggle: () -> Unit,
    onStatusFilterChanged: (String) -> Unit,
    onSortByChanged: (String) -> Unit,
    onViewToggle: () -> Unit,
    onRequestDeleteBook: (Book) -> Unit,
    onRequestEditBook: (Book) -> Unit,
    onMarkCompleted: (Book) -> Unit,
    onMarkPlanToRead: (Book) -> Unit,
    onShare: (Book) -> Unit,
    onDownload: (String) -> Unit,
    onDismissDownloadError: (String) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissEdit: () -> Unit,
    onConfirmEditBook: (
        book: Book,
        title: String,
        author: String?,
        description: String?,
        coverBytes: ByteArray?
    ) -> Unit,
    onFormatSelected: (String) -> Unit,
    onImportPdf: (sourcePath: String, fallbackTitle: String?, pdfFile: File) -> Unit,
    onImportEpub: (
        sourcePath: String,
        fallbackTitle: String?,
        inputStreamProvider: suspend () -> InputStream?
    ) -> Unit
) {
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
                        onImportPdf(
                            pdfFile.absolutePath,
                            fileName.removeSuffix(".pdf"),
                            pdfFile
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
                        onImportEpub(
                            epubFile.absolutePath,
                            fileName.removeSuffix(".epub"),
                            {
                                epubFile.inputStream()
                            }
                        )
                    }
                }.onFailure { /* Errors handled globally */ }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                LibraryToolbar(
                    showSearch = uiState.showSearch,
                    onSearchToggle = onSearchToggle,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onFilterToggle = onFilterToggle,
                    statusFilter = uiState.statusFilter,
                    onStatusFilterChanged = onStatusFilterChanged,
                    sortBy = uiState.sortBy,
                    onSortByChanged = onSortByChanged,
                    isGridView = uiState.isGridView,
                    onViewToggle = onViewToggle
                )

                BookGridSection(
                    books = searchedBooks,
                    readingMinutesByBook = uiState.readingMinutesByBook,
                    isGridView = uiState.isGridView,
                    onBookSelected = onBookSelected,
                    onBookLongPress = onRequestDeleteBook,
                    onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) },
                    onEdit = onRequestEditBook,
                    onMarkCompleted = onMarkCompleted,
                    onMarkPlanToRead = onMarkPlanToRead,
                    onShare = onShare,
                    emptyContent = if (uiState.books.isEmpty()) {
                        {
                            EmptyShelfPlaceholder(
                                isImporting = uiState.isImporting,
                                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) }
                            )
                        }
                    } else {
                        null
                    },
                    footerContent = {
                        DownloadableBooksSection(
                            books = uiState.downloadableBooks,
                            downloadStateMap = uiState.downloadState,
                            firstError = firstDownloadError,
                            onDownload = onDownload,
                            onDismissError = onDismissDownloadError
                        )
                    }
                )
            }
        }

        LibraryDialogs(
            bookToDelete = uiState.bookToDelete,
            onDismissDelete = onDismissDelete,
            onConfirmDelete = onConfirmDelete,
            bookToEdit = uiState.bookToEdit,
            editCoverUri = editCoverUri,
            onDismissEdit = onDismissEdit,
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
                    onConfirmEditBook(
                        book,
                        title,
                        author,
                        description,
                        coverBytes
                    )
                }
            },
            onChangeCover = { coverPickerLauncher.launch("image/*") }
        )

        // ── Sync status indicator (top-right) ──
        LibrarySyncStatus(syncError = uiState.syncError, isSyncing = uiState.isSyncing)

        FilterSheetContent(
            showFilterSheet = uiState.showFilterSheet,
            filterFormat = uiState.filterFormat,
            onFormatSelected = onFormatSelected,
            onDismiss = onFilterToggle
        )
    }
}

/**
 * Sync status indicator that takes the sync state values it renders directly,
 * isolating recomposition to only the indicator when sync state changes.
 */
@Composable
private fun LibrarySyncStatus(
    syncError: String?,
    isSyncing: Boolean
) {
    val syncState = when {
        syncError != null -> SyncState.Error(syncError)
        isSyncing -> SyncState.Running
        else -> SyncState.Idle
    }
    Box(modifier = Modifier.fillMaxSize()) {
        SyncStatusIndicator(
            syncState = syncState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp)
        )
    }
}

/**
 * Section showing books available from other devices.
 * Rendered as a footer AFTER local books (or after the empty placeholder when
 * the shelf is empty) inside the shared scroll container. Hidden when there
 * are no downloadable books.
 */
@Composable
private fun DownloadableBooksSection(
    books: List<UserBookRow>,
    downloadStateMap: Map<String, DownloadState>,
    firstError: DownloadState.Error?,
    onDownload: (bookId: String) -> Unit,
    onDismissError: (bookId: String) -> Unit
) {
    if (firstError != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = firstError.message,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            IconButton(
                onClick = { onDismissError(firstError.bookId) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = NextPageIcons.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        }
    }

    if (books.isEmpty()) return

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.book_available_from_other_device),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.library_count, books.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books, key = { it.id }) { row ->
                DownloadableBookCard(
                    book = row,
                    isDownloading = downloadStateMap[row.id] is DownloadState.Downloading,
                    onDownload = { onDownload(row.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * A single downloadable book card with cover thumbnail, title, author, source label,
 * and download button. Matches [BookGridCard] styling for a consistent catalog look.
 */
@Composable
private fun DownloadableBookCard(
    book: UserBookRow,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    val sourceLabel = when (book.sourceDevice) {
        "android" -> stringResource(R.string.book_download_source_android)
        "desktop" -> stringResource(R.string.book_download_source_desktop)
        else -> stringResource(R.string.book_download_source_unknown)
    }

    Card(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            CoverThumbnail(
                coverPath = book.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author != null) {
                    Text(
                        text = book.author,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = sourceLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterHorizontally),
                        strokeWidth = 2.dp
                    )
                } else {
                    FilledTonalButton(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = NextPageIcons.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.book_download),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inline empty-shelf placeholder rendered INSIDE the shared scroll container
 * (as the first item of the grid/list) when the local shelf is empty. Full
 * width and centered; no own scroll container — pull-to-refresh stays active.
 */
@Composable
private fun EmptyShelfPlaceholder(
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    NextPageEmptyState(
        icon = NextPageIcons.LibraryBooks,
        title = stringResource(R.string.library_empty),
        subtitle = stringResource(R.string.library_import_formats),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NextPageDimens.spacingLg),
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

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    val sampleBook = Book(
        id = "book-1",
        title = "Clean Code",
        author = "Robert C. Martin",
        coverPath = null,
        filePath = "/books/clean-code.epub",
        format = "epub",
        updatedAtEpochMillis = 1L
    )
    LibraryScreenContent(
        uiState = LibraryUiState(
            books = listOf(sampleBook),
            isLoading = false,
            isGridView = true,
            readingMinutesByBook = mapOf("book-1" to 40L),
            isSyncing = false,
            isRefreshing = false,
            syncError = null,
            downloadableBooks = emptyList(),
            downloadState = emptyMap(),
            statusFilter = "all",
            sortBy = "date_added",
            searchQuery = "",
            debouncedSearchQuery = "",
            showSearch = false,
            showFilterSheet = false,
            filterFormat = "all"
        ),
        searchedBooks = listOf(sampleBook),
        firstDownloadError = null,
        contentPadding = PaddingValues(16.dp),
        onBookSelected = { _, _, _ -> },
        onRefresh = {},
        onSearchToggle = {},
        onSearchQueryChange = {},
        onFilterToggle = {},
        onStatusFilterChanged = {},
        onSortByChanged = {},
        onViewToggle = {},
        onRequestDeleteBook = {},
        onRequestEditBook = {},
        onMarkCompleted = {},
        onMarkPlanToRead = {},
        onShare = {},
        onDownload = {},
        onDismissDownloadError = {},
        onDismissDelete = {},
        onConfirmDelete = {},
        onDismissEdit = {},
        onConfirmEditBook = { _, _, _, _, _ -> },
        onFormatSelected = {},
        onImportPdf = { _, _, _ -> },
        onImportEpub = { _, _, _ -> }
    )
}
