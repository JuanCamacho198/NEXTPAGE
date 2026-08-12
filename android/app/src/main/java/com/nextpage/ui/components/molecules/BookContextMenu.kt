package com.nextpage.ui.components.molecules

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageTheme

/**
 * Dropdown menu of book-level actions (edit, mark completed, mark
 * plan-to-read, share, delete). Renders a Material 3 `DropdownMenu`
 * with five items; "Remove" is visually separated by a divider and
 * tinted in `colorScheme.error`.
 *
 * @param expanded Whether the menu is currently visible. Bind this to
 *   the `showMenu` state from the caller.
 * @param onDismissRequest Invoked on outside-tap, scrim-tap, or
 *   back-press. Also called by every item before its own callback so
 *   the menu always closes after a selection.
 * @param onEdit Edit-metadata action. Called after the menu auto-dismisses.
 * @param onMarkCompleted Move the book to the "completed" status.
 * @param onMarkPlanToRead Move the book to the "plan to read" status.
 * @param onShare Share the book (open the system share sheet).
 * @param onDelete Remove the book from the library. Item label is
 *   tinted `colorScheme.error` to signal destructive intent.
 *
 * **Visual**: standard Material 3 `DropdownMenu`. Items in order:
 * Edit metadata, Mark completed, Mark plan to read, Share,
 * `HorizontalDivider`, Remove. The "Remove" label is `colorScheme.error`.
 * **Behavior**: each item calls `onDismissRequest()` first, then its
 * specific callback. The menu is uncontrolled — visibility is fully
 * driven by [expanded].
 * **Recomposition**: recomposes when any callback or [expanded] changes.
 */
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

@Preview(showBackground = true)
@Composable
private fun BookContextMenuDarkPreview() {
    NextPageTheme(darkTheme = true) {
        BookContextMenu(
            expanded = true,
            onDismissRequest = {},
            onEdit = {},
            onMarkCompleted = {},
            onMarkPlanToRead = {},
            onShare = {},
            onDelete = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BookContextMenuLightPreview() {
    NextPageTheme(darkTheme = false) {
        BookContextMenu(
            expanded = true,
            onDismissRequest = {},
            onEdit = {},
            onMarkCompleted = {},
            onMarkPlanToRead = {},
            onShare = {},
            onDelete = {}
        )
    }
}
