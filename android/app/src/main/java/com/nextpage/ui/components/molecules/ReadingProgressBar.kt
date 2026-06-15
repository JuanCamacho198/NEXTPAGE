package com.nextpage.ui.components.molecules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R

/**
 * Reading progress bar with draggable thumb and chapter navigation buttons.
 *
 * Design:
 * - Track: #2F3445FF (h=4dp)
 * - Fill: #ADC6FFFF (h=4dp)
 * - Thumb: #ADC6FFFF (w=12dp, h=12dp, shadow), draggable via pointerInput
 * - Below track: [Previous Chapter] — "102 / 320" centered — "32%" — [Next Chapter]
 * - Padding [16, 20, 32, 20] (start, end, bottom, top)
 *
 * @param progressPercent 0f–100f progress value
 * @param label Position label like "3 / 10" or "45 / 200"
 * @param onProgressChange Called when user drags thumb to a new percentage
 * @param onPreviousChapter Called when previous chapter button is tapped
 * @param onNextChapter Called when next chapter button is tapped
 * @param canGoPrevious Whether previous navigation is allowed
 * @param canGoNext Whether next navigation is allowed
 */
@Composable
fun ReadingProgressBar(
    progressPercent: Float,
    label: String,
    onProgressChange: ((Float) -> Unit)? = null,
    onPreviousChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    onRotateScreen: (() -> Unit)? = null,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progressPercent.coerceIn(0f, 100f)
    val progressFraction = clampedProgress / 100f

    // Animate the fill smoothly
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        label = "progressFill"
    )

    var trackWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp, top = 20.dp)
    ) {
        // ── Progress bar track + thumb ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp) // extra height for touch target
                .onSizeChanged { trackWidth = it.width }
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2F3445))
            )

            // Fill
            if (trackWidth > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFADC6FF))
                )
            }

            // Draggable thumb
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.CenterStart)
                    .offset(
                        x = with(density) {
                            (trackWidth * animatedProgress).toFloat()
                                .coerceIn(0f, trackWidth.toFloat())
                                .toDp()
                        } - 6.dp // center the 12dp thumb on the position
                    )
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFFADC6FF))
                    .pointerInput(Unit) {
                        if (onProgressChange != null) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val newFraction =
                                    (change.position.x / trackWidth).coerceIn(0f, 1f)
                                onProgressChange(newFraction * 100f)
                            }
                        }
                    }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Navigation buttons + labels ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rotation button
            if (onRotateScreen != null) {
                IconButton(
                    onClick = onRotateScreen,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = stringResource(R.string.reader_rotate_screen),
                        tint = Color(0xFF718096),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }

            // Previous chapter
            IconButton(
                onClick = { onPreviousChapter?.invoke() },
                enabled = canGoPrevious,
                modifier = Modifier.size(36.dp)
            ) {
                Text(
                    text = "<",
                    color = if (canGoPrevious) Color(0xFFADC6FF) else Color(0xFF4A5568),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Page label
            Text(
                text = label.ifEmpty { stringResource(R.string.reader_progress_empty) },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF718096),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            // Percentage
            Text(
                text = "${clampedProgress.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFADC6FF),
                fontWeight = FontWeight.Bold
            )

            // Next chapter
            IconButton(
                onClick = { onNextChapter?.invoke() },
                enabled = canGoNext,
                modifier = Modifier.size(36.dp)
            ) {
                Text(
                    text = ">",
                    color = if (canGoNext) Color(0xFFADC6FF) else Color(0xFF4A5568),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
