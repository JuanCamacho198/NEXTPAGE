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

@Composable
fun NextPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    avatarImageUrl: String? = null,
    avatarInitials: String = "NP",
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
                size = 40.dp
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
