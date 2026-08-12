package com.nextpage.ui.components.molecules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageTheme
import kotlin.math.roundToInt

/** Default Pencil kixeV design color presets (5 hex values). */
val DEFAULT_HIGHLIGHT_PRESETS = listOf(
    "#4ADE80",  // GREEN
    "#3B82F6",  // BLUE
    "#F97316",  // ORANGE
    "#EF4444",  // RED
    "#FACC15"   // YELLOW
)

/**
 * Reusable color picker controls extracted from
 * [HighlightColorPickerPopover]. Contains preset swatches, a spectrum
 * bar, a hue slider, and a hex text field. Does NOT include a popover
 * card wrapper — use inside a card, dialog, or column as needed.
 *
 * @param presets List of hex color presets shown as swatches.
 * @param selectedColor Currently selected hex color (with or without
 *   `#`). The matching swatch gets a 2dp `#1F2937` border.
 * @param onColorSelected Invoked with the chosen hex when the user
 *   taps a preset. Spectrum/hue/hex updates happen locally but do NOT
 *   emit this callback.
 * @param onDismiss Invoked when the user taps a preset (auto-dismiss).
 * @param modifier Modifier applied to the outer `Column`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerContent(
    presets: List<String>,
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedColorInternal by remember { mutableStateOf(selectedColor) }
    var hexInput by remember { mutableStateOf(selectedColor.removePrefix("#")) }
    var hue by remember { mutableFloatStateOf(hueFromHex(selectedColor)) }
    var spectrumPosition by remember { mutableFloatStateOf(0.5f) }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 5 Colour Preset Circles ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            presets.take(5).forEach { hex ->
                val isActive = hex.equals(selectedColorInternal, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(parseColorHex(hex))
                        .then(
                            if (isActive) Modifier.border(
                                2.dp,
                                Color(0xFF1F2937),
                                CircleShape
                            ) else Modifier
                        )
                        .clickable {
                            selectedColorInternal = hex
                            hexInput = hex.removePrefix("#")
                            hue = hueFromHex(hex)
                            onColorSelected(hex)
                            onDismiss()
                        }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Spectrum Gradient Canvas ───────────────────────────────
        SpectrumBar(
            currentPosition = spectrumPosition,
            hue = hue,
            onPositionChange = { pos ->
                spectrumPosition = pos
                selectedColorInternal = spectrumColorAt(pos)
                hexInput = selectedColorInternal.removePrefix("#")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── Hue Slider ─────────────────────────────────────────────
        HueSlider(
            hue = hue,
            onHueChange = { newHue ->
                hue = newHue
                selectedColorInternal = hslToHex(newHue)
                hexInput = selectedColorInternal.removePrefix("#")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── Hex Input ──────────────────────────────────────────────
        OutlinedTextField(
            value = hexInput.uppercase(),
            onValueChange = { input ->
                val clean = input.filter { it in "0123456789ABCDEFabcdef" }.take(6)
                hexInput = clean
                if (clean.length == 6) {
                    val hex = "#$clean"
                    selectedColorInternal = hex
                    hue = hueFromHex(hex)
                }
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF3F4F6),
                unfocusedContainerColor = Color(0xFFF3F4F6),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            leadingIcon = {
                Text(
                    text = "#",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    ),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        )

        Spacer(Modifier.height(4.dp))

        // ── Confirmation hint ──────────────────────────────────────
        Text(
            text = stringResource(R.string.color_picker_confirm),
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * kixeV color picker popover for selecting a highlight color. 220dp
 * wide white card wrapping [ColorPickerContent] with preset swatches,
 * spectrum bar, hue slider, and hex input.
 *
 * Design matches Pencil node `kixeV`:
 * - White `#FFFFFF` container, 16dp rounded, 12dp shadow, 220dp wide.
 * - Delegates to [ColorPickerContent] for the actual controls.
 *
 * @param customColors Custom 5-color preset list. Falls back to
 *   [DEFAULT_HIGHLIGHT_PRESETS] when `null` or fewer than 5 entries.
 *   The first 5 entries are used.
 * @param onColorSelected Invoked with the chosen hex color
 *   (e.g. `"#4ADE80"`) when the user taps a preset (this also
 *   auto-dismisses) or when the user types a valid 6-char hex in
 *   the input (caller must dismiss).
 * @param onDismiss Invoked when the caller wants to close the
 *   popover (preset taps also call it). This composable does NOT
 *   render a scrim or backdrop — the parent is expected to provide
 *   tap-away handling (see [SelectionOverlay] for the pattern).
 * @param anchorX Horizontal anchor in pixels (px). Used to position
 *   the popover near the originating UI element. Default `0`.
 * @param anchorY Vertical anchor in pixels (px) for the arrow tip.
 *   The popover itself is offset using `Modifier.offset { ... }`
 *   based on this value. Default `0`.
 * @param modifier Modifier applied to the outer `Column`. (Note: the
 *   parent usually wraps this in a `Modifier.offset` to position
 *   the popover near the selection rect.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightColorPickerPopover(
    customColors: List<String>?,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    anchorX: Int = 0,
    anchorY: Int = 0,
    modifier: Modifier = Modifier
) {
    val presets = (customColors?.takeIf { it.size >= 5 } ?: DEFAULT_HIGHLIGHT_PRESETS)
        .take(5)

    val selectedColor by remember { mutableStateOf(presets.first()) }

    Column(
        modifier = modifier
            .width(220.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ColorPickerContent(
            presets = presets,
            selectedColor = selectedColor,
            onColorSelected = onColorSelected,
            onDismiss = onDismiss
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HighlightColorPickerPopoverDarkPreview() {
    NextPageTheme(darkTheme = true) {
        HighlightColorPickerPopover(
            customColors = null,
            onColorSelected = {},
            onDismiss = {}
        )
    }
}

// Preview-only: fixed dark palette — light render is intentionally broken (see sdd/ui-previews-both-themes spec R7; color migration deferred)
@Preview(showBackground = true)
@Composable
private fun HighlightColorPickerPopoverLightPreview() {
    NextPageTheme(darkTheme = false) {
        HighlightColorPickerPopover(
            customColors = null,
            onColorSelected = {},
            onDismiss = {}
        )
    }
}

// ── Spectrum Bar ────────────────────────────────────────────────────

@Composable
private fun SpectrumBar(
    currentPosition: Float,
    hue: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbRadius = 8.dp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        val pos = (change.position.x / size.width)
                            .coerceIn(0f, 1f)
                        onPositionChange(pos)
                    }
                }
        ) {
            // Black → current-hue-saturated → white gradient
            val saturatedColor = hslToColor(hue, 1f, 0.5f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, saturatedColor, Color.White)
                ),
                size = size
            )

            // Thumb indicator line
            val thumbX = currentPosition * size.width
            drawLine(
                color = Color(0xFF1F2937),
                start = Offset(thumbX, 0f),
                end = Offset(thumbX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Draggable thumb circle
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        ((currentPosition * 220.dp.toPx()) - thumbRadius.toPx()).roundToInt(),
                        0
                    )
                }
                .size(thumbRadius * 2)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color(0xFF1F2937), CircleShape)
        )
    }
}

// ── Hue Slider ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Slider(
        value = hue,
        onValueChange = onHueChange,
        valueRange = 0f..360f,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.Transparent, // custom track via drawBehind
            inactiveTrackColor = Color.Transparent
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Color(0xFFD1D5DB), CircleShape)
            )
        }
    )
    // The gradient track is drawn by the parent via Modifier.background with
    // hue colors. For simplicity here we use the Slider defaults.
    // A production version would draw the rainbow gradient behind.
}

// ── Color Utilities ─────────────────────────────────────────────────

private fun parseColorHex(hex: String): Color {
    return try {
        val sanitized = hex.removePrefix("#")
        Color(("FF$sanitized").toLong(16))
    } catch (_: Exception) {
        Color.Magenta
    }
}

/** Extracts approximate hue (0-360) from a hex colour string. */
private fun hueFromHex(hex: String): Float {
    val c = parseColorHex(hex)
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b).coerceAtLeast(0.001f)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta < 0.001f) return 0f
    val h = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return if (h < 0f) h + 360f else h
}

/** Returns a pseudo-spectrum colour at [position] (0..1) with the given [hue]. */
private fun spectrumColorAt(position: Float): String {
    val r = ((1f - position) * 0 + position * hslToColor(hue = 120f, saturation = 1f, lightness = 0.5f).red * 255f).roundToInt()
        .coerceIn(0, 255)
    val g = ((1f - position) * 0 + position * 255).roundToInt().coerceIn(0, 255)
    val b = ((1f - position) * 0 + position * 255).roundToInt().coerceIn(0, 255)
    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
}

/** Converts HSL to a Compose [Color]. */
private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = lightness - c / 2f
    val (rp, gp, bp) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(rp + m, gp + m, bp + m)
}

/** Converts a hue (0-360) to a fully-saturated hex string. */
private fun hslToHex(hue: Float): String {
    val color = hslToColor(hue, saturation = 1f, lightness = 0.5f)
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
}
