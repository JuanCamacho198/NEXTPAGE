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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
 * Mutable state backing the color picker controls (preset swatches,
 * spectrum bar, hue slider, hex input).
 *
 * Lifted out of [ColorPickerContent] so a picker survives recompositions
 * and reopens with the user's last selection instead of resetting to the
 * first preset. Created via [rememberColorPickerState].
 */
class ColorPickerState(initialColor: String) {
    var selectedColor by mutableStateOf(initialColor)
    var hexInput by mutableStateOf(initialColor.removePrefix("#"))
    var hue by mutableFloatStateOf(hueFromHex(initialColor))
    var spectrumPosition by mutableFloatStateOf(0.5f)
}

/** Creates a [ColorPickerState] remembered for this composition. */
@Composable
fun rememberColorPickerState(initialColor: String): ColorPickerState =
    remember { ColorPickerState(initialColor) }

/**
 * Reusable color picker controls extracted from
 * [HighlightColorPickerPopover]. Contains preset swatches, a spectrum
 * bar, a hue slider, and a hex text field. Does NOT include a popover
 * card wrapper — use inside a card, dialog, or column as needed.
 *
 * @param presets List of hex color presets shown as swatches.
 * @param state Shared [ColorPickerState] owning the selected color, hex
 *   input, hue, and spectrum position. Callers create it via
 *   [rememberColorPickerState] so the picker keeps its state across
 *   recompositions. The matching preset swatch gets a 2dp
 *   `MaterialTheme.colorScheme.outline` border.
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
    state: ColorPickerState,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                val isActive = hex.equals(state.selectedColor, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(parseColorHex(hex))
                        .then(
                            if (isActive) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.outline,
                                CircleShape
                            ) else Modifier
                        )
                        .clickable {
                            state.selectedColor = hex
                            state.hexInput = hex.removePrefix("#")
                            state.hue = hueFromHex(hex)
                            onColorSelected(hex)
                            onDismiss()
                        }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Spectrum Gradient Canvas ───────────────────────────────
        SpectrumBar(
            currentPosition = state.spectrumPosition,
            hue = state.hue,
            onPositionChange = { pos ->
                state.spectrumPosition = pos
                state.selectedColor = spectrumColorAt(pos, state.hue)
                state.hexInput = state.selectedColor.removePrefix("#")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── Hue Slider ─────────────────────────────────────────────
        HueSlider(
            hue = state.hue,
            onHueChange = { newHue ->
                state.hue = newHue
                state.selectedColor = hslToHex(newHue)
                state.hexInput = state.selectedColor.removePrefix("#")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── Hex Input ──────────────────────────────────────────────
        OutlinedTextField(
            value = state.hexInput.uppercase(),
            onValueChange = { input ->
                val clean = input.filter { it in "0123456789ABCDEFabcdef" }.take(6)
                state.hexInput = clean
                if (clean.length == 6) {
                    val hex = "#$clean"
                    state.selectedColor = hex
                    state.hue = hueFromHex(hex)
                }
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        )

        Spacer(Modifier.height(4.dp))

        // ── Confirmation hint ──────────────────────────────────────
        Text(
            text = stringResource(R.string.color_picker_confirm),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * - `MaterialTheme.colorScheme.surface` container, 16dp rounded, 12dp
 *   shadow, 220dp wide.
 * - Delegates to [ColorPickerContent] for the actual controls.
 * - The picker state is remembered via [rememberColorPickerState] so the
 *   selected color / hue / spectrum position survive recompositions.
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

    val pickerState = rememberColorPickerState(presets.first())

    Column(
        modifier = modifier
            .width(220.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ColorPickerContent(
            presets = presets,
            state = pickerState,
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

            // Thumb indicator: a circular thumb drawn INSIDE this Canvas
            // (so it always tracks the gradient and never misaligns, no
            // hardcoded width offsets). White fill with a dark border.
            val thumbX = currentPosition * size.width
            val thumbCenter = Offset(thumbX, size.height / 2f)
            drawCircle(
                color = Color.White,
                radius = thumbRadius.toPx(),
                center = thumbCenter
            )
            drawCircle(
                color = Color(0xFF1F2937),
                radius = thumbRadius.toPx(),
                center = thumbCenter,
                style = Stroke(width = 2.dp.toPx())
            )
        }
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
    // Rainbow gradient drawn behind the Slider as its track. The Slider
    // itself renders transparent tracks, so only the white thumb shows.
    val rainbowColors = remember {
        List(12) { i -> hslToColor(i * 30f, 1f, 0.5f) }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(rainbowColors))
        )
        Slider(
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
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
    }
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

// HSL/HEX conversion constants.
private const val HUE_EPSILON = 0.001f
private const val FULL_HUE_DEGREES = 360f
private const val HUE_SECTOR_240 = 240f
private const val BYTE_CHANNEL_MAX = 255

/** Extracts approximate hue (0-360) from a hex colour string. */
private fun hueFromHex(hex: String): Float {
    val c = parseColorHex(hex)
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b).coerceAtLeast(HUE_EPSILON)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta < HUE_EPSILON) return 0f
    val h = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return if (h < 0f) h + FULL_HUE_DEGREES else h
}

/**
 * Returns the spectrum colour at [position] (0..1) for the given [hue].
 *
 * Matches the spectrum bar gradient exactly: black at 0, fully-saturated
 * [hue] at 0.5, white at 1 — i.e. HSL lightness sweeps 0 → 0.5 → 1.
 */
private fun spectrumColorAt(position: Float, hue: Float): String {
    val color = hslToColor(hue, saturation = 1f, lightness = position.coerceIn(0f, 1f))
    val r = (color.red * BYTE_CHANNEL_MAX).roundToInt().coerceIn(0, BYTE_CHANNEL_MAX)
    val g = (color.green * BYTE_CHANNEL_MAX).roundToInt().coerceIn(0, BYTE_CHANNEL_MAX)
    val b = (color.blue * BYTE_CHANNEL_MAX).roundToInt().coerceIn(0, BYTE_CHANNEL_MAX)
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
        hue < HUE_SECTOR_240 -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(rp + m, gp + m, bp + m)
}

/** Converts a hue (0-360) to a fully-saturated hex string. */
private fun hslToHex(hue: Float): String {
    val color = hslToColor(hue, saturation = 1f, lightness = 0.5f)
    val r = (color.red * BYTE_CHANNEL_MAX).roundToInt().coerceIn(0, BYTE_CHANNEL_MAX)
    val g = (color.green * BYTE_CHANNEL_MAX).roundToInt().coerceIn(0, BYTE_CHANNEL_MAX)
    val b = (color.blue * BYTE_CHANNEL_MAX).roundToInt().coerceIn(0, BYTE_CHANNEL_MAX)
    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
}
