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
import com.nextpage.ui.components.molecules.AddBookCard

/**
 * List view: ONE shared [LazyColumn] where the empty placeholder (when the
 * shelf is empty) renders first, then local books, then the Disponibles
 * section (footer, AFTER local books). The add-book card is always last.
 * No pinned Column.
 */
@Composable
fun BookList(
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    onBookSelected: (String, String, String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onImportClick: () -> Unit,
    onEdit: (Book) -> Unit,
    onMarkCompleted: (Book) -> Unit,
    onMarkPlanToRead: (Book) -> Unit,
    onShare: (Book) -> Unit,
    emptyContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (books.isEmpty() && emptyContent != null) {
            item(key = "empty_state", contentType = { "empty" }) {
                emptyContent()
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
        if (footerContent != null) {
            item(key = "downloadable_footer", contentType = { "footer" }) {
                footerContent()
            }
        }
        // The add-book card is the primary import affordance when the shelf
        // has books. When the shelf is empty, the empty-state placeholder
        // provides the import button instead, so the card is hidden.
        if (books.isNotEmpty()) {
            item(key = "add_book", contentType = { "add" }) {
                AddBookCard(onImportClick = onImportClick)
            }
        }
    }
}