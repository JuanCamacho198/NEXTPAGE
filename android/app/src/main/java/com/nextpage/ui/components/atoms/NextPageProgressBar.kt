package com.nextpage.ui.components.atoms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextpage.presentation.theme.NextPageTheme

/**
 * Determinate linear progress indicator (Material 3
 * `LinearProgressIndicator`) pre-colored to the NextPage theme.
 *
 * @param progress Current progress in the range `[0f, 1f]`. Values
 *   outside this range are clamped by Material 3.
 * @param modifier Modifier applied to the underlying indicator. Use it
 *   to set width/height (default height comes from the Material
 *   indicator itself).
 *
 * **Visual**: horizontal bar. Fill uses `colorScheme.primary`; track
 * uses `colorScheme.surfaceVariant`.
 * **Behavior**: pure rendering — no animation. For an indeterminate
 * spinner use [NextPageLoadingIndicator] instead.
 * **Recomposition**: recomposes when `progress` or `modifier` change.
 */
@Composable
fun NextPageProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun NextPageProgressBarDarkPreview() {
    NextPageTheme(darkTheme = true) {
        NextPageProgressBar(
            progress = 0.65f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NextPageProgressBarLightPreview() {
    NextPageTheme(darkTheme = false) {
        NextPageProgressBar(
            progress = 0.65f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}
