package com.nextpage.ui.components.atoms

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Thin horizontal divider using the Material 3 `HorizontalDivider`
 * with the NextPage outline-variant color.
 *
 * @param modifier Modifier applied to the divider. The default fills
 *   the available width with 1dp thickness; override for fixed widths.
 *
 * **Visual**: 1dp thick line in `colorScheme.outlineVariant`.
 * **Behavior**: pure rendering.
 * **Recomposition**: recomposes only when `modifier` changes.
 */
@Composable
fun NextPageDivider(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp
    )
}
