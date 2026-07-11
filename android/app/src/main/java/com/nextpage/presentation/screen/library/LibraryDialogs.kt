package com.nextpage.presentation.screen.library

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nextpage.R
import com.nextpage.domain.model.Book
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
