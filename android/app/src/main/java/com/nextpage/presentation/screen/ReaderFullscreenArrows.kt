package com.nextpage.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R

// ── Reader Design Colors ──────────────────────────────────────────
private val HEADER_FG = Color(0xFFDDE2F8)
private val ARROW_BG = Color(0x552F3445)

/**
 * Side navigation arrows shown while the reader is in fullscreen mode.
 *
 * Two large translucent circular buttons, vertically centered on the left
 * and right edges of the screen. Tapping the left arrow turns to the
 * previous page/chapter; tapping the right arrow advances.
 *
 * Subtle (~33% alpha background, 40dp icon) so they don't distract from
 * reading, but always reachable without leaving fullscreen.
 *
 * Intended to live above the WebView content. Should be hidden while a
 * text selection is active so the arrow hit areas do not steal the
 * selection drag gesture.
 */
@Composable
fun ReaderFullscreenArrows(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        FullscreenArrowButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.fullscreen_prev_page),
            onClick = onPrevious,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        )
        FullscreenArrowButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.fullscreen_next_page),
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
    }
}

@Composable
private fun FullscreenArrowButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ARROW_BG)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = HEADER_FG,
            modifier = Modifier.size(40.dp)
        )
    }
}
