package com.nextpage.ui.components.molecules

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nextpage.R

/**
 * Simple [AlertDialog] for adding or editing a tag on a highlight.
 *
 * Contains a single [OutlinedTextField] pre-filled with the current
 * tag value (if any), and "Cancelar" / "Guardar" action buttons.
 *
 * @param initialTag pre-filled tag text (empty string for new)
 * @param onSave invoked with the final tag text when "Guardar" is
 *  tapped (empty string results in null tag)
 * @param onDismiss invoked when "Cancelar" or backdrop is tapped
 */
@Composable
fun HighlightTagDialog(
    initialTag: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tag by remember(initialTag) { mutableStateOf(initialTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.tag_dialog_title))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = {
                        Text(text = stringResource(R.string.tag_dialog_label))
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tag) }) {
                Text(text = stringResource(R.string.reader_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.reader_cancel))
            }
        }
    )
}
