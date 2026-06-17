package com.nextpage.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.data.remote.sync.SyncState

/**
 * A compact sync status indicator atom.
 *
 * Shows a colored dot + label reflecting the current [SyncState],
 * and an optional numeric badge for [pendingCount].
 */
@Composable
fun SyncStatusIndicator(
    syncState: SyncState,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    val (dotColor, labelRes) = when (syncState) {
        is SyncState.Idle -> Color(0xFF4ADE80) to R.string.sync_status_synced
        is SyncState.Disabled -> Color(0xFF9CA3AF) to R.string.sync_status_off
        is SyncState.Running -> Color(0xFF60A5FA) to R.string.sync_status_syncing
        is SyncState.Error -> Color(0xFFF87171) to R.string.sync_status_error
    }
    val label = stringResource(labelRes)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Pending count badge (only when > 0)
        if (pendingCount > 0) {
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (pendingCount > 99) "99+" else pendingCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
