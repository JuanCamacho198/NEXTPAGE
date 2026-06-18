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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextpage.R
import com.nextpage.domain.model.Book
import java.io.File

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
