package com.nextpage.ui.components.atoms

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Material 3 `AlertDialog` with a fixed two-button layout
 * (confirm + dismiss). Wraps the buttons in `TextButton`s to match the
 * app's chrome.
 *
 * @param title Dialog title rendered as Material 3 default `headlineSmall`
 *   (the `AlertDialog` title slot).
 * @param body Dialog body text rendered in the `AlertDialog` text slot
 *   with Material 3 default body styling.
 * @param confirmText Label of the confirm `TextButton`.
 * @param dismissText Label of the dismiss `TextButton`.
 * @param onConfirm Invoked when the user taps the confirm button. NOT
 *   called on outside-tap or back-press — those go through [onDismiss].
 * @param onDismiss Invoked when the user taps the dismiss button,
 *   taps outside the dialog, or presses back. Use this to clear the
 *   dialog state in the ViewModel.
 * @param modifier Modifier applied to the underlying `AlertDialog`.
 * @param confirmColor Color of the [confirmText] label. Default
 *   `colorScheme.primary`. Use `colorScheme.error` for destructive
 *   confirms.
 *
 * **Visual**: standard Material 3 dialog with confirm on the right
 * and dismiss on the left (Material 3 reverses them in RTL locales).
 * **Behavior**: non-cancelable programmatically — the dialog can only
 * be dismissed via [onConfirm] or [onDismiss]. No icon slot.
 * **Recomposition**: recomposes when any string parameter, callback,
 * or `confirmColor` changes.
 */
@Composable
fun NextPageDialog(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmColor: Color = MaterialTheme.colorScheme.primary
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = confirmColor
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        }
    )
}
