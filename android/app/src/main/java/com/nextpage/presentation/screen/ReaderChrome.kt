package com.nextpage.presentation.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.presentation.viewmodel.ReaderUiState

// ── Reader Design Colors ──────────────────────────────────────────
private val READER_BG = Color(0xFF0D1322)
private val HEADER_FG = Color(0xFFDDE2F8)
private val HEADER_AUTHOR_FG = Color(0xFFC2C6D6)
private val BUTTON_BG = Color(0xFF2F3445)

// ── ReaderChrome: structural layout ────────────────────────────────

/**
 * Structural layout for the reader screen.
 * Renders the header, content slot (format-specific), footer,
 * and overlays (search, highlights, settings, sleep timer, etc.)
 * on top of the dark reader background.
 */
@Composable
fun ReaderChrome(
    isFullscreen: Boolean,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
    overlays: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(READER_BG)
            .padding(contentPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullscreen) header()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }

            if (!isFullscreen) footer()
        }

        overlays()
    }
}

// ── Header Component ──────────────────────────────────────────────

@Composable
fun ReaderHeader(
    uiState: ReaderUiState,
    onNavigateBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleHighlights: () -> Unit,
    onCreateBookmark: () -> Unit,
    onToggleSplitSettings: () -> Unit = {},
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            // Fullscreen
            HeaderActionButton(
                icon = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = stringResource(
                    if (uiState.isFullscreen) R.string.fullscreen_exit else R.string.fullscreen_enter
                ),
                onClick = onToggleFullscreen
            )

            // Search
            HeaderActionButton(
                icon = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_label),
                onClick = onToggleSearch
            )

            // aA Typography
            HeaderActionButton(
                icon = Icons.Default.TextIncrease,
                contentDescription = stringResource(R.string.reader_typography),
                onClick = onToggleSplitSettings
            )

            // Index / TOC
            HeaderActionButton(
                icon = Icons.AutoMirrored.Filled.Toc,
                contentDescription = stringResource(R.string.reader_toc),
                onClick = onToggleHighlights
            )

            // Bookmark
            HeaderActionButton(
                icon = Icons.Default.BookmarkBorder,
                contentDescription = stringResource(R.string.reader_add_bookmark),
                onClick = onCreateBookmark
            )
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
