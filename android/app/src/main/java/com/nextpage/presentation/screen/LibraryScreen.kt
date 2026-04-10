package com.nextpage.presentation.screen

import android.net.Uri
import android.graphics.BitmapFactory
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.LibraryImportEvent
import com.nextpage.presentation.viewmodel.LibraryUiEvent
import com.nextpage.presentation.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onBookSelected: (String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var layoutMode by remember { mutableStateOf(LibraryLayoutMode.LIST) }

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
            LibraryCollection(
                books = uiState.books,
                contentPadding = contentPadding,
                isImporting = uiState.isImporting,
                layoutMode = layoutMode,
                onLayoutModeChanged = { layoutMode = it },
                onBookSelected = onBookSelected,
                onBookLongPress = { book -> viewModel.requestDeleteBook(book) },
                totalMinutesRead = uiState.totalMinutesRead,
                readingMinutesByBook = uiState.readingMinutesByBook,
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

@Composable
private fun LibraryCollection(
    books: List<Book>,
    contentPadding: PaddingValues,
    isImporting: Boolean,
    layoutMode: LibraryLayoutMode,
    onLayoutModeChanged: (LibraryLayoutMode) -> Unit,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    totalMinutesRead: Long,
    readingMinutesByBook: Map<String, Long>,
    onEpubClick: () -> Unit,
    onPdfClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = NextPageDimens.spacingMd, vertical = NextPageDimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)) {
            Button(onClick = onEpubClick, enabled = !isImporting, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.library_import_epub))
            }
            Button(onClick = onPdfClick, enabled = !isImporting, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.library_import_pdf))
            }
            Button(
                onClick = { onLayoutModeChanged(LibraryLayoutMode.LIST) },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.library_layout_list))
            }
            Button(
                onClick = { onLayoutModeChanged(LibraryLayoutMode.GRID) },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.library_layout_grid))
            }
        }

        Text(
            text = stringResource(R.string.library_total_minutes_read, totalMinutesRead),
            style = MaterialTheme.typography.bodyMedium
        )

        if (layoutMode == LibraryLayoutMode.LIST) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
            ) {
                items(books, key = { book -> book.id }) { book ->
                    LibraryBookItem(
                        book = book,
                        minutesRead = readingMinutesByBook[book.id] ?: 0L,
                        onClick = { onBookSelected(book.id, book.filePath, book.format) },
                        onLongPress = { onBookLongPress(book) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm),
                horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
            ) {
                items(books, key = { book -> book.id }) { book ->
                    LibraryBookGridItem(
                        book = book,
                        minutesRead = readingMinutesByBook[book.id] ?: 0L,
                        onClick = { onBookSelected(book.id, book.filePath, book.format) },
                        onLongPress = { onBookLongPress(book) }
                    )
                }
            }
        }
    }
}

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
        Button(
            onClick = onEpubClick,
            enabled = !isImporting
        ) {
            Text(text = stringResource(R.string.library_import_epub))
        }
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
        Button(
            onClick = onPdfClick,
            enabled = !isImporting
        ) {
            Text(text = stringResource(R.string.library_import_pdf))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LibraryBookItem(
    book: Book,
    minutesRead: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        tonalElevation = NextPageDimens.spacingXs,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column(
            modifier = Modifier.padding(NextPageDimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingXs)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)) {
                CoverThumbnail(
                    coverPath = book.coverPath,
                    modifier = Modifier.size(56.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingXs)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = book.author ?: stringResource(R.string.library_author_unknown),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.library_last_updated,
                        book.updatedAtEpochMillis.toString()
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.library_open),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = stringResource(R.string.library_item_minutes_read, minutesRead),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LibraryBookGridItem(
    book: Book,
    minutesRead: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        tonalElevation = NextPageDimens.spacingXs,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column(
            modifier = Modifier.padding(NextPageDimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CoverThumbnail(
                coverPath = book.coverPath,
                modifier = Modifier.size(120.dp)
            )
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = book.author ?: stringResource(R.string.library_author_unknown),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.library_item_minutes_read, minutesRead),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CoverThumbnail(coverPath: String?, modifier: Modifier = Modifier) {
    val imageBitmap = remember(coverPath) {
        if (coverPath.isNullOrBlank()) {
            null
        } else {
            BitmapFactory.decodeFile(coverPath)?.asImageBitmap()
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.small)
        )
    } else {
        Surface(
            shape = MaterialTheme.shapes.small,
            tonalElevation = NextPageDimens.spacingXs,
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.library_cover_placeholder),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(NextPageDimens.spacingXs)
                )
            }
        }
    }
}

private enum class LibraryLayoutMode {
    LIST,
    GRID
}
