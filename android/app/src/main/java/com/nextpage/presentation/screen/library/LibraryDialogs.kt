package com.nextpage.presentation.screen.library

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
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
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(text = stringResource(R.string.library_delete_title)) },
            text = {
                Text(
                    text = stringResource(R.string.library_delete_message, selectedBook.title)
                )
            },
            confirmButton = {
                NextPageButton(
                    onClick = onConfirmDelete,
                    variant = NextPageButtonVariant.TEXT
                ) {
                    Text(text = stringResource(R.string.library_delete_confirm))
                }
            },
            dismissButton = {
                NextPageButton(
                    onClick = onDismissDelete,
                    variant = NextPageButtonVariant.TEXT
                ) {
                    Text(text = stringResource(R.string.reader_cancel))
                }
            }
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
