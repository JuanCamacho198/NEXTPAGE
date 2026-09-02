package com.nextpage.presentation.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.nextpage.domain.repository.LibraryRepository

@Composable
fun BookDetailScreen(
    contentPadding: PaddingValues,
    bookId: String,
    libraryRepository: LibraryRepository,
    onNavigateBack: () -> Unit,
    onEditBook: () -> Unit,
    onContinueReading: (String, String?, String) -> Unit
) {
    com.nextpage.presentation.feature.bookdetail.BookDetailScreen(
        contentPadding = contentPadding,
        bookId = bookId,
        libraryRepository = libraryRepository,
        onNavigateBack = onNavigateBack,
        onEditBook = onEditBook,
        onContinueReading = onContinueReading
    )
}
