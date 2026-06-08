package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageDimens

/**
 * Reading progress bar shown at the bottom of the reader.
 *
 * Displays a horizontal blue progress bar with the current position
 * (e.g., "3 / 10" for EPUB chapters, "45 / 200" for PDF pages)
 * and a percentage label.
 *
 * @param progressPercent 0f–100f progress value
 * @param label Short position label like "3 / 10" or "45 / 200"
 * @param onClick Optional action when the user taps the bar
 */
@Composable
fun ReadingProgressBar(
    progressPercent: Float,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progressPercent.coerceIn(0f, 100f)
    val progressFraction = clampedProgress / 100f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NextPageDimens.cardCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = NextPageDimens.spacingMd, vertical = NextPageDimens.spacingSm)
    ) {
        // ── Progress bar track ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NextPageDimens.progressBarHeight)
                .clip(RoundedCornerShape(NextPageDimens.progressBarHeight / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .matchParentSize()
                    .clip(RoundedCornerShape(NextPageDimens.progressBarHeight / 2))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))

        // ── Label row ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.ifEmpty { stringResource(R.string.reader_progress_empty) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${clampedProgress.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
