package com.nextpage.presentation.screen

import android.app.Activity
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.nextpage.BuildConfig
import com.nextpage.R
import com.nextpage.data.remote.drive.DriveAuthResult
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.data.remote.supabase.UserBookRow
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.model.Book
import com.nextpage.presentation.screen.library.BookGridSection
import com.nextpage.ui.icons.NextPageIcons
import com.nextpage.presentation.screen.library.FilterSheetContent
import com.nextpage.presentation.screen.library.LibraryDialogs
import com.nextpage.presentation.screen.library.LibraryToolbar
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.util.getContentDisplayName
import com.nextpage.presentation.viewmodel.DownloadState
import com.nextpage.presentation.viewmodel.LibraryUiState
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.ui.components.atoms.CoverThumbnail
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageDialog
import com.nextpage.ui.components.atoms.NextPageDownloadOverlay
import com.nextpage.ui.components.atoms.NextPageEmptyState
import com.nextpage.ui.components.atoms.SyncStatusIndicator

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    driveAuthHelper: GoogleDriveAuthHelper,
    authSession: AuthSession?,
    onOpenAccount: () -> Unit,
    onBookSelected: (String, String, String) -> Unit,
    onEditBook: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchedBooks by viewModel.searchedBooks.collectAsStateWithLifecycle()
    val firstDownloadError by viewModel.firstDownloadError.collectAsStateWithLifecycle()

    LibraryScreenContent(
        uiState = uiState,
        searchedBooks = searchedBooks,
        firstDownloadError = firstDownloadError,
        contentPadding = contentPadding,
        driveAuthHelper = driveAuthHelper,
        authSession = authSession,
        onOpenAccount = onOpenAccount,
        onBookSelected = onBookSelected,
        onEditBook = onEditBook,
        onRefresh = viewModel::onPullToRefresh,
        onSearchToggle = viewModel::onToggleSearch,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onFilterToggle = viewModel::onToggleFilterSheet,
        onStatusFilterChanged = viewModel::onStatusFilterChanged,
        onSortByChanged = viewModel::onSortByChanged,
        onViewToggle = viewModel::onToggleView,
        onRequestDeleteBook = viewModel::requestDeleteBook,
        onMarkCompleted = viewModel::onMenuMarkCompleted,
        onMarkPlanToRead = viewModel::onMenuMarkPlanToRead,
        onShare = viewModel::onMenuShare,
        onDownload = viewModel::downloadBook,
        onDismissDownloadError = viewModel::dismissDownloadError,
        onDismissDelete = viewModel::dismissDeleteDialog,
        onConfirmDelete = viewModel::confirmDeleteBook,
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
    driveAuthHelper: GoogleDriveAuthHelper?,
    authSession: AuthSession?,
    onOpenAccount: () -> Unit,
    onBookSelected: (String, String, String) -> Unit,
    onEditBook: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterToggle: () -> Unit,
    onStatusFilterChanged: (String) -> Unit,
    onSortByChanged: (String) -> Unit,
    onViewToggle: () -> Unit,
    onRequestDeleteBook: (Book) -> Unit,
    onMarkCompleted: (Book) -> Unit,
    onMarkPlanToRead: (Book) -> Unit,
    onShare: (Book) -> Unit,
    onDownload: (String) -> Unit,
    onDismissDownloadError: (String) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
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

    // ── Drive connect gate for cloud downloads ──────────────────────
    // When the user taps Download on a cloud book but Drive is not
    // authorized, show a connect dialog; on accept, run the shared PKCE
    // browser flow inline and retry the pending download on success.
    var showDriveConnectDialog by remember { mutableStateOf(false) }
    var pendingDownloadId by remember { mutableStateOf<String?>(null) }
    var isAuthorizingDrive by remember { mutableStateOf(false) }

    val driveOauthErrorText = stringResource(R.string.settings_drive_error_oauth)
    val driveConfigErrorText = stringResource(R.string.settings_drive_error_config)

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Browser task closed without a redirect (user pressed back) — canceled.
        if (result.resultCode != Activity.RESULT_OK) {
            isAuthorizingDrive = false
        }
    }

    // Redirect-driven outcome (browser → MainActivity.onNewIntent → helper.onRedirect).
    LaunchedEffect(driveAuthHelper) {
        val helper = driveAuthHelper ?: return@LaunchedEffect
        helper.authResult.collect { result ->
            if (result != null) {
                isAuthorizingDrive = false
                when (result) {
                    is DriveAuthResult.Success -> {
                        // Authorized — retry the download that was blocked.
                        val bookId = pendingDownloadId
                        pendingDownloadId = null
                        if (bookId != null) {
                            onDownload(bookId)
                        }
                    }
                    is DriveAuthResult.Failure -> Toast.makeText(
                        context,
                        driveOauthErrorText,
                        Toast.LENGTH_SHORT
                    ).show()
                    DriveAuthResult.Canceled -> Unit // cancellation is not an error — no toast
                }
                helper.consumeResult()
            }
        }
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
                    onViewToggle = onViewToggle,
                    avatarImageUrl = authSession?.photoUrl,
                    avatarInitials = authSession?.displayName?.take(2)?.uppercase() ?: "NP",
                    onAvatarClick = onOpenAccount,
                    avatarContentDescription = stringResource(R.string.home_avatar_content_description)
                )

                BookGridSection(
                    books = searchedBooks,
                    readingMinutesByBook = uiState.readingMinutesByBook,
                    isGridView = uiState.isGridView,
                    onBookSelected = onBookSelected,
                    onBookLongPress = onRequestDeleteBook,
                    onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) },
                    onEdit = { book -> onEditBook(book.id) },
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
                            isLoading = uiState.isDownloadableLoading,
                            isDriveAuthorized = driveAuthHelper == null || driveAuthHelper.isAuthorized(),
                            onConnectDrive = { row ->
                                pendingDownloadId = row.id
                                showDriveConnectDialog = true
                            },
                            onConfirmDownload = onDownload
                        )
                    }
                )
            }
        }

        LibraryDialogs(
            bookToDelete = uiState.bookToDelete,
            onDismissDelete = onDismissDelete,
            onConfirmDelete = onConfirmDelete
        )

        // ── Cross-device download overlay ──────────────────────────────
        val activeDownload = uiState.downloadState.entries.firstOrNull { (_, state) ->
            state is DownloadState.Downloading || state is DownloadState.Success
        }
        val activeDownloadBook = activeDownload?.let { (id, _) ->
            uiState.downloadableBooks.firstOrNull { it.id == id }
        }
        if (activeDownload != null) {
            NextPageDownloadOverlay(
                bookTitle = (activeDownload.value as? DownloadState.Success)?.title
                    ?: activeDownloadBook?.title
                    ?: "",
                coverUrl = activeDownloadBook?.coverUrl,
                isCompleted = activeDownload.value is DownloadState.Success,
                visible = true
            )
        }

        // ── Sync status indicator (top-right) ──
        LibrarySyncStatus(syncError = uiState.syncError, isSyncing = uiState.isSyncing)

        FilterSheetContent(
            showFilterSheet = uiState.showFilterSheet,
            filterFormat = uiState.filterFormat,
            onFormatSelected = onFormatSelected,
            onDismiss = onFilterToggle
        )

        // ── Drive connect dialog (gate before cloud download) ────────
        if (showDriveConnectDialog) {
            NextPageDialog(
                title = stringResource(R.string.drive_connect_prompt_title),
                body = stringResource(R.string.drive_connect_prompt_body),
                confirmText = stringResource(R.string.drive_connect_prompt_accept),
                dismissText = stringResource(R.string.drive_connect_prompt_decline),
                icon = NextPageIcons.CloudDownload,
                onConfirm = {
                    showDriveConnectDialog = false
                    val helper = driveAuthHelper
                    val clientId = BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID
                    if (helper == null || clientId.isBlank()) {
                        Toast.makeText(
                            context,
                            driveConfigErrorText,
                            Toast.LENGTH_SHORT
                        ).show()
                        pendingDownloadId = null
                        return@NextPageDialog
                    }
                    isAuthorizingDrive = true
                    driveAuthLauncher.launch(helper.beginAuth())
                },
                onDismiss = {
                    showDriveConnectDialog = false
                    pendingDownloadId = null
                }
            )
        }
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
 *
 * Tapping Download gates through [isDriveAuthorized]: when Drive is not
 * connected the [onConnectDrive] callback is invoked; when authorized a
 * confirmation [NextPageDialog] is shown before [onConfirmDownload] runs.
 */
@Composable
private fun DownloadableBooksSection(
    books: List<UserBookRow>,
    downloadStateMap: Map<String, DownloadState>,
    isDriveAuthorized: Boolean,
    isLoading: Boolean,
    onConnectDrive: (UserBookRow) -> Unit,
    onConfirmDownload: (bookId: String) -> Unit
) {
    var pendingDownloadBook by remember { mutableStateOf<UserBookRow?>(null) }

    if (isLoading && books.isEmpty()) {
        // First catalog fetch still in flight — show a loading placeholder so the
        // cross-device section doesn't pop in ~5s after the screen renders.
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.book_available_from_other_device),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cloud_books_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
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
                    onDownload = {
                        if (isDriveAuthorized) {
                            pendingDownloadBook = row
                        } else {
                            onConnectDrive(row)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    pendingDownloadBook?.let { book ->
        NextPageDialog(
            title = stringResource(R.string.download_confirm_title),
            body = if (book.author.isNullOrBlank()) {
                stringResource(R.string.download_confirm_body_no_author, book.title)
            } else {
                stringResource(R.string.download_confirm_body, book.title, book.author)
            },
            confirmText = stringResource(R.string.book_download),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                onConfirmDownload(book.id)
                pendingDownloadBook = null
            },
            onDismiss = { pendingDownloadBook = null },
            icon = NextPageIcons.CloudDownload
        )
    }
}

/**
 * A single downloadable book card with cover thumbnail, title, author, source label,
 * and download button (replaced by a spinner + "Downloading…" label while in progress).
 * Matches [BookGridCard] styling for a consistent catalog look.
 */
@Composable
private fun DownloadableBookCard(
    book: UserBookRow,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            // Fixed cover box (2:3 ratio) so every card renders identically
            // regardless of the source image's intrinsic size or aspect.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                CoverThumbnail(
                    coverPath = book.coverUrl,
                    modifier = Modifier.matchParentSize()
                )
            }

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
                book.fileSize?.takeIf { it > 0 }?.let { bytes ->
                    Text(
                        text = formatFileSize(bytes),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDownloading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.book_downloading),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
 * Empty-shelf placeholder shown INSIDE the shared scroll container (as the
 * first item of the grid/list) when the local shelf is empty. Renders an
 * outlined "Import book" button so the empty state still offers the import
 * action without the add-book card (which is hidden when the shelf is empty).
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
            .padding(
                horizontal = NextPageDimens.spacingMd,
                vertical = NextPageDimens.spacingLg
            ),
        action = {
            NextPageButton(
                onClick = onImportClick,
                enabled = !isImporting,
                variant = NextPageButtonVariant.OUTLINED,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = stringResource(R.string.library_import_book))
            }
        }
    )
}

/**
 * Formats a file size in bytes as a compact MB string (e.g. "2.4 MB").
 */
private const val BYTES_PER_MB = 1024.0 * 1024.0

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / BYTES_PER_MB
    return if (mb >= 100) {
        "${mb.toInt()} MB"
    } else {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
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
            driveAuthHelper = null,
            authSession = null,
            onOpenAccount = {},
            onBookSelected = { _, _, _ -> },
            onEditBook = {},
            onRefresh = {},
            onSearchToggle = {},
            onSearchQueryChange = {},
            onFilterToggle = {},
            onStatusFilterChanged = {},
            onSortByChanged = {},
            onViewToggle = {},
            onRequestDeleteBook = {},
            onMarkCompleted = {},
            onMarkPlanToRead = {},
            onShare = {},
            onDownload = {},
            onDismissDownloadError = {},
            onDismissDelete = {},
            onConfirmDelete = {},
            onFormatSelected = {},
            onImportPdf = { _, _, _ -> },
            onImportEpub = { _, _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
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
            driveAuthHelper = null,
            authSession = null,
            onOpenAccount = {},
            onBookSelected = { _, _, _ -> },
            onEditBook = {},
            onRefresh = {},
            onSearchToggle = {},
            onSearchQueryChange = {},
            onFilterToggle = {},
            onStatusFilterChanged = {},
            onSortByChanged = {},
            onViewToggle = {},
            onRequestDeleteBook = {},
            onMarkCompleted = {},
            onMarkPlanToRead = {},
            onShare = {},
            onDownload = {},
            onDismissDownloadError = {},
            onDismissDelete = {},
            onConfirmDelete = {},
            onFormatSelected = {},
            onImportPdf = { _, _, _ -> },
            onImportEpub = { _, _, _ -> }
        )
    }
}
