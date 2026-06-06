package com.nextpage.presentation.screen

import android.net.Uri
import java.io.File
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val snackbarHostState = remember { SnackbarHostState() }

    var statusFilter by remember { mutableStateOf("all") }
    var sortBy by remember { mutableStateOf("date_added") }
    var isGridView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val filteredByStatus = remember(uiState.books, statusFilter, uiState.readingMinutesByBook) {
        filterBooks(uiState.books, statusFilter, uiState.readingMinutesByBook)
    }

    val searchedBooks = remember(filteredByStatus, searchQuery) {
        if (searchQuery.isBlank()) filteredByStatus
        else filteredByStatus.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            (it.author?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val sortedBooks = remember(searchedBooks, sortBy) {
        sortBookList(searchedBooks, sortBy)
    }

    val epubPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            viewModel.importBookFromEpub(
                sourcePath = uri.toString(),
                fallbackTitle = uri.lastPathSegment,
                inputStreamProvider = {
                    context.contentResolver.openInputStream(uri)
                }
            )
        }
    )

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            val fileName = uri.lastPathSegment ?: "imported_${System.currentTimeMillis()}.pdf"
            val pdfDir = File(context.filesDir, "pdfs")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }
            val pdfFile = File(pdfDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                pdfFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            viewModel.importPdfBook(
                sourcePath = pdfFile.absolutePath,
                fallbackTitle = fileName.removeSuffix(".pdf"),
                pdfFile = pdfFile
            )
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
                onEpubClick = { epubPickerLauncher.launch(arrayOf("application/epub+zip")) },
                onPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
            )
        } else {
            LibraryBookshelfContent(
                contentPadding = contentPadding,
                books = uiState.books,
                readingMinutesByBook = uiState.readingMinutesByBook,
                statusFilter = statusFilter,
                onStatusFilterChanged = { statusFilter = it },
                sortBy = sortBy,
                onSortByChanged = { sortBy = it },
                isGridView = isGridView,
                onViewToggle = { isGridView = !isGridView },
                showSearch = showSearch,
                onSearchToggle = { showSearch = !showSearch },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onBookSelected = onBookSelected,
                onBookLongPress = { book -> viewModel.requestDeleteBook(book) },
                onEpubClick = { epubPickerLauncher.launch(arrayOf("application/epub+zip")) },
                onPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
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
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onEpubClick: () -> Unit,
    onPdfClick: () -> Unit
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
            onSearchQueryChange = onSearchQueryChange
        )

        // 2. Status Tabs
        StatusTabsRow(
            selectedTab = statusFilter,
            onTabSelected = onStatusFilterChanged
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Sort Row
        SortRowComposable(
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
            onEpubClick = onEpubClick,
            onPdfClick = onPdfClick
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 1: Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryHeader(
    showSearch: Boolean,
    onSearchToggle: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_library),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.library_search_placeholder)
                    )
                }
                IconButton(onClick = { /* TODO: filter */ }) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.library_filter_label)
                    )
                }
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 2: Status Tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusTabsRow(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tabs = listOf(
            "all" to R.string.library_tab_all,
            "reading" to R.string.library_tab_reading,
            "pending" to R.string.library_tab_pending,
            "completed" to R.string.library_tab_completed
        )
        tabs.forEach { (tabId, labelRes) ->
            val isSelected = selectedTab == tabId
            AssistChip(
                onClick = { onTabSelected(tabId) },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 3: Sort Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SortRowComposable(
    sortBy: String,
    onSortByChanged: (String) -> Unit,
    isGridView: Boolean,
    onViewToggle: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortLabelRes = when (sortBy) {
        "title" -> R.string.library_sort_title
        "author" -> R.string.library_sort_author
        "last_read" -> R.string.library_sort_last_read
        else -> R.string.library_sort_date_added
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_sort_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(text = stringResource(sortLabelRes))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_date_added)) },
                        onClick = {
                            onSortByChanged("date_added")
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_title)) },
                        onClick = {
                            onSortByChanged("title")
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_author)) },
                        onClick = {
                            onSortByChanged("author")
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_last_read)) },
                        onClick = {
                            onSortByChanged("last_read")
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { if (!isGridView) onViewToggle() }) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = "Grid view",
                    tint = if (isGridView) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { if (isGridView) onViewToggle() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ViewList,
                    contentDescription = "List view",
                    tint = if (!isGridView) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 4: Book Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookGridSection(
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onEpubClick: () -> Unit,
    onPdfClick: () -> Unit
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
        items(books, key = { it.id }) { book ->
            BookGridCard(
                book = book,
                minutesRead = readingMinutesByBook[book.id] ?: 0L,
                onClick = { onBookSelected(book.id, book.filePath, book.format) },
                onLongPress = { onBookLongPress(book) }
            )
        }
        item {
            AddBookCard(
                onEpubClick = onEpubClick,
                onPdfClick = onPdfClick
            )
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
        (minutesRead.toFloat() / 300f).coerceIn(0f, 1f)
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
// Add Book Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddBookCard(
    onEpubClick: () -> Unit,
    onPdfClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .dashedBorder(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                strokeWidth = 1.5.dp,
                cornerRadius = 12.dp,
                dashLength = 8.dp,
                gapLength = 4.dp
            )
            .clickable { showMenu = true },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.library_add_book),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.library_add_book),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_epub)) },
                onClick = {
                    showMenu = false
                    onEpubClick()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_pdf)) },
                onClick = {
                    showMenu = false
                    onPdfClick()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty Library (preserved)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyLibrary(
    contentPadding: PaddingValues,
    isImporting: Boolean,
    onEpubClick: () -> Unit,
    onPdfClick: () -> Unit
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
        TextButton(onClick = onEpubClick, enabled = !isImporting) {
            Text(text = stringResource(R.string.library_import_epub))
        }
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
        TextButton(onClick = onPdfClick, enabled = !isImporting) {
            Text(text = stringResource(R.string.library_import_pdf))
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun filterBooks(
    books: List<Book>,
    statusFilter: String,
    readingMinutesByBook: Map<String, Long>
): List<Book> {
    return when (statusFilter) {
        "reading" -> books.filter {
            (readingMinutesByBook[it.id] ?: 0L) > 0L
        }
        "pending" -> books.filter {
            (readingMinutesByBook[it.id] ?: 0L) == 0L
        }
        "completed" -> books.filter {
            (readingMinutesByBook[it.id] ?: 0L) >= 300L
        }
        else -> books
    }
}

private fun sortBookList(
    books: List<Book>,
    sortBy: String
): List<Book> {
    return when (sortBy) {
        "title" -> books.sortedBy { it.title }
        "author" -> books.sortedBy { it.author ?: "" }
        "last_read" -> books.sortedByDescending { it.updatedAtEpochMillis }
        else -> books // "date_added" — already in insertion order
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dashed Border Modifier
// ─────────────────────────────────────────────────────────────────────────────

private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 1.dp,
    cornerRadius: Dp = 12.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 4.dp
) = this.drawBehind {
    val rect = androidx.compose.ui.geometry.Rect(
        offset = androidx.compose.ui.geometry.Offset.Zero,
        size = size
    )
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = rect,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
            )
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                0f
            )
        )
    )
}
