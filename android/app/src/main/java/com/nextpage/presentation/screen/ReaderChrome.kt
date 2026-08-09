package com.nextpage.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nextpage.R
import com.nextpage.debug.DebugPrefs
import com.nextpage.presentation.viewmodel.ReaderUiState
import com.nextpage.ui.icons.NextPageIcons

// ── Reader Design Colors ──────────────────────────────────────────
private val READER_BG = Color(0xFF0D1322)
private val HEADER_FG = Color(0xFFDDE2F8)
private val HEADER_AUTHOR_FG = Color(0xFFC2C6D6)
private val BUTTON_BG = Color(0xFF2F3445)

/** Duration (ms) of the header/footer show/hide animation. */
private const val CHROME_ANIM_MS = 300

// ── ReaderChrome: structural layout ────────────────────────────────

/**
 * Structural layout for the reader screen.
 * Renders the header, content slot (format-specific), footer,
 * and overlays (search, highlights, settings, sleep timer, etc.)
 * on top of the dark reader background.
 *
 * The reader is always in immersive (fullscreen) mode. The header and
 * footer are toggled by [controlsVisible] and animate in/out with a
 * slide + fade transition — they are never removed from the layout
 * abruptly.
 *
 * @param controlsVisible drives the animated show/hide of the header
 *   and footer. Auto-toggled by the caller (tap to show, inactivity to hide).
 * @param contentModifier Modifier applied to the content Box (useful for
 *   attaching tap / gesture handlers that observe content taps).
 */
@Composable
fun ReaderChrome(
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
    overlays: @Composable BoxScope.() -> Unit = {},
    controlsVisible: Boolean = true,
    contentModifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(READER_BG)
            .padding(contentPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = slideInVertically(
                    animationSpec = tween(CHROME_ANIM_MS, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it }
                ) + fadeIn(animationSpec = tween(CHROME_ANIM_MS)),
                exit = slideOutVertically(
                    animationSpec = tween(CHROME_ANIM_MS, easing = FastOutSlowInEasing),
                    targetOffsetY = { -it }
                ) + fadeOut(animationSpec = tween(CHROME_ANIM_MS))
            ) {
                header()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // zIndex lifts the content slot above the footer Box in
                    // the Column, so the SelectionOverlay (and its color
                    // picker popover) render on top of the progress bar.
                    .zIndex(1f)
                    .then(contentModifier)
            ) {
                content()
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = slideInVertically(
                    animationSpec = tween(CHROME_ANIM_MS, easing = FastOutSlowInEasing),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(CHROME_ANIM_MS)),
                exit = slideOutVertically(
                    animationSpec = tween(CHROME_ANIM_MS, easing = FastOutSlowInEasing),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(CHROME_ANIM_MS))
            ) {
                footer()
            }
        }

        overlays()
    }
}

// ── Header Component ──────────────────────────────────────────────

@Composable
fun ReaderHeader(
    uiState: ReaderUiState,
    onNavigateBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleHighlights: () -> Unit,
    onCreateBookmark: () -> Unit,
    onToggleSplitSettings: () -> Unit = {},
    onToggleToc: () -> Unit = {},
    onToggleDebugPanel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(READER_BG)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BUTTON_BG)
        ) {
            Icon(
                imageVector = NextPageIcons.ArrowBack,
                contentDescription = stringResource(R.string.reader_back),
                tint = HEADER_FG,
                modifier = Modifier.size(20.dp)
            )
        }

        // Title + Author centered
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_title),
                style = MaterialTheme.typography.titleSmall,
                color = HEADER_FG,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title
                    ?: stringResource(R.string.reader_author_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = HEADER_AUTHOR_FG,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Action buttons row
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search
            HeaderActionButton(
                icon = NextPageIcons.Search,
                contentDescription = stringResource(R.string.search_label),
                onClick = onToggleSearch
            )

            // aA Typography
            HeaderActionButton(
                icon = NextPageIcons.TextSize,
                contentDescription = stringResource(R.string.reader_typography),
                onClick = onToggleSplitSettings
            )

            // Index / TOC — hidden for books with no chapter list
            if (uiState.chapters.isNotEmpty()) {
                HeaderActionButton(
                    icon = NextPageIcons.ListBullets,
                    contentDescription = stringResource(R.string.reader_toc),
                    onClick = onToggleToc
                )
            }

            // Highlights (Create / pencil icon) — kept for the user-facing
            // highlights sheet. Tapping it should never crash, but the
            // previous wiring also routed long-press on the title to a
            // debug-only force-menu helper that could throw when the
            // selection overlay was already open. Both call sites now go
            // through safe state-only toggles.
            HeaderActionButton(
                icon = NextPageIcons.Pencil,
                contentDescription = stringResource(R.string.reader_highlights_button),
                onClick = onToggleHighlights
            )

            // Bookmark
            HeaderActionButton(
                icon = NextPageIcons.Bookmark,
                contentDescription = stringResource(R.string.reader_add_bookmark),
                onClick = onCreateBookmark
            )

            // Debug panel toggle — gated on runtime DebugPrefs so it works
            // in release builds when the debug toggle is enabled in Settings.
            if (DebugPrefs.isEnabled(LocalContext.current)) {
                HeaderActionButton(
                    icon = NextPageIcons.BugReport,
                    contentDescription = stringResource(R.string.debug_panel_title),
                    onClick = onToggleDebugPanel
                )
            }
        }
    }
}

// ── Header Action Button ──────────────────────────────────────────

@Composable
fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(BUTTON_BG)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = HEADER_FG,
            modifier = Modifier.size(18.dp)
        )
    }
}
