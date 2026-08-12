package com.nextpage.ui.components.atoms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextpage.presentation.theme.NextPageTheme

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

@Preview(showBackground = true)
@Composable
private fun NextPageDividerDarkPreview() {
    NextPageTheme(darkTheme = true) {
        NextPageDivider(modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
private fun NextPageDividerLightPreview() {
    NextPageTheme(darkTheme = false) {
        NextPageDivider(modifier = Modifier.fillMaxWidth())
    }
}
