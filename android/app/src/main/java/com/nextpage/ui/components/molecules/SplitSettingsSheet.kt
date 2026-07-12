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
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Split-layout settings panel for the reader. Top 40% is a live text
 * preview that reflects the current [ReaderSettings]; bottom 60% is
 * the config panel (font, theme, font size, layout toggles,
 * expandable sections for justification / margins / direction /
 * audio).
 *
 * The preview is a real `Text` composable that re-renders on every
 * settings change — what you see here is what the reader will look
 * like after applying these settings.
 *
 * Visual design (locked to the dark reader theme):
 * - Preview: background = `settings.theme.bgHex`, text =
 *   `settings.theme.textHex`, with the configured
 *   `fontSize`/`lineHeight`/`fontFamily`/`alignment` and
 *   `leftMargin`/`rightMargin`.
 * - Config panel: `#191F2F` background, 24dp top corners, 8dp shadow.
 *
 * @param settings Current reader settings. Drives the preview AND
 *   the form controls (selected theme, slider position, switch
 *   states).
 * @param previewText Text to render in the live preview. When
 *   blank, the `R.string.aa_preview_text` lorem-ipsum is used.
 * @param onSettingsChanged Invoked with a new [ReaderSettings]
 *   whenever the user changes any control. The composable does NOT
 *   persist the change — the caller is in charge of saving it.
 * @param onDismiss Invoked when the user taps the close X in the
 *   panel header. (Note: this composable does not render a scrim
 *   or sheet wrapper — it is intended to be embedded in a sheet
 *   the caller controls.)
 * @param modifier Modifier applied to the outer `Column`.
 *
 * **Visual**: outer `Column` with 12dp shadow, 24dp top corners.
 *   Top half (weight 0.4f): live preview block with 20dp vertical
 *   padding and the configured side margins. Bottom half
 *   (weight 0.6f): 24dp top corners, scrollable, 20dp horizontal
 *   padding, 32dp bottom padding. Contents: drag handle + header
 *   (title + close X), font selector (`DropdownMenu` with Georgia/
 *   Arial/Merriweather), 4 theme circles (Light/Sepia/Dark/OLED)
 *   with labels, font-size slider (`A-` ... `A+` discrete steps
 *   over `FontSizePreset.entries`), two `SettingsSwitchRow`s
 *   (editor values, vertical scroll), and four `ExpandableSection`s
 *   (justification, margins, direction, audio).
 * **Behavior**: every control emits a new [ReaderSettings] through
 *   [onSettingsChanged]. The vertical-scroll switch also flips
 *   `scrollMode` between `VERTICAL` and `PAGINATED` automatically.
 *   Expandable sections toggle locally; they don't round-trip
 *   through the parent.
 * **Recomposition**: recomposes when `settings`, `previewText`, or
 *   callbacks change. Internal `showFontDropdown` and section
 *   expansion flags are `remember`-ed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun SplitSettingsSheet(
    settings: ReaderSettings,
    previewText: String,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFontDropdown by remember { mutableStateOf(false) }
    var showJustificationSection by remember { mutableStateOf(false) }
    var showMarginsSection by remember { mutableStateOf(false) }
    var showDirectionSection by remember { mutableStateOf(false) }
    var showAudioSection by remember { mutableStateOf(false) }

    val previewFontFamily = when (settings.fontName) {
        "Arial" -> androidx.compose.ui.text.font.FontFamily.SansSerif
        "Merriweather" -> androidx.compose.ui.text.font.FontFamily.Serif
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }

    val previewAlignment = when (settings.layoutPrefs.alignment) {
        LayoutPreferences.Alignment.LEFT -> androidx.compose.ui.text.style.TextAlign.Left
        LayoutPreferences.Alignment.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
        LayoutPreferences.Alignment.RIGHT -> androidx.compose.ui.text.style.TextAlign.Right
        LayoutPreferences.Alignment.JUSTIFY -> androidx.compose.ui.text.style.TextAlign.Justify
    }

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
                .background(parseColorHex(settings.theme.bgHex))
                .padding(
                    start = settings.layoutPrefs.leftMargin.dp,
                    end = settings.layoutPrefs.rightMargin.dp,
                    top = 20.dp,
                    bottom = 20.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = previewText.ifBlank { stringResource(R.string.aa_preview_text) },
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
                    },
                    fontFamily = previewFontFamily
                ),
                color = parseColorHex(settings.theme.textHex),
                textAlign = previewAlignment
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

            // ── Header Row with Close Button ───────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.reader_typography),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFDDE2F8),
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.aa_close_settings),
                        tint = Color(0xFF718096)
                    )
                }
            }

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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val alignmentOptions = listOf(
                            LayoutPreferences.Alignment.LEFT to Icons.AutoMirrored.Filled.FormatAlignLeft,
                            LayoutPreferences.Alignment.CENTER to Icons.Default.FormatAlignCenter,
                            LayoutPreferences.Alignment.RIGHT to Icons.AutoMirrored.Filled.FormatAlignRight,
                            LayoutPreferences.Alignment.JUSTIFY to Icons.Default.FormatAlignJustify
                        )
                        alignmentOptions.forEach { (alignment, icon) ->
                            val isSelected = settings.layoutPrefs.alignment == alignment
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF2F3445) else Color.Transparent)
                                    .border(1.dp, if (isSelected) Color(0xFFADC6FF) else Color(0xFF4A5568), RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSettingsChanged(
                                            settings.copy(
                                                layoutPrefs = settings.layoutPrefs.copy(alignment = alignment)
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = alignment.name,
                                    tint = if (isSelected) Color(0xFFADC6FF) else Color(0xFF718096)
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
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.aa_margin_left) + ": ${settings.layoutPrefs.leftMargin}px",
                            color = Color(0xFFDDE2F8),
                            fontSize = 12.sp
                        )
                        Slider(
                            value = settings.layoutPrefs.leftMargin.toFloat(),
                            onValueChange = { value ->
                                onSettingsChanged(
                                    settings.copy(
                                        layoutPrefs = settings.layoutPrefs.copy(leftMargin = value.toInt())
                                    )
                                )
                            },
                            valueRange = 0f..40f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFADC6FF),
                                activeTrackColor = Color(0xFFADC6FF),
                                inactiveTrackColor = Color(0xFF2F3445)
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.aa_margin_right) + ": ${settings.layoutPrefs.rightMargin}px",
                            color = Color(0xFFDDE2F8),
                            fontSize = 12.sp
                        )
                        Slider(
                            value = settings.layoutPrefs.rightMargin.toFloat(),
                            onValueChange = { value ->
                                onSettingsChanged(
                                    settings.copy(
                                        layoutPrefs = settings.layoutPrefs.copy(rightMargin = value.toInt())
                                    )
                                )
                            },
                            valueRange = 0f..40f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFADC6FF),
                                activeTrackColor = Color(0xFFADC6FF),
                                inactiveTrackColor = Color(0xFF2F3445)
                            )
                        )
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
    return try {
        val sanitized = hex.removePrefix("#")
        val longHex = when (sanitized.length) {
            6 -> "FF$sanitized"
            8 -> sanitized
            else -> "FF000000"
        }
        Color(longHex.toLong(16))
    } catch (_: Exception) {
        Color.Magenta
    }
}
