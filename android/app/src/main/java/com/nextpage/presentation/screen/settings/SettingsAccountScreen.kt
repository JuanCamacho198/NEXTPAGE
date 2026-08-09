package com.nextpage.presentation.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.AuthSession
import com.nextpage.ui.components.atoms.NextPageAvatar
import com.nextpage.ui.components.atoms.NextPageLogoutDialog
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage
import com.nextpage.ui.icons.NextPageIcons

@Composable
fun SettingsAccountScreen(
    authSession: AuthSession?,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        NextPageLogoutDialog(
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    NextPageSettingsSubPage(
        title = stringResource(R.string.settings_account_title),
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NextPageAvatar(
                    imageUrl = authSession?.photoUrl,
                    initials = (authSession?.displayName ?: stringResource(R.string.settings_user_default))
                        .take(2).uppercase(),
                    size = 56.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = authSession?.displayName ?: stringResource(R.string.settings_user_default),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = authSession?.email ?: stringResource(R.string.settings_email_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Provider badge
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = when (authSession?.provider) {
                                "google" -> stringResource(R.string.settings_provider_google)
                                "email" -> stringResource(R.string.settings_provider_email)
                                else -> stringResource(R.string.settings_provider_anonymous)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // Member since
                    val memberSince = authSession?.createdAt?.let { formatMemberSince(it) }
                    if (memberSince != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_member_since, memberSince),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { showLogoutDialog = true }),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = NextPageIcons.SignOut,
                    contentDescription = stringResource(R.string.settings_logout),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.settings_logout),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Formats an ISO 8601 date-time string (e.g. "2024-03-15T10:30:00Z")
 * into a locale-aware month-year label (e.g. "March 2024" or "marzo 2024").
 */
private fun formatMemberSince(createdAt: String): String {
    return try {
        val instant = java.time.Instant.parse(createdAt)
        val zdt = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")
        zdt.format(formatter)
    } catch (_: Exception) {
        createdAt
    }
}
