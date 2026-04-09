package com.nextpage.presentation.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.LibraryImportEvent
import com.nextpage.presentation.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onBookSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.books.isEmpty()) {
            EmptyLibrary(
                contentPadding = contentPadding,
                isImporting = uiState.isImporting,
                onImportClick = { epubPickerLauncher.launch(arrayOf("application/epub+zip")) }
            )
        } else {
            LibraryList(
                books = uiState.books,
                contentPadding = contentPadding,
                isImporting = uiState.isImporting,
                onBookSelected = onBookSelected,
                onImportClick = { epubPickerLauncher.launch(arrayOf("application/epub+zip")) }
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
private fun LibraryList(
    books: List<Book>,
    contentPadding: PaddingValues,
    isImporting: Boolean,
    onBookSelected: (String) -> Unit,
    onImportClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            horizontal = NextPageDimens.spacingMd,
            vertical = NextPageDimens.spacingMd
        ),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
    ) {
        item {
            Button(
                onClick = onImportClick,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.library_import_button))
            }
        }
        items(books, key = { book -> book.id }) { book ->
            LibraryBookItem(
                book = book,
                onClick = { onBookSelected(book.id) }
            )
        }
    }
}

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
        Button(
            onClick = onImportClick,
            enabled = !isImporting
        ) {
            Text(text = stringResource(R.string.library_import_button))
        }
    }
}

@Composable
private fun LibraryBookItem(book: Book, onClick: () -> Unit) {
    Surface(
        tonalElevation = NextPageDimens.spacingXs,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(NextPageDimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingXs)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = book.author ?: stringResource(R.string.library_author_unknown),
                style = MaterialTheme.typography.bodyMedium
            )
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
        }
    }
}
