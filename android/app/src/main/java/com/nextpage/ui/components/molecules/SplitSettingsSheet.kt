package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.domain.model.FontSizePreset
import com.nextpage.domain.model.LayoutPreferences
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.ReaderTheme
import com.nextpage.domain.model.ScrollMode

/**
 * Split-layout settings panel that replaces the old ReadingSettingsSheet.
 *
 * Design: Column(weight 0.4f + 0.6f)
 * - Top half: live Text preview with current settings applied
 * - Bottom half: config panel with drag handle + font selector + 4 theme circles
 *   + font size slider + layout toggles + switches + expandable sections
 *
 * Config panel bg: #191F2FFF, cornerRadius [24,24,0,0], shadow, drag handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun SplitSettingsSheet(
    settings: ReaderSettings,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFontDropdown by remember { mutableStateOf(false) }
    var showJustificationSection by remember { mutableStateOf(false) }
    var showMarginsSection by remember { mutableStateOf(false) }
    var showDirectionSection by remember { mutableStateOf(false) }
    var showAudioSection by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        // ── Reading preview (weight 0.4f) ──────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .background(Color(0xFF0D1322))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.aa_preview_text),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = when (settings.fontSize) {
                        FontSizePreset.XS -> 12.sp
                        FontSizePreset.S -> 13.sp
                        FontSizePreset.SM -> 14.sp
                        FontSizePreset.M -> 16.sp
                        FontSizePreset.ML -> 18.sp
                        FontSizePreset.L -> 20.sp
                        FontSizePreset.XL -> 22.sp
                        FontSizePreset.XXL -> 26.sp
                    },
                    lineHeight = when (settings.lineHeight) {
                        com.nextpage.domain.model.LineHeightPreset.TIGHT -> 18.sp
                        com.nextpage.domain.model.LineHeightPreset.NORMAL -> 22.sp
                        com.nextpage.domain.model.LineHeightPreset.COMFORTABLE -> 26.sp
                        com.nextpage.domain.model.LineHeightPreset.WIDE -> 30.sp
                    }
                ),
                color = parseColorHex(settings.theme.textHex)
            )
        }

        // ── Config panel (weight 0.6f) ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    color = Color(0xFF191F2F),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Drag Handle ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4A5568))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Font Selector ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2F3445))
                    .clickable { showFontDropdown = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = null,
                        tint = Color(0xFFADC6FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = settings.fontName,
                        color = Color(0xFFDDE2F8),
                        fontSize = 14.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF718096),
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showFontDropdown,
                onDismissRequest = { showFontDropdown = false }
            ) {
                listOf("Georgia", "Arial", "Merriweather").forEach { font ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when (font) {
                                    "Georgia" -> stringResource(R.string.font_name_georgia)
                                    "Arial" -> stringResource(R.string.font_name_arial)
                                    "Merriweather" -> stringResource(R.string.font_name_merriweather)
                                    else -> font
                                }
                            )
                        },
                        onClick = {
                            onSettingsChanged(settings.copy(fontName = font))
                            showFontDropdown = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Theme Circles ──────────────────────────────────────
            Text(
                text = stringResource(R.string.reader_settings_theme_section),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF718096),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ThemeCircle(
                    color = "#FFFFFF",
                    label = stringResource(R.string.reader_theme_light),
                    isSelected = settings.theme == ReaderTheme.LIGHT,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.LIGHT)) }
                )
                ThemeCircle(
                    color = "#F4ECD8",
                    label = stringResource(R.string.reader_theme_sepia),
                    isSelected = settings.theme == ReaderTheme.SEPIA,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.SEPIA)) }
                )
                ThemeCircle(
                    color = "#121212",
                    label = stringResource(R.string.reader_theme_dark),
                    isSelected = settings.theme == ReaderTheme.DARK,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.DARK)) }
                )
                ThemeCircle(
                    color = "#0D1322",
                    label = stringResource(R.string.theme_oled),
                    isSelected = settings.theme == ReaderTheme.OLED,
                    onClick = { onSettingsChanged(settings.copy(theme = ReaderTheme.OLED)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Font Size Slider ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "A-",
                    color = Color(0xFF718096),
                    fontSize = 12.sp
                )

                Slider(
                    value = settings.fontSize.ordinal.toFloat(),
                    onValueChange = { value ->
                        val preset = FontSizePreset.fromOrdinal(value.toInt())
                        onSettingsChanged(settings.copy(fontSize = preset))
                    },
                    valueRange = 0f..(FontSizePreset.entries.size - 1).toFloat(),
                    steps = FontSizePreset.entries.size - 2,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFADC6FF),
                        activeTrackColor = Color(0xFFADC6FF),
                        inactiveTrackColor = Color(0xFF2F3445)
                    )
                )

                Text(
                    text = "A+",
                    color = Color(0xFF718096),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Layout Toggles ─────────────────────────────────────
            SettingsSwitchRow(
                label = stringResource(R.string.editor_values),
                checked = settings.editorValues,
                onCheckedChange = { onSettingsChanged(settings.copy(editorValues = it)) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            SettingsSwitchRow(
                label = stringResource(R.string.vertical_scroll),
                checked = settings.verticalScroll,
                onCheckedChange = {
                    onSettingsChanged(
                        settings.copy(
                            verticalScroll = it,
                            scrollMode = if (it) ScrollMode.VERTICAL else ScrollMode.PAGINATED
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Expandable Sections ────────────────────────────────
            ExpandableSection(
                label = stringResource(R.string.aa_section_justification),
                isExpanded = showJustificationSection,
                onToggle = { showJustificationSection = !showJustificationSection },
                content = {
                Column {
                    listOf(
                        stringResource(R.string.aa_justification_left) to LayoutPreferences.Alignment.LEFT,
                        stringResource(R.string.aa_justification_justify) to LayoutPreferences.Alignment.JUSTIFY
                    ).forEach { (label, alignment) ->
                        val isSelected = settings.layoutPrefs.alignment == alignment
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF2F3445) else Color.Transparent)
                                .clickable {
                                    onSettingsChanged(
                                        settings.copy(
                                            layoutPrefs = settings.layoutPrefs.copy(alignment = alignment)
                                        )
                                    )
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFFADC6FF) else Color(0xFFDDE2F8),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                } // end content
            ) // end ExpandableSection

            ExpandableSection(
                label = stringResource(R.string.aa_section_margins),
                isExpanded = showMarginsSection,
                onToggle = { showMarginsSection = !showMarginsSection },
                content = {
                Column {
                    data class MarginPreset(val label: String, val left: Int, val right: Int)
                    val marginPresets = listOf(
                        MarginPreset(stringResource(R.string.aa_margins_narrow), 8, 8),
                        MarginPreset(stringResource(R.string.aa_margins_normal), 16, 16),
                        MarginPreset(stringResource(R.string.aa_margins_wide), 24, 24)
                    )
                    marginPresets.forEach { preset ->
                        val isSelected = settings.layoutPrefs.leftMargin == preset.left &&
                            settings.layoutPrefs.rightMargin == preset.right
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF2F3445) else Color.Transparent)
                                .clickable {
                                    onSettingsChanged(
                                        settings.copy(
                                            layoutPrefs = settings.layoutPrefs.copy(
                                                leftMargin = preset.left,
                                                rightMargin = preset.right
                                            )
                                        )
                                    )
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = preset.label,
                                color = if (isSelected) Color(0xFFADC6FF) else Color(0xFFDDE2F8),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                } // end content
            ) // end ExpandableSection

            ExpandableSection(
                label = stringResource(R.string.aa_section_direction),
                isExpanded = showDirectionSection,
                onToggle = { showDirectionSection = !showDirectionSection },
                content = {
                Text(
                    text = stringResource(R.string.aa_direction_ltr),
                    color = Color(0xFF718096),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
                } // end content
            ) // end ExpandableSection

            ExpandableSection(
                label = stringResource(R.string.aa_section_audio),
                isExpanded = showAudioSection,
                onToggle = { showAudioSection = !showAudioSection },
                content = {
                Text(
                    text = stringResource(R.string.aa_audio_coming_soon),
                    color = Color(0xFF718096),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
                } // end content
            ) // end ExpandableSection
        }
    }
}

@Composable
private fun ThemeCircle(
    color: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(parseColorHex(color))
                .then(
                    if (isSelected) Modifier
                        .border(2.dp, Color(0xFFADC6FF), CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFFADC6FF) else Color(0xFF718096),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFDDE2F8),
            fontSize = 14.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFADC6FF),
                checkedTrackColor = Color(0xFFADC6FF).copy(alpha = 0.3f),
                uncheckedThumbColor = Color(0xFF4A5568),
                uncheckedTrackColor = Color(0xFF2F3445)
            )
        )
    }
}

@Composable
private fun ExpandableSection(
    label: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFDDE2F8),
                fontSize = 14.sp
            )
            Icon(
                imageVector = if (isExpanded)
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                else
                    Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) stringResource(R.string.aa_collapse) else stringResource(R.string.aa_expand),
                tint = Color(0xFF718096),
                modifier = Modifier.size(20.dp)
            )
        }

        if (isExpanded) {
            content()
        }

        HorizontalDivider(color = Color(0xFF2F3445))
    }
}

private fun parseColorHex(hex: String): Color {
    val sanitized = hex.removePrefix("#")
    val longHex = when (sanitized.length) {
        6 -> "FF$sanitized"
        8 -> sanitized
        else -> "FF000000"
    }
    return Color(longHex.toLong(16))
}
