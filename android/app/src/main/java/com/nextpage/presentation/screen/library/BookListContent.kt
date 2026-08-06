package com.nextpage.presentation.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nextpage.domain.model.Book

/**
 * List view: ONE shared [LazyColumn] where the Disponibles section (header)
 * is the first item and scrolls away with the list (D7). No pinned Column.
 */
@Composable
fun BookList(
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onEdit: (Book) -> Unit,
    onMarkCompleted: (Book) -> Unit,
    onMarkPlanToRead: (Book) -> Unit,
    onShare: (Book) -> Unit,
    headerContent: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (headerContent != null) {
            item(key = "downloadable_header", contentType = { "header" }) {
                headerContent()
            }
        }
        items(books, key = { it.id }) { book ->
            BookListCard(
                book = book,
                minutesRead = readingMinutesByBook[book.id] ?: 0L,
                onClick = { onBookSelected(book.id, book.filePath, book.format) },
                onLongPress = { onBookLongPress(book) },
                onEdit = { onEdit(book) },
                onMarkCompleted = { onMarkCompleted(book) },
                onMarkPlanToRead = { onMarkPlanToRead(book) },
                onShare = { onShare(book) }
            )
        }
    }
}