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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.HighlightColor

/**
 * Settings section that displays the 5 customisable highlight colour
 * swatches and allows the user to edit each one or reset to defaults.
 *
 * @param customColors the current custom palette (5 hex strings), or
 *  null to show [HighlightColor] enum defaults
 * @param onUpdateColor invoked with (index, hex) when a swatch is edited
 * @param onReset invoked when the user taps "Reset to defaults"
 */
@Composable
fun HighlightPaletteSection(
    customColors: List<String>?,
    onUpdateColor: (Int, String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = customColors ?: HighlightColor.defaultHexList()
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editorHex by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Subtitle ──────────────────────────────────────────────
        Text(
            text = stringResource(R.string.palette_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── 5 swatch rows ─────────────────────────────────────────
        colors.forEachIndexed { index, hex ->
            PaletteSwatchRow(
                index = index,
                hex = hex,
                onClick = {
                    editingIndex = index
                    editorHex = hex
                }
            )
        }

        // ── Reset button ──────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReset),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = stringResource(R.string.palette_reset),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }

    // ── Hex editor dialog per swatch ──────────────────────────────
    if (editingIndex != null) {
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = {
                Text(text = stringResource(R.string.palette_pick_color))
            },
            text = {
                Column {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(parseColorHex(editorHex))
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editorHex,
                        onValueChange = { value ->
                            val cleaned = value.filter {
                                it in "0123456789ABCDEFabcdef#"
                            }
                            editorHex = cleaned
                        },
                        label = { Text("#RRGGBB") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sanitized = editorHex.removePrefix("#").trim()
                        if (sanitized.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
                            onUpdateColor(editingIndex!!, "#$sanitized")
                        }
                        editingIndex = null
                    }
                ) {
                    Text(text = stringResource(R.string.reader_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = null }) {
                    Text(text = stringResource(R.string.reader_cancel))
                }
            }
        )
    }
}

/** Single row: coloured circle + hex label, tappable to edit. */
@Composable
private fun PaletteSwatchRow(
    index: Int,
    hex: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(parseColorHex(hex))
                            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape)

            )
            Text(
                text = hex.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Parses a hex colour string (with or without #) to a Compose [Color]. */
@Composable
private fun parseColorHex(hex: String): Color {
    val sanitized = hex.removePrefix("#").trim()
    val longHex = when (sanitized.length) {
        6 -> "FF$sanitized"
        8 -> sanitized
        else -> "FF000000"
    }
    return try {
        Color(longHex.toLong(16))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.outline
    }
}
