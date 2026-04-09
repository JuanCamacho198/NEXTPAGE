package com.nextpage.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    onBookSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.books.isEmpty()) {
        EmptyLibrary(contentPadding = contentPadding)
        return
    }

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
        items(uiState.books, key = { book -> book.id }) { book ->
            LibraryBookItem(
                book = book,
                onClick = { onBookSelected(book.id) }
            )
        }
    }
}

@Composable
private fun EmptyLibrary(contentPadding: PaddingValues) {
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
