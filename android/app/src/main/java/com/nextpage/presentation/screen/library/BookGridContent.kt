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
    headerContent: (@Composable () -> Unit)? = null
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
            headerContent = headerContent
        )
    } else {
        BookList(
            books = books,
            readingMinutesByBook = readingMinutesByBook,
            onBookSelected = onBookSelected,
            onBookLongPress = onBookLongPress,
            onEdit = onEdit,
            onMarkCompleted = onMarkCompleted,
            onMarkPlanToRead = onMarkPlanToRead,
            onShare = onShare,
            headerContent = headerContent
        )
    }
}

/**
 * Grid view: ONE shared [LazyVerticalStaggeredGrid] where the Disponibles
 * section (header) is the full-span first item and scrolls away with the grid
 * (D7). No pinned Column.
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
    headerContent: (@Composable () -> Unit)? = null
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
        if (headerContent != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "downloadable_header", contentType = { "header" }) {
                headerContent()
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
        item(key = "add_book", contentType = { "add" }) {
            AddBookCard(onImportClick = onImportClick)
        }
    }
}