package com.nextpage.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.R
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.HighlightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    contentPadding: PaddingValues,
    viewModel: HighlightsViewModel = viewModel()
) {
    val highlights by viewModel.highlights.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_highlights)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.highlights_tab, highlights.size)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.bookmarks_tab, bookmarks.size)) }
                )
            }

            when (selectedTab) {
                0 -> HighlightsList(highlights = highlights)
                1 -> BookmarksList(bookmarks = bookmarks)
            }
        }
    }
}

@Composable
private fun HighlightsList(highlights: List<Highlight>) {
    if (highlights.isEmpty()) {
        EmptyState(message = stringResource(R.string.highlights_empty))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(NextPageDimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
        ) {
            items(highlights, key = { it.id }) { highlight ->
                HighlightItem(highlight = highlight)
            }
        }
    }
}

@Composable
private fun HighlightItem(highlight: Highlight) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(NextPageDimens.spacingMd)
        ) {
            Text(
                text = "\"${highlight.textContent}\"",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!highlight.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
                Text(
                    text = "Note: ${highlight.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
            Text(
                text = stringResource(R.string.highlight_location, highlight.cfiRange),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun BookmarksList(bookmarks: List<Bookmark>) {
    if (bookmarks.isEmpty()) {
        EmptyState(message = stringResource(R.string.bookmarks_empty))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(NextPageDimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
        ) {
            items(bookmarks, key = { it.id }) { bookmark ->
                BookmarkItem(bookmark = bookmark)
            }
        }
    }
}

@Composable
private fun BookmarkItem(bookmark: Bookmark) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NextPageDimens.spacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.titleOrSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.bookmark_location, bookmark.cfiLocation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
