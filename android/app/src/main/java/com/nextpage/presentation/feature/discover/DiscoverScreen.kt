package com.nextpage.presentation.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextpage.R
import com.nextpage.presentation.viewmodel.DiscoverStatus
import com.nextpage.presentation.viewmodel.DiscoverUiState
import com.nextpage.presentation.viewmodel.DiscoverViewModel
import com.nextpage.ui.icons.NextPageIcons

/**
 * Discover shell: header, search field, trending chips, then exactly one body
 * state. The body is owned per state — rails own their Loading/Loaded/Hidden
 * lifecycle, so the shell never shows a screen-level spinner for them.
 *
 * @param userInitial Initial of the signed-in user, resolved by the nav call site
 *   (the screen no longer reaches into a service locator). Null renders a
 *   neutral avatar placeholder.
 * @param onOpenSection Opens the "Ver todo" list for a rail, forwarding the
 *   already-localized section title.
 */
@Composable
fun DiscoverScreen(
    contentPadding: PaddingValues,
    viewModel: DiscoverViewModel,
    userInitial: String? = null,
    onOpenSection: (DiscoverRailState.Loaded, String) -> Unit = { _, _ -> },
    onNavigateToSettingsAddons: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trendingChips = trendingChips()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DiscoverHeader(
            title = stringResource(R.string.discover_title),
            subtitle = headerSubtitle(uiState),
            userInitial = userInitial,
            avatarContentDescription = stringResource(R.string.discover_avatar_content_desc)
        )

        DiscoverSearchField(
            query = uiState.query,
            isSearching = uiState.isSearching,
            onQueryChange = viewModel::onQueryChange,
            onClear = { viewModel.onQueryChange("") },
            onSearch = viewModel::searchFirstPage
        )

        if (uiState.status == DiscoverStatus.IDLE) {
            DiscoverChipRow(
                chips = trendingChips,
                onChipClick = { index ->
                    val term = trendingChips[index].label
                    viewModel.onQueryChange(term)
                    viewModel.searchFirstPage()
                }
            )
        }

        // Source narrowing applies to the merged result list, so the chips are
        // only meaningful once a search has produced results.
        if (
            uiState.sources.isNotEmpty() &&
            (
                uiState.status == DiscoverStatus.LOADED ||
                    uiState.status == DiscoverStatus.LOADING_MORE ||
                    uiState.status == DiscoverStatus.EMPTY
                )
        ) {
            DiscoverSourceFilterRow(
                sources = uiState.sources,
                selected = uiState.sourceFilter,
                onSelect = viewModel::setSourceFilter
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (uiState.status) {
                DiscoverStatus.IDLE -> DiscoverIdleRails(
                    rails = uiState.rails,
                    attributionNames = uiState.attributionNames,
                    onOpenBook = viewModel::openDetail,
                    onSeeAll = onOpenSection,
                    onNavigateToSettingsAddons = onNavigateToSettingsAddons
                )
                DiscoverStatus.LOADING -> DiscoverSkeletonState()
                DiscoverStatus.EMPTY -> DiscoverEmptyState(
                    query = uiState.query,
                    onSuggestionClick = { term ->
                        viewModel.onQueryChange(term)
                        viewModel.searchFirstPage()
                    }
                )
                DiscoverStatus.ERROR -> DiscoverErrorState(onRetry = viewModel::retry)
                DiscoverStatus.OFFLINE -> DiscoverOfflineState(onRetry = viewModel::retry)
                DiscoverStatus.LOADED, DiscoverStatus.LOADING_MORE -> DiscoverResultsGrid(
                    books = uiState.visibleBooks,
                    nextPage = uiState.nextPage,
                    isLoadingMore = uiState.status == DiscoverStatus.LOADING_MORE,
                    onOpen = viewModel::openDetail,
                    onLoadNext = viewModel::loadNextPage,
                    attributionNames = uiState.attributionNames
                )
            }
        }

        DiscoverDetailSheet(
            detail = uiState.detail,
            detailStatus = uiState.detailStatus,
            download = uiState.download,
            onDownload = viewModel::startDownload,
            onCancelDownload = viewModel::cancelDownload,
            onDismiss = viewModel::dismissDetail
        )
    }
}

/**
 * IDLE body: vertically scrollable stack of featured rails. A Hidden rail
 * composes nothing, so a partially servable IDLE shows only the rails that
 * actually resolved.
 */
@Composable
private fun DiscoverIdleRails(
    rails: List<DiscoverRailState>,
    onOpenBook: (String) -> Unit,
    onSeeAll: (DiscoverRailState.Loaded, String) -> Unit,
    onNavigateToSettingsAddons: () -> Unit,
    attributionNames: Map<String, String> = emptyMap()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        rails.forEach { rail ->
            DiscoverRailSection(
                state = rail,
                onBookClick = onOpenBook,
                onSeeAll = onSeeAll,
                attributionNames = attributionNames
            )
        }
        DiscoverManageAddonsEntry(onClick = onNavigateToSettingsAddons)
    }
}

@Composable
private fun headerSubtitle(uiState: DiscoverUiState): String = when {
    uiState.status == DiscoverStatus.LOADED || uiState.status == DiscoverStatus.LOADING_MORE ->
        if (uiState.totalCount > 0) {
            stringResource(R.string.discover_results_count, uiState.totalCount)
        } else {
            stringResource(R.string.discover_subtitle)
        }
    else -> stringResource(R.string.discover_subtitle)
}

@Composable
private fun trendingChips(): List<DiscoverChip> = listOf(
    DiscoverChip(label = stringResource(R.string.discover_trending_all), icon = NextPageIcons.Sparkle),
    DiscoverChip(label = stringResource(R.string.discover_trending_popular), icon = NextPageIcons.Flame),
    DiscoverChip(label = stringResource(R.string.discover_trending_scifi), icon = NextPageIcons.Sparkle),
    DiscoverChip(label = stringResource(R.string.discover_trending_classics), icon = NextPageIcons.Book)
)
