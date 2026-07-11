package com.nextpage.ui.components.atoms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nextpage.R

/**
 * Convenience wrapper around [NextPageDialog] with
 * [NextPageDialogVariant.DESTRUCTIVE] and an [ExitToApp] icon,
 * pre-configured for logout confirmation.
 *
 * @param onConfirm Invoked when the user confirms logout.
 * @param onDismiss Invoked when the user dismisses the dialog.
 */
@Composable
fun NextPageLogoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    NextPageDialog(
        icon = Icons.AutoMirrored.Outlined.ExitToApp,
        variant = NextPageDialogVariant.DESTRUCTIVE,
        title = stringResource(R.string.settings_logout_title),
        body = stringResource(R.string.settings_logout_message),
        confirmText = stringResource(R.string.settings_logout_confirm),
        dismissText = stringResource(R.string.reader_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
