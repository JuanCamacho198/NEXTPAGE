package com.nextpage.ui.components.atoms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextpage.ui.icons.NextPageIcons
import com.nextpage.presentation.theme.NextPageTheme

/**
 * Visual variant for [NextPageDialog] that drives the confirm button
 * color and icon tint automatically.
 */
enum class NextPageDialogVariant {
    /** Default variant — uses [MaterialTheme.colorScheme.primary]. */
    INFO,
    /** Destructive action variant — uses [MaterialTheme.colorScheme.error]. */
    DESTRUCTIVE,
    /** Success/positive variant — uses [MaterialTheme.colorScheme.primary]. */
    SUCCESS
}

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
 * @param icon Optional icon rendered centered above the title. Tint
 *   follows [variant] unless [confirmColor] is explicitly set.
 * @param variant Controls the confirm button color and icon tint.
 *   Default [NextPageDialogVariant.INFO].
 * @param confirmColor Optional explicit color override for the confirm
 *   button. When provided, takes precedence over [variant].
 *
 * **Visual**: standard Material 3 dialog with confirm on the right
 * and dismiss on the left (Material 3 reverses them in RTL locales).
 * **Behavior**: non-cancelable programmatically — the dialog can only
 * be dismissed via [onConfirm] or [onDismiss].
 * **Recomposition**: recomposes when any parameter changes.
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
    icon: ImageVector? = null,
    variant: NextPageDialogVariant = NextPageDialogVariant.INFO,
    confirmColor: Color? = null
) {
    val effectiveConfirmColor = confirmColor ?: when (variant) {
        NextPageDialogVariant.INFO -> MaterialTheme.colorScheme.primary
        NextPageDialogVariant.DESTRUCTIVE -> MaterialTheme.colorScheme.error
        NextPageDialogVariant.SUCCESS -> MaterialTheme.colorScheme.primary
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = effectiveConfirmColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(text = title)
            }
        },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = effectiveConfirmColor
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

@Preview(showBackground = true)
@Composable
private fun NextPageDialogDestructiveDarkPreview() {
    NextPageTheme(darkTheme = true) {
        NextPageDialog(
            title = "Delete book?",
            body = "This removes it from your library.",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {},
            onDismiss = {},
            icon = NextPageIcons.Trash,
            variant = NextPageDialogVariant.DESTRUCTIVE
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NextPageDialogDestructiveLightPreview() {
    NextPageTheme(darkTheme = false) {
        NextPageDialog(
            title = "Delete book?",
            body = "This removes it from your library.",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {},
            onDismiss = {},
            icon = NextPageIcons.Trash,
            variant = NextPageDialogVariant.DESTRUCTIVE
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NextPageDialogInfoDarkPreview() {
    NextPageTheme(darkTheme = true) {
        NextPageDialog(
            title = "Info",
            body = "This is an informational message.",
            confirmText = "OK",
            dismissText = "Close",
            onConfirm = {},
            onDismiss = {},
            icon = NextPageIcons.Info,
            variant = NextPageDialogVariant.INFO
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NextPageDialogInfoLightPreview() {
    NextPageTheme(darkTheme = false) {
        NextPageDialog(
            title = "Info",
            body = "This is an informational message.",
            confirmText = "OK",
            dismissText = "Close",
            onConfirm = {},
            onDismiss = {},
            icon = NextPageIcons.Info,
            variant = NextPageDialogVariant.INFO
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NextPageDialogSuccessDarkPreview() {
    NextPageTheme(darkTheme = true) {
        NextPageDialog(
            title = "Saved",
            body = "Your changes were saved.",
            confirmText = "Great",
            dismissText = "Close",
            onConfirm = {},
            onDismiss = {},
            icon = NextPageIcons.Check,
            variant = NextPageDialogVariant.SUCCESS
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NextPageDialogSuccessLightPreview() {
    NextPageTheme(darkTheme = false) {
        NextPageDialog(
            title = "Saved",
            body = "Your changes were saved.",
            confirmText = "Great",
            dismissText = "Close",
            onConfirm = {},
            onDismiss = {},
            icon = NextPageIcons.Check,
            variant = NextPageDialogVariant.SUCCESS
        )
    }
}
