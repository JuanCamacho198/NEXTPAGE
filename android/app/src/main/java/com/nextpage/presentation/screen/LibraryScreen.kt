package com.nextpage.presentation.screen

import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.util.getContentDisplayName
import com.nextpage.presentation.viewmodel.LibraryImportEvent
import com.nextpage.presentation.viewmodel.LibraryUiEvent
import com.nextpage.presentation.viewmodel.LibraryViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.nextpage.ui.components.molecules.FilterBottomSheet
import com.nextpage.ui.components.molecules.LibraryHeader
import com.nextpage.ui.components.molecules.StatusChipRow
import com.nextpage.ui.components.molecules.SortControlRow
import com.nextpage.ui.components.molecules.AddBookCard

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onBookSelected: (String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchedBooks = uiState.searchedBooks

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

                    if (!isPdf && !isEpub) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.library_import_unsupported)
                        )
                        return@runCatching
                    }

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
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.library_import_failure, error.message ?: "Unknown error")
                    )
                }
            }
        }
    )

    LaunchedEffect(viewModel) {
        viewModel.importEvents.collect { event ->
            val message = when (event) {
                is LibraryImportEvent.Success -> context.getString(
                    R.string.library_import_success,
                    event.title
                )
                is LibraryImportEvent.Failure -> context.getString(
                    R.string.library_import_failure,
                    event.message
                )
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            val message = when (event) {
                is LibraryUiEvent.Success -> event.message
                is LibraryUiEvent.Failure -> event.message
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.books.isEmpty()) {
            EmptyLibrary(
                contentPadding = contentPadding,
                isImporting = uiState.isImporting,
                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) }
            )
        } else {
            LibraryBookshelfContent(
                contentPadding = contentPadding,
                books = searchedBooks,
                readingMinutesByBook = uiState.readingMinutesByBook,
                statusFilter = uiState.statusFilter,
                onStatusFilterChanged = { viewModel.onStatusFilterChanged(it) },
                sortBy = uiState.sortBy,
                onSortByChanged = { viewModel.onSortByChanged(it) },
                isGridView = uiState.isGridView,
                onViewToggle = { viewModel.onToggleView() },
                showSearch = uiState.showSearch,
                onSearchToggle = { viewModel.onToggleSearch() },
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                onFilterToggle = { viewModel.onToggleFilterSheet() },
                onBookSelected = onBookSelected,
                onBookLongPress = { book -> viewModel.requestDeleteBook(book) },
                onImportClick = { importLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) }
            )
        }

        uiState.bookToDelete?.let { selectedBook ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteDialog() },
                title = { Text(text = stringResource(R.string.library_delete_title)) },
                text = {
                    Text(
                        text = stringResource(R.string.library_delete_message, selectedBook.title)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDeleteBook() }) {
                        Text(text = stringResource(R.string.library_delete_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                        Text(text = stringResource(R.string.reader_cancel))
                    }
                }
            )
        }

        if (uiState.isImporting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ── Filter Bottom Sheet ──
        if (uiState.showFilterSheet) {
            FilterBottomSheet(
                selectedFormat = uiState.filterFormat,
                onFormatSelected = { viewModel.onFilterFormatChanged(it) },
                onDismiss = { viewModel.onToggleFilterSheet() }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(NextPageDimens.spacingMd)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bookshelf Content (non-empty state)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryBookshelfContent(
    contentPadding: PaddingValues,
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    statusFilter: String,
    onStatusFilterChanged: (String) -> Unit,
    sortBy: String,
    onSortByChanged: (String) -> Unit,
    isGridView: Boolean,
    onViewToggle: () -> Unit,
    showSearch: Boolean,
    onSearchToggle: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterToggle: () -> Unit,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // 1. Header
        LibraryHeader(
            showSearch = showSearch,
            onSearchToggle = onSearchToggle,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onFilterToggle = onFilterToggle
        )

        // 2. Status Tabs
        StatusChipRow(
            selectedTab = statusFilter,
            onTabSelected = onStatusFilterChanged
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Sort Row
        SortControlRow(
            sortBy = sortBy,
            onSortByChanged = onSortByChanged,
            isGridView = isGridView,
            onViewToggle = onViewToggle
        )

        Spacer(modifier = Modifier.height(12.dp))            // 4. Book Grid - books are already filtered/sorted from parent
        BookGridSection(
            books = books,
            readingMinutesByBook = readingMinutesByBook,
            isGridView = isGridView,
            onBookSelected = onBookSelected,
            onBookLongPress = onBookLongPress,
            onImportClick = onImportClick
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 4: Book Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookGridSection(
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    isGridView: Boolean,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onImportClick: () -> Unit
) {
    if (isGridView) {
        BookGrid(
            books = books,
            readingMinutesByBook = readingMinutesByBook,
            onBookSelected = onBookSelected,
            onBookLongPress = onBookLongPress,
            onImportClick = onImportClick
        )
    } else {
        BookList(
            books = books,
            readingMinutesByBook = readingMinutesByBook,
            onBookSelected = onBookSelected,
            onBookLongPress = onBookLongPress
        )
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onImportClick: () -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(books, key = { it.id }, contentType = { "book" }) { book ->
            BookGridCard(
                book = book,
                minutesRead = readingMinutesByBook[book.id] ?: 0L,
                onClick = { onBookSelected(book.id, book.filePath, book.format) },
                onLongPress = { onBookLongPress(book) }
            )
        }
        item(key = "add_book", contentType = { "add" }) {
            AddBookCard(onImportClick = onImportClick)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Book List View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookList(
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookListCard(
                book = book,
                minutesRead = readingMinutesByBook[book.id] ?: 0L,
                onClick = { onBookSelected(book.id, book.filePath, book.format) },
                onLongPress = { onBookLongPress(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListCard(
    book: Book,
    minutesRead: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val progressFraction = if (minutesRead > 0L) {
        (minutesRead.toFloat() / READING_TARGET_MINUTES).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SURFACE_ALPHA)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Cover thumbnail
            CoverThumbnail(
                coverPath = book.coverPath,
                modifier = Modifier
                    .width(60.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author ?: stringResource(R.string.library_author_unknown),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progressFraction > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.library_status_in_progress, minutesRead),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Book Grid Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridCard(
    book: Book,
    minutesRead: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val progressFraction = if (minutesRead > 0L) {
        (minutesRead.toFloat() / READING_TARGET_MINUTES).coerceIn(0f, 1f)
    } else 0f

    val statusText = when {
        progressFraction >= 1f -> stringResource(R.string.library_status_completed)
        minutesRead > 0L -> stringResource(R.string.library_status_in_progress, minutesRead)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SURFACE_ALPHA))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        // Cover thumbnail
        CoverThumbnail(
            coverPath = book.coverPath,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        )

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title
            Text(
                text = book.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Author
            Text(
                text = book.author ?: stringResource(R.string.library_author_unknown),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Status and progress
            if (statusText != null) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (progressFraction in 0.01f..0.99f) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty Library
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyLibrary(
    contentPadding: PaddingValues,
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(NextPageDimens.spacingLg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.library_empty),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
        Text(
            text = stringResource(R.string.library_import_formats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(NextPageDimens.spacingMd))
        TextButton(onClick = onImportClick, enabled = !isImporting) {
            Text(text = stringResource(R.string.library_import_book))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cover Thumbnail (preserved)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CoverThumbnail(
    coverPath: String?,
    modifier: Modifier = Modifier,
    onImageState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    val coverFile = remember(coverPath) {
        coverPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
    }
    val imageRequest = remember(context, coverFile, coverPath) {
        ImageRequest.Builder(context)
            .data(coverFile)
            .placeholder(R.drawable.cover_placeholder)
            .error(R.drawable.cover_error)
            .fallback(R.drawable.cover_placeholder)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        onState = { state -> onImageState?.invoke(state) },
        contentDescription = stringResource(R.string.library_cover_content_description),
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small)
    )
}

private const val READING_TARGET_MINUTES = 300L
private const val SURFACE_ALPHA = 0.3f

