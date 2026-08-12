package com.nextpage.presentation.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nextpage.domain.model.Book
import com.nextpage.ui.components.molecules.AddBookCard

@Composable
fun BookGridSection(
    books: List<Book>,
    readingMinutesByBook: Map<String, Long>,
    isGridView: Boolean,
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
    if (isGridView) {
        BookGrid(
            books = books,
            readingMinutesByBook = readingMinutesByBook,
            onBookSelected = onBookSelected,
            onBookLongPress = onBookLongPress,
            onImportClick = onImportClick,
            onEdit = onEdit,
            onMarkCompleted = onMarkCompleted,
            onMarkPlanToRead = onMarkPlanToRead,
            onShare = onShare,
            emptyContent = emptyContent,
            footerContent = footerContent
        )
    } else {
        BookList(
            books = books,
            readingMinutesByBook = readingMinutesByBook,
            onBookSelected = onBookSelected,
            onBookLongPress = onBookLongPress,
            onImportClick = onImportClick,
            onEdit = onEdit,
            onMarkCompleted = onMarkCompleted,
            onMarkPlanToRead = onMarkPlanToRead,
            onShare = onShare,
            emptyContent = emptyContent,
            footerContent = footerContent
        )
    }
}

/**
 * Grid view: ONE shared [LazyVerticalStaggeredGrid] where the empty placeholder
 * (when the shelf is empty) and the Disponibles section (footer, AFTER local
 * books) are full-span items that scroll away with the grid. The add-book card
 * is always the last item. No pinned Column.
 */
@Composable
fun BookGrid(
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
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (books.isEmpty() && emptyContent != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "empty_state", contentType = { "empty" }) {
                emptyContent()
            }
        }
        items(books, key = { it.id }, contentType = { "book" }) { book ->
            BookGridCard(
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
            item(span = StaggeredGridItemSpan.FullLine, key = "downloadable_footer", contentType = { "footer" }) {
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