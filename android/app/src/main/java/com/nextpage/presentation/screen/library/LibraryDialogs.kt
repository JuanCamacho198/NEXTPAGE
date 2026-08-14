package com.nextpage.presentation.screen.library

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.ui.components.atoms.NextPageDialog
import com.nextpage.ui.components.atoms.NextPageDialogVariant
import com.nextpage.ui.components.molecules.EditBookMetadataDialog

@Composable
fun LibraryDialogs(
    bookToDelete: Book?,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    bookToEdit: Book?,
    editCoverUri: Uri?,
    onDismissEdit: () -> Unit,
    onSaveEdit: (book: Book, title: String, author: String?, description: String?) -> Unit,
    onChangeCover: () -> Unit
) {
    bookToDelete?.let { selectedBook ->
        NextPageDialog(
            title = stringResource(R.string.library_delete_title),
            body = stringResource(R.string.library_delete_message, selectedBook.title),
            confirmText = stringResource(R.string.library_delete_confirm),
            dismissText = stringResource(R.string.reader_cancel),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            variant = NextPageDialogVariant.DESTRUCTIVE
        )
    }

    bookToEdit?.let { book ->
        EditBookMetadataDialog(
            book = book,
            selectedCoverUri = editCoverUri,
            onDismiss = onDismissEdit,
            onSave = { title, author, description ->
                onSaveEdit(book, title, author, description)
            },
            onChangeCover = onChangeCover
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────

private val PreviewBook = Book(
    id = "preview-book-1",
    title = "The Hobbit",
    author = "J.R.R. Tolkien",
    coverPath = null,
    filePath = "/preview/the-hobbit.epub",
    format = "epub",
    updatedAtEpochMillis = 0L
)

@Preview(showBackground = true)
@Composable
private fun LibraryDialogsDarkPreview() {
    NextPageTheme(darkTheme = true) {
        LibraryDialogs(
            bookToDelete = PreviewBook,
            onDismissDelete = {},
            onConfirmDelete = {},
            bookToEdit = null,
            editCoverUri = null,
            onDismissEdit = {},
            onSaveEdit = { _, _, _, _ -> },
            onChangeCover = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryDialogsLightPreview() {
    NextPageTheme(darkTheme = false) {
        LibraryDialogs(
            bookToDelete = PreviewBook,
            onDismissDelete = {},
            onConfirmDelete = {},
            bookToEdit = null,
            editCoverUri = null,
            onDismissEdit = {},
            onSaveEdit = { _, _, _, _ -> },
            onChangeCover = {}
        )
    }
}
