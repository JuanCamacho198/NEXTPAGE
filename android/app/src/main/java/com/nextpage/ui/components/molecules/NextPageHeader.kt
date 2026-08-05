package com.nextpage.ui.components.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.ui.components.atoms.NextPageAvatar
import com.nextpage.ui.components.atoms.NextPageIconButton

/**
 * Top app header: avatar (or initials) + title on the left, optional
 * search and notifications icon buttons on the right, followed by any
 * caller-supplied trailing action icons.
 *
 * @param title Header text rendered next to the avatar in
 *   `titleMedium` semibold.
 * @param modifier Modifier applied to the outer `Row`.
 * @param avatarImageUrl Remote avatar URL. When `null`/blank, the
 *   [avatarInitials] fallback is used. Default `null`.
 * @param avatarInitials Two-character fallback for the avatar circle
 *   (e.g. `"JS"`). Default `"NP"`. Will be uppercased and truncated
 *   to 2 chars by [NextPageAvatar].
 * @param onAvatarClick Optional avatar click handler. When `null`, the
 *   avatar is non-interactive.
 * @param avatarContentDescription Optional accessibility label for the
 *   avatar (e.g. "Open account settings").
 * @param onSearchClick Optional search-button callback. When `null`,
 *   the search icon is not rendered.
 * @param onNotificationsClick Optional notifications-button callback.
 *   When `null`, the bell icon is not rendered.
 * @param trailingActions Additional icon buttons rendered after the
 *   built-in search/notifications buttons. Each pair is
 *   `(icon, onClick)`. The `contentDescription` for these is
 *   intentionally `""` (decorative — pair with an a11y label outside
 *   the header if needed).
 *
 * **Visual**: 40dp avatar + 12dp gap + title on the left. On the
 *   right: search icon (if [onSearchClick]), notifications icon (if
 *   [onNotificationsClick]), then any [trailingActions] — all 40dp
 *   [NextPageIconButton]s with 4dp spacing. 16dp top padding.
 * **Behavior**: each visible icon calls its respective callback. No
 *   internal state.
 * **Recomposition**: recomposes when `title`, `avatarImageUrl`,
 *   `avatarInitials`, or any callback/action changes.
 */
@Composable
fun NextPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    avatarImageUrl: String? = null,
    avatarInitials: String = "NP",
    onAvatarClick: (() -> Unit)? = null,
    avatarContentDescription: String? = null,
    onSearchClick: (() -> Unit)? = null,
    onNotificationsClick: (() -> Unit)? = null,
    trailingActions: List<Pair<ImageVector, () -> Unit>> = emptyList()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NextPageAvatar(
                imageUrl = avatarImageUrl,
                initials = avatarInitials,
                size = 40.dp,
                onClick = onAvatarClick,
                contentDescription = avatarContentDescription
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            onSearchClick?.let {
                NextPageIconButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.search_label),
                    onClick = it,
                    size = 40.dp
                )
            }
            onNotificationsClick?.let {
                NextPageIconButton(
                    icon = Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.notifications_title),
                    onClick = it,
                    size = 40.dp
                )
            }
            trailingActions.forEach { (icon, onClick) ->
                NextPageIconButton(
                    icon = icon,
                    contentDescription = "",
                    onClick = onClick,
                    size = 40.dp
                )
            }
        }
    }
}
