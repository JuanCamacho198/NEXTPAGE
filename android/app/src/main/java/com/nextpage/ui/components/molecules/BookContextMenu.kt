package com.nextpage.ui.components.molecules

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nextpage.R

@Composable
fun BookContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onMarkCompleted: () -> Unit,
    onMarkPlanToRead: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_menu_edit_metadata)) },
            onClick = {
                onDismissRequest()
                onEdit()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_menu_mark_completed)) },
            onClick = {
                onDismissRequest()
                onMarkCompleted()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_menu_mark_plan_to_read)) },
            onClick = {
                onDismissRequest()
                onMarkPlanToRead()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_menu_share)) },
            onClick = {
                onDismissRequest()
                onShare()
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.library_menu_remove),
                    color = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDismissRequest()
                onDelete()
            }
        )
    }
}
