package com.nextpage.presentation.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.nextpage.data.storage.CoverStorage
import com.nextpage.domain.repository.LibraryRepository

@Composable
fun EditBookMetadataScreen(
    contentPadding: PaddingValues,
    bookId: String,
    libraryRepository: LibraryRepository,
    coverStorage: CoverStorage,
    onNavigateBack: () -> Unit
) {
    com.nextpage.presentation.feature.editmetadata.EditBookMetadataScreen(
        contentPadding = contentPadding,
        bookId = bookId,
        libraryRepository = libraryRepository,
        coverStorage = coverStorage,
        onNavigateBack = onNavigateBack
    )
}
