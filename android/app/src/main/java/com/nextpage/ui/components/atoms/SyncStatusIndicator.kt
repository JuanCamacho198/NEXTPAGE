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
 * Compact pill that surfaces the current cloud-sync state and the
 * number of pending operations. Used in toolbars/headers to give the
 * user at-a-glance feedback on background sync activity.
 *
 * Mapping (color, label) is hard-coded by [syncState]:
 * - `Idle` → green (`#4ADE80`) + `R.string.sync_status_synced`
 * - `Disabled` → gray (`#9CA3AF`) + `R.string.sync_status_off`
 * - `Running` → blue (`#60A5FA`) + `R.string.sync_status_syncing`
 * - `Error` → red (`#F87171`) + `R.string.sync_status_error`
 *
 * @param syncState Current sync state. See `data.remote.sync.SyncState`.
 * @param pendingCount Number of pending operations. The badge is
 *   rendered only when `pendingCount > 0`, with text capped at `"99+"`
 *   to keep the pill narrow.
 * @param modifier Modifier applied to the outer `Row`.
 *
 * **Visual**: 8dp circular dot, `labelSmall` label, optional 8dp-rounded
 * badge with `colorScheme.errorContainer` background and
 * `colorScheme.onErrorContainer` text. The pill itself uses
 * `surfaceVariant` at 60% alpha and 12dp corner radius. Internal
 * spacing: 6dp between elements, 10dp horizontal × 4dp vertical padding.
 * **Behavior**: pure rendering. The dot color is a hard-coded hex
 * value (not from the theme) so the meaning is consistent across
 * themes — green/gray/blue/red are universally read as
 * ok/off/active/error.
 * **Recomposition**: recomposes when `syncState` or `pendingCount`
 * change.
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
