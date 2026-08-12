package com.nextpage.ui.components.molecules

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageTheme
import java.io.File

/**
 * `AlertDialog` for editing a book's metadata (title, author,
 * description, cover). Owns its own local form state — initial values
 * come from the [book] parameter and are `remember`-ed keyed on the
 * book instance so swapping to a different book resets the form.
 *
 * @param book The book being edited. Provides the initial values for
 *   the form fields (title is required, author/description are
 *   optional and default to empty string).
 * @param selectedCoverUri Optional user-picked `Uri` for a new cover.
 *   When `null`, the dialog falls back to the book's existing
 *   `coverPath` (if any) and renders a blank thumbnail area.
 * @param onDismiss Invoked when the user taps outside the dialog,
 *   presses back, or taps "Cancel". Does NOT save the form.
 * @param onSave Invoked with `(title, author?, description?)` when the
 *   user taps "Save". Empty `author`/`description` are normalized to
 *   `null` before the call.
 * @param onChangeCover Invoked when the user taps "Change cover".
 *   The caller is expected to launch an image picker and pass the
 *   resulting URI back through [selectedCoverUri].
 *
 * **Visual**: standard Material 3 `AlertDialog` with a scrollable
 * body (in case of small phones) and a 72×96dp cover preview
 * thumbnail alongside a "Change cover" outlined button.
 * **Behavior**: the local form state is `remember(book)`-keyed, so
 * editing a different book resets all fields. Saving calls [onSave]
 * with the current values; the dialog does NOT auto-dismiss — the
 * caller should call [onDismiss] in response to [onSave] (typical
 * pattern in this codebase).
 * **Recomposition**: recomposes when `book` (different identity),
 * `selectedCoverUri`, or any callback changes.
 */
@Composable
fun EditBookMetadataDialog(
    book: Book,
    selectedCoverUri: Uri?,
    onDismiss: () -> Unit,
    onSave: (title: String, author: String?, description: String?) -> Unit,
    onChangeCover: () -> Unit
) {
    var title by remember(book) { mutableStateOf(book.title) }
    var author by remember(book) { mutableStateOf(book.author.orEmpty()) }
    var description by remember(book) { mutableStateOf(book.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.edit_metadata_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_metadata_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.edit_metadata_field_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.edit_metadata_field_synopsis)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.edit_metadata_field_cover),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val context = LocalContext.current
                    val coverModel = selectedCoverUri?.let { uri ->
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(120)
                            .crossfade(true)
                            .build()
                    } ?: book.coverPath?.let { path ->
                        if (path.isNotBlank()) {
                            ImageRequest.Builder(context)
                                .data(File(path))
                                .size(120)
                                .crossfade(true)
                                .build()
                        } else null
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp, 96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = stringResource(R.string.library_cover_content_description),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedButton(onClick = onChangeCover) {
                        Text(stringResource(R.string.edit_metadata_change_cover))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title, author.ifBlank { null }, description.ifBlank { null })
                }
            ) {
                Text(stringResource(R.string.edit_metadata_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun EditBookMetadataDialogDarkPreview() {
    NextPageTheme(darkTheme = true) {
        EditBookMetadataDialog(
            book = Book(
                id = "book-1",
                title = "The Old Man and the Sea",
                author = "Ernest Hemingway",
                coverPath = null,
                filePath = "/books/book-1.epub",
                format = "epub",
                description = "An old fisherman struggles against a giant marlin in the Gulf Stream.",
                updatedAtEpochMillis = 1_700_000_000_000L
            ),
            selectedCoverUri = null,
            onDismiss = {},
            onSave = { _, _, _ -> },
            onChangeCover = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditBookMetadataDialogLightPreview() {
    NextPageTheme(darkTheme = false) {
        EditBookMetadataDialog(
            book = Book(
                id = "book-1",
                title = "The Old Man and the Sea",
                author = "Ernest Hemingway",
                coverPath = null,
                filePath = "/books/book-1.epub",
                format = "epub",
                description = "An old fisherman struggles against a giant marlin in the Gulf Stream.",
                updatedAtEpochMillis = 1_700_000_000_000L
            ),
            selectedCoverUri = null,
            onDismiss = {},
            onSave = { _, _, _ -> },
            onChangeCover = {}
        )
    }
}
