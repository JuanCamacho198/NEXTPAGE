package com.nextpage.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextpage.BuildConfig
import com.nextpage.R
import com.nextpage.data.session.AppLanguagePreferences
import com.nextpage.debug.DebugPrefs
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.model.ThemeMode
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.ui.components.molecules.NextPageHeader
import com.nextpage.ui.components.molecules.NextPagePreferenceItem

private data class SettingsRow(
    val labelRes: Int,
    val icon: ImageVector,
    val value: String? = null,
    val onClick: () -> Unit = {}
)

private data class SettingsGroup(
    val titleRes: Int,
    val rows: List<SettingsRow>
)

@Composable
fun SettingsListScreen(
    authSession: AuthSession?,
    appThemeMode: ThemeMode,
    onNavigateToAccount: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToPalette: () -> Unit,
    onNavigateToDataStorage: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToDictionary: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val langPrefs = androidx.compose.runtime.remember { AppLanguagePreferences(context = context) }

    val currentLanguageLabel = when (langPrefs.load()) {
        "es" -> R.string.settings_language_spanish
        "en" -> R.string.settings_language_english
        else -> R.string.settings_language_system
    }

    val currentThemeLabel = when (appThemeMode) {
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.SYSTEM -> R.string.settings_theme_system
    }

    val groups = listOf(
        SettingsGroup(
            titleRes = R.string.settings_account_section,
            rows = listOf(
                SettingsRow(
                    labelRes = R.string.settings_account_title,
                    icon = Icons.Outlined.Person,
                    onClick = onNavigateToAccount
                ),
                SettingsRow(
                    labelRes = R.string.settings_devices_title,
                    icon = Icons.Outlined.Devices,
                    onClick = onNavigateToDevices
                )
            )
        ),
        SettingsGroup(
            titleRes = R.string.settings_apariencia_section,
            rows = listOf(
                SettingsRow(
                    labelRes = R.string.settings_pref_theme,
                    icon = Icons.Outlined.DarkMode,
                    value = stringResource(currentThemeLabel),
                    onClick = onNavigateToTheme
                ),
                SettingsRow(
                    labelRes = R.string.settings_pref_language,
                    icon = Icons.Outlined.Language,
                    value = stringResource(currentLanguageLabel),
                    onClick = onNavigateToLanguage
                ),
                SettingsRow(
                    labelRes = R.string.palette_section_title,
                    icon = Icons.Outlined.Palette,
                    onClick = onNavigateToPalette
                )
            )
        ),
        SettingsGroup(
            titleRes = R.string.settings_datos_section,
            rows = listOf(
                SettingsRow(
                    labelRes = R.string.settings_data_storage_title,
                    icon = Icons.Outlined.Storage,
                    onClick = onNavigateToDataStorage
                ),
                SettingsRow(
                    labelRes = R.string.settings_pref_notifications,
                    icon = Icons.Outlined.Notifications,
                    onClick = onNavigateToNotifications
                ),
                SettingsRow(
                    labelRes = R.string.settings_dictionary_label,
                    icon = Icons.Outlined.LibraryBooks,
                    onClick = onNavigateToDictionary
                )
            )
        ),
        SettingsGroup(
            titleRes = R.string.settings_info_section,
            rows = listOf(
                SettingsRow(
                    labelRes = R.string.settings_pref_about,
                    icon = Icons.Outlined.Info,
                    onClick = onNavigateToAbout
                )
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            NextPageHeader(
                title = stringResource(R.string.home_nextpage_title),
                avatarInitials = stringResource(R.string.app_logo_initials)
            )

            TitleSection()

            AccountSection(
                authSession = authSession,
                onClick = onNavigateToAccount
            )

            groups.forEach { group ->
                SettingsGroupBlock(group = group)
            }

            if (BuildConfig.DEBUG) {
                DebugModeSection(context = context)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TitleSection() {
    Column {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccountSection(
    authSession: AuthSession?,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_account_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (authSession?.displayName ?: stringResource(R.string.settings_user_default))
                            .take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = authSession?.displayName ?: stringResource(R.string.settings_user_default),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = authSession?.email ?: stringResource(R.string.settings_email_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupBlock(group: SettingsGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(group.titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        group.rows.forEach { row ->
            NextPagePreferenceItem(
                icon = row.icon,
                label = stringResource(row.labelRes),
                value = row.value,
                onClick = row.onClick
            )
        }
    }
}

@Composable
private fun DebugModeSection(context: android.content.Context) {
    var debugEnabled by remember { mutableStateOf(DebugPrefs.isEnabled(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.debug_mode_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_mode_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.debug_mode_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = debugEnabled,
                    onCheckedChange = { enabled ->
                        debugEnabled = enabled
                        DebugPrefs.setEnabled(context, enabled)
                    }
                )
            }
        }
    }
}
