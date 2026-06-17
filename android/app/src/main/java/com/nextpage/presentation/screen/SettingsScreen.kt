package com.nextpage.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.nextpage.ui.components.molecules.HighlightPaletteSection
import com.nextpage.ui.components.molecules.NextPageHeader
import com.nextpage.ui.components.molecules.NextPagePreferenceItem
import com.nextpage.ui.components.molecules.NextPageSelector
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage
import com.nextpage.ui.components.molecules.NotificationSheet
import com.nextpage.ui.components.molecules.SelectorOption

// ─── Data models for sections ────────────────────────────────────────

private data class SectionItem(
    val labelRes: Int,
    val icon: ImageVector,
    val value: String? = null,
    val onClick: () -> Unit = {}
)

private data class SettingsSection(
    val titleRes: Int,
    val items: List<SectionItem>
)

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    authSession: AuthSession?,
    appThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onAppThemeModeChanged: (ThemeMode) -> Unit = {},
    onLogout: () -> Unit = {},
    customHighlightColors: List<String>? = null,
    onUpdateCustomHighlightColor: (Int, String) -> Unit = { _, _ -> },
    onResetCustomHighlightColors: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showThemeSelector by remember { mutableStateOf(false) }
    var showLanguageSelector by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showPaletteSubPage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val langPrefs = remember { AppLanguagePreferences(context = context) }

    if (showNotifications) {
        NotificationSheet(onDismiss = { showNotifications = false })
    }

    if (showPaletteSubPage) {
        NextPageSettingsSubPage(
            title = stringResource(R.string.palette_section_title),
            onBack = { showPaletteSubPage = false }
        ) {
            HighlightPaletteSection(
                customColors = customHighlightColors,
                onUpdateColor = { index, hex ->
                    onUpdateCustomHighlightColor(index, hex)
                },
                onReset = {
                    onResetCustomHighlightColors()
                    showPaletteSubPage = false
                }
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(text = stringResource(R.string.settings_logout_title)) },
            text = { Text(text = stringResource(R.string.settings_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text(
                        text = stringResource(R.string.settings_logout_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = stringResource(R.string.reader_cancel))
                }
            }
        )
    }

    if (showThemeSelector) {
        NextPageSelector(
            title = stringResource(R.string.settings_pref_theme),
            options = listOf(
                SelectorOption(ThemeMode.LIGHT.name, R.string.settings_theme_light),
                SelectorOption(ThemeMode.DARK.name, R.string.settings_theme_dark),
                SelectorOption(ThemeMode.SYSTEM.name, R.string.settings_theme_system)
            ),
            selectedOptionId = appThemeMode.name,
            onOptionSelected = { option ->
                ThemeMode.entries.find { it.name == option.id }?.let {
                    onAppThemeModeChanged(it)
                }
                showThemeSelector = false
            },
            onDismiss = { showThemeSelector = false }
        )
    }

    if (showLanguageSelector) {
        val currentLang = langPrefs.load()
        NextPageSelector(
            title = stringResource(R.string.settings_language_title),
            options = listOf(
                SelectorOption("es", R.string.settings_language_spanish),
                SelectorOption("en", R.string.settings_language_english),
                SelectorOption("system", R.string.settings_language_system)
            ),
            selectedOptionId = currentLang ?: "system",
            onOptionSelected = { option ->
                val code = if (option.id == "system") null else option.id
                langPrefs.save(code)
                if (code != null) {
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(code)
                    )
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                    )
                }
                showLanguageSelector = false
            },
            onDismiss = { showLanguageSelector = false }
        )
    }

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

    val sections = listOf(
        SettingsSection(
            titleRes = R.string.settings_lectura_section,
            items = listOf(
                SectionItem(R.string.settings_pref_reading_theme, Icons.Outlined.Palette),
                SectionItem(R.string.settings_pref_font_size, Icons.Outlined.TextFields),
                SectionItem(R.string.settings_pref_line_height, Icons.Outlined.TextFields),
                SectionItem(R.string.palette_section_title, Icons.Outlined.Palette, onClick = { showPaletteSubPage = true })
            )
        ),
        SettingsSection(
            titleRes = R.string.settings_apariencia_section,
            items = listOf(
                SectionItem(
                    labelRes = R.string.settings_pref_theme,
                    icon = Icons.Outlined.DarkMode,
                    value = stringResource(currentThemeLabel),
                    onClick = { showThemeSelector = true }
                ),
                SectionItem(
                    labelRes = R.string.settings_pref_language,
                    icon = Icons.Outlined.Language,
                    value = stringResource(currentLanguageLabel),
                    onClick = { showLanguageSelector = true }
                )
            )
        ),
        SettingsSection(
            titleRes = R.string.settings_datos_section,
            items = listOf(
                SectionItem(R.string.settings_pref_sync, Icons.Outlined.Sync),
                SectionItem(R.string.settings_pref_storage, Icons.Outlined.Storage),
                SectionItem(R.string.settings_pref_stats, Icons.Outlined.Timeline)
            )
        ),
        SettingsSection(
            titleRes = R.string.settings_info_section,
            items = listOf(
                SectionItem(R.string.settings_pref_about, Icons.Outlined.Info)
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            NextPageHeader(
                title = stringResource(R.string.home_nextpage_title),
                avatarInitials = stringResource(R.string.app_logo_initials),
                onNotificationsClick = { showNotifications = true }
            )

            TitleSection()

            AccountSection(authSession = authSession, onLogout = { showLogoutDialog = true })

            sections.forEach { section ->
                SettingsSectionBlock(section = section)
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
    onLogout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_account_section),
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
                Column {
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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogout),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
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
}

@Composable
private fun SettingsSectionBlock(section: SettingsSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(section.titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        section.items.forEach { item ->
            NextPagePreferenceItem(
                icon = item.icon,
                label = stringResource(item.labelRes),
                value = item.value,
                onClick = item.onClick
            )
        }
    }
}

@Composable
private fun DebugModeSection(context: android.content.Context) {
    var debugEnabled by remember { mutableStateOf(DebugPrefs.isEnabled(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Debug",
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
                Switch(
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
