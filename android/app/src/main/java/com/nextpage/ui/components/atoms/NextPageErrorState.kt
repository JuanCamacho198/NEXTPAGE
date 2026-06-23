package com.nextpage.ui.components.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nextpage.R

/**
 * Full-screen error state: error icon, title, message, and an optional
 * `retryAction` slot. The icon is decorative (no `contentDescription`).
 *
 * @param title Headline text rendered in `titleMedium` semibold
 *   `colorScheme.onSurface`.
 * @param message Supporting text rendered in `bodyMedium`
 *   `colorScheme.onSurfaceVariant`.
 * @param modifier Modifier applied to the outer `Column`.
 * @param retryAction Optional composable (typically a button) rendered
 *   below the message with 16dp top spacing. Pass `null` to hide the
 *   action area entirely.
 *
 * **Visual**: 64dp outlined `ErrorOutline` icon in `colorScheme.error`,
 * 16dp gap, title, 8dp gap, message, optional 16dp gap + action.
 * All text is center-aligned.
 * **Behavior**: pure rendering. The icon is intentionally
 * content-description-less because the title communicates the same
 * meaning to assistive tech.
 * **Recomposition**: recomposes when `title`, `message`, or
 * `retryAction` change.
 */
@Composable
fun NextPageErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryAction: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (retryAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            retryAction()
        }
    }
}

/**
 * Single-message convenience overload of [NextPageErrorState]. The
 * title is resolved from `R.string.error_unknown` via `stringResource`.
 *
 * @param message Supporting text rendered in `bodyMedium`
 *   `colorScheme.onSurfaceVariant`.
 * @param modifier Modifier applied to the outer `Column`.
 * @param retryAction Optional composable (typically a button) rendered
 *   below the message. Pass `null` to hide the action area.
 *
 * **Visual**: identical to the full overload, with a localized default
 * title.
 * **Behavior**: pure rendering. Reads the title string at composition
 * time — will not update if the locale changes without a
 * configuration change.
 * **Recomposition**: recomposes when `message` or `retryAction` change.
 */
@Composable
fun NextPageErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryAction: @Composable (() -> Unit)? = null
) {
    NextPageErrorState(
        title = stringResource(R.string.error_unknown),
        message = message,
        modifier = modifier,
        retryAction = retryAction
    )
}
