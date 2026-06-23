package com.nextpage.ui.components.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Simple row that pairs a section title with an optional action
 * button on the right. Used to introduce list sections ("Recently
 * added", "Continue reading", etc.).
 *
 * @param title Section title rendered in `titleMedium` semibold.
 * @param modifier Modifier applied to the outer `Row`.
 * @param actionLabel Text for the action button on the right. The
 *   button is hidden unless BOTH [actionLabel] and [onActionClick]
 *   are non-null. Pass `null` to omit the action area entirely.
 * @param onActionClick Invoked when the user taps the action button.
 *   The button is hidden unless both this and [actionLabel] are
 *   non-null.
 *
 * **Visual**: title-left, action-right. The action is a Material 3
 * `TextButton` (no container, just a tinted label).
 * **Behavior**: tap the action → [onActionClick]. No internal state.
 * **Recomposition**: recomposes when `title`, `actionLabel`, or
 * `onActionClick` change.
 */
@Composable
fun NextPageSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(text = actionLabel)
            }
        }
    }
}
