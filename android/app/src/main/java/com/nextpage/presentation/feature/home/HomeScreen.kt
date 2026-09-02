package com.nextpage.presentation.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.screen.HomeTags
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.HomeUiState
import com.nextpage.presentation.viewmodel.HomeViewModel
import com.nextpage.ui.components.atoms.NextPageEmptyState
import com.nextpage.ui.components.molecules.NextPageHeader
import com.nextpage.ui.components.molecules.NotificationSheet
import com.nextpage.ui.icons.NextPageIcons

@Composable
fun HomeScreen(contentPadding: PaddingValues, viewModel: HomeViewModel, onNavigateToLibrary: () -> Unit, onNavigateToHighlights: () -> Unit, onNavigateToSettings: () -> Unit, onOpenAccount: () -> Unit = {}, onNavigateToStatistics: () -> Unit, onBookSelected: (String, String, String) -> Unit, onContinueReading: (String, String?, String) -> Unit, onImportBook: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(uiState = uiState, contentPadding = contentPadding, onNavigateToLibrary = onNavigateToLibrary, onNavigateToHighlights = onNavigateToHighlights, onNavigateToSettings = onNavigateToSettings, onOpenAccount = onOpenAccount, onNavigateToStatistics = onNavigateToStatistics, onBookSelected = onBookSelected, onContinueReading = onContinueReading, onImportBook = onImportBook, onSearchQueryChange = viewModel::onSearchQueryChanged, onToggleSearch = viewModel::onToggleSearch)
}

@Composable
fun HomeScreenContent(uiState: HomeUiState, contentPadding: PaddingValues, onNavigateToLibrary: () -> Unit, onNavigateToHighlights: () -> Unit, onNavigateToSettings: () -> Unit, onOpenAccount: () -> Unit = {}, onNavigateToStatistics: () -> Unit, onBookSelected: (String, String, String) -> Unit, onContinueReading: (String, String?, String) -> Unit, onImportBook: () -> Unit, onSearchQueryChange: (String) -> Unit, onToggleSearch: () -> Unit) {
    var showNotifications by remember { mutableStateOf(false) }
    if (showNotifications) NotificationSheet(onDismiss = { showNotifications = false })
    LazyColumn(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 24.dp).testTag(HomeTags.SCREEN_ROOT), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NextPageDimens.spacingMd)) {
        if (uiState.showSearch) {
            item { SearchBarSection(searchQuery = uiState.searchQuery, onSearchQueryChange = onSearchQueryChange, onCloseSearch = onToggleSearch) }
            if (uiState.searchResults.isNotEmpty()) { item { SearchResultsList(results = uiState.searchResults, onBookSelected = onBookSelected) } }
            else if (uiState.searchQuery.isNotBlank()) { item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { NextPageEmptyState(icon = NextPageIcons.Search, title = stringResource(R.string.library_search_no_results), subtitle = stringResource(R.string.library_search_empty_subtitle)) } } }
        }
        if (!uiState.showSearch) {
            item { NextPageHeader(title = stringResource(R.string.home_nextpage_title), avatarImageUrl = uiState.avatarUrl, avatarInitials = uiState.userName.take(1).uppercase(), onAvatarClick = onOpenAccount, avatarContentDescription = stringResource(R.string.home_avatar_content_description), onSearchClick = onToggleSearch, onNotificationsClick = { showNotifications = true }) }
            item { GreetingSection(userName = uiState.userName) }
            item { TodaySummarySection(minutesReadToday = uiState.minutesReadToday, sessionsToday = uiState.sessionsToday, currentStreak = uiState.currentStreak) }
            item { ContinueReadingSection(books = uiState.currentBooks, progressPercentByBook = uiState.progressPercentByBook, onBookSelected = onBookSelected, onContinueReading = onContinueReading) }
            item { MyBookshelfSection(books = uiState.recentBooks, onViewAll = onNavigateToLibrary, onBookSelected = onBookSelected) }
            item { QuickAccessSection(onImportBook = onImportBook, onHighlights = onNavigateToHighlights, onStatistics = onNavigateToStatistics, onSettings = onNavigateToSettings) }
        }
        item { Spacer(modifier = Modifier.height(NextPageDimens.spacingMd)) }
        if (com.nextpage.BuildConfig.DEBUG) {
            item { Spacer(modifier = Modifier.height(12.dp)); Text(text = stringResource(R.string.home_version_format, com.nextpage.BuildConfig.VERSION_NAME, com.nextpage.BuildConfig.GIT_SHA, com.nextpage.BuildConfig.BUILD_TIME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) }
            item { Text(text = stringResource(R.string.debug_version_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        val sampleBook = Book(id = "book-1", title = "The Pragmatic Programmer", author = "David Thomas", coverPath = null, filePath = "/books/pragmatic.epub", format = "epub", updatedAtEpochMillis = 1L)
        HomeScreenContent(uiState = HomeUiState(userName = "María", minutesReadToday = 42, sessionsToday = 3, dailyProgressPercent = 0.5f, currentBooks = listOf(sampleBook), recentBooks = listOf(sampleBook), isLoading = false, showSearch = false, searchQuery = "", searchResults = emptyList()), contentPadding = PaddingValues(16.dp), onNavigateToLibrary = {}, onNavigateToHighlights = {}, onNavigateToSettings = {}, onNavigateToStatistics = {}, onBookSelected = { _, _, _ -> }, onContinueReading = { _, _, _ -> }, onImportBook = {}, onSearchQueryChange = {}, onToggleSearch = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        val sampleBook = Book(id = "book-1", title = "The Pragmatic Programmer", author = "David Thomas", coverPath = null, filePath = "/books/pragmatic.epub", format = "epub", updatedAtEpochMillis = 1L)
        HomeScreenContent(uiState = HomeUiState(userName = "María", minutesReadToday = 42, sessionsToday = 3, dailyProgressPercent = 0.5f, currentBooks = listOf(sampleBook), recentBooks = listOf(sampleBook), isLoading = false, showSearch = false, searchQuery = "", searchResults = emptyList()), contentPadding = PaddingValues(16.dp), onNavigateToLibrary = {}, onNavigateToHighlights = {}, onNavigateToSettings = {}, onNavigateToStatistics = {}, onBookSelected = { _, _, _ -> }, onContinueReading = { _, _, _ -> }, onImportBook = {}, onSearchQueryChange = {}, onToggleSearch = {})
    }
}
