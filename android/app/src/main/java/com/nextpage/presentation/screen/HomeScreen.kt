package com.nextpage.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.HomeViewModel
import com.nextpage.ui.components.atoms.CoverThumbnail
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageEmptyState
import com.nextpage.ui.components.atoms.NextPageTextField
import com.nextpage.ui.components.molecules.NextPageHeader
import com.nextpage.ui.components.molecules.NotificationSheet

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToHighlights: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onBookSelected: (String, String, String) -> Unit,
    onContinueReading: (String, String?, String) -> Unit,
    onImportBook: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNotifications by remember { mutableStateOf(false) }

    if (showNotifications) {
        NotificationSheet(onDismiss = { showNotifications = false })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
    ) {
        // Search Bar (always at top when visible)
        if (uiState.showSearch) {
            item {
                SearchBarSection(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onCloseSearch = { viewModel.onToggleSearch() }
                )
            }

            // Search Results
            if (uiState.searchResults.isNotEmpty()) {
                item {
                    SearchResultsList(
                        results = uiState.searchResults,
                        onBookSelected = onBookSelected
                    )
                }
            } else if (uiState.searchQuery.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NextPageEmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(R.string.library_search_no_results),
                            subtitle = stringResource(R.string.library_search_empty_subtitle)
                        )
                    }
                }
            }
        }

        // Only show regular content when search is hidden
        if (!uiState.showSearch) {
            // 1. Header
            item {
                NextPageHeader(
                    title = stringResource(R.string.home_nextpage_title),
                    avatarImageUrl = uiState.avatarUrl,
                    avatarInitials = uiState.userName.take(1).uppercase(),
                    onSearchClick = { viewModel.onToggleSearch() },
                    onNotificationsClick = { showNotifications = true }
                )
            }

            // 2. Greeting — collects own state
            item { GreetingSection(viewModel = viewModel) }

            // 3. TodaySummary — collects own state
            item { TodaySummarySection(viewModel = viewModel) }

            // 4. ContinueReading
            item {
                ContinueReadingSection(
                    currentBook = uiState.currentBook,
                    progress = uiState.currentBookProgress,
                    onBookSelected = onBookSelected,
                    onContinueReading = onContinueReading
            )
            }

            // 5. MyBookshelf
            item {
                MyBookshelfSection(
                    books = uiState.recentBooks,
                    onViewAll = onNavigateToLibrary,
                    onBookSelected = onBookSelected
                )
            }

            // 6. QuickAccess
            item {
                QuickAccessSection(
                    onImportBook = onImportBook,
                    onHighlights = onNavigateToHighlights,
                    onStatistics = onNavigateToStatistics,
                    onSettings = onNavigateToSettings
                )
            }
        }

        // 7. Bottom spacer
        item { Spacer(modifier = Modifier.height(NextPageDimens.spacingMd)) }

        // 8. Debug version label (debug builds only)
        if (com.nextpage.BuildConfig.DEBUG) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.home_version_format,
                        com.nextpage.BuildConfig.VERSION_NAME,
                        com.nextpage.BuildConfig.GIT_SHA,
                        com.nextpage.BuildConfig.BUILD_TIME
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.debug_version_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ─── Section 2: Greeting ─────────────────────────────────────────────

@Composable
private fun GreetingSection(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    Column {
        Text(
            text = stringResource(R.string.home_greeting, uiState.userName),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Section 3: Today Summary ────────────────────────────────────────

@Composable
private fun TodaySummarySection(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    Column {
        Text(
            text = stringResource(R.string.home_today_summary_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
        ) {
            StatCard(
                icon = Icons.Outlined.Schedule,
                value = "${uiState.minutesReadToday}",
                label = stringResource(R.string.home_minutes),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                value = "${uiState.sessionsToday}",
                label = stringResource(R.string.home_sessions),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Outlined.BarChart,
                value = stringResource(R.string.format_percent, (uiState.dailyProgressPercent * 100).toInt()),
                label = stringResource(R.string.home_progress),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(NextPageDimens.spacingSm),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NextPageDimens.spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Section 4: Continue Reading ─────────────────────────────────────

@Composable
private fun ContinueReadingSection(
    currentBook: Book?,
    progress: Float = 0f,
    onBookSelected: (String, String, String) -> Unit,
    onContinueReading: (String, String?, String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.home_continue_reading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))

        if (currentBook != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onBookSelected(currentBook.id, currentBook.filePath, currentBook.format)
                    },
                shape = RoundedCornerShape(NextPageDimens.spacingSm),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(modifier = Modifier.padding(NextPageDimens.spacingMd)) {
                    // Cover thumbnail — 80x120dp rounded
                    CoverThumbnail(
                        coverPath = currentBook.coverPath,
                        modifier = Modifier
                            .width(80.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(NextPageDimens.spacingXs))
                    )

                    Spacer(modifier = Modifier.width(NextPageDimens.spacingMd))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentBook.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        currentBook.author?.let { author ->
                            Text(
                                text = author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))

                        // Progress bar
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))

                        NextPageButton(
                            onClick = {
                                onContinueReading(currentBook.id, currentBook.filePath, currentBook.format)
                            },
                            variant = NextPageButtonVariant.FILLED,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_continuar),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NextPageDimens.spacingSm),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.home_no_current_book),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(NextPageDimens.spacingMd)
                )
            }
        }
    }
}

// ─── Section 5: My Bookshelf ─────────────────────────────────────────

@Composable
private fun MyBookshelfSection(
    books: List<Book>,
    onViewAll: () -> Unit,
    onBookSelected: (String, String, String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_my_bookshelf_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            NextPageButton(
                onClick = onViewAll,
                variant = NextPageButtonVariant.TEXT
            ) {
                Text(text = stringResource(R.string.home_ver_todo))
            }
        }

        if (books.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
            ) {
                items(books, key = { it.id }) { book ->
                    BookshelfCard(
                        book = book,
                        onClick = {
                            onBookSelected(book.id, book.filePath, book.format)
                        }
                    )
                }
            }
        } else {
            NextPageEmptyState(
                icon = Icons.Outlined.Bookmark,
                title = stringResource(R.string.home_bookshelf_empty_title),
                subtitle = stringResource(R.string.home_bookshelf_empty_subtitle),
                modifier = Modifier.padding(vertical = NextPageDimens.spacingMd)
            )
        }
    }
}

@Composable
private fun BookshelfCard(
    book: Book,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(NextPageDimens.spacingSm),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(NextPageDimens.spacingSm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CoverThumbnail(
                coverPath = book.coverPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(NextPageDimens.spacingXs))
            )
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Section 6: Quick Access ──────────────────────────────────────────

@Composable
private fun QuickAccessSection(
    onImportBook: () -> Unit,
    onHighlights: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.home_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))

        Column(verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
            ) {
                QuickAccessButton(
                    icon = Icons.Outlined.UploadFile,
                    label = stringResource(R.string.home_action_import_book),
                    onClick = onImportBook,
                    modifier = Modifier.weight(1f)
                )
                QuickAccessButton(
                    icon = Icons.Outlined.Bookmark,
                    label = stringResource(R.string.home_highlights),
                    onClick = onHighlights,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
            ) {
                QuickAccessButton(
                    icon = Icons.Outlined.BarChart,
                    label = stringResource(R.string.home_action_stats),
                    onClick = onStatistics,
                    modifier = Modifier.weight(1f)
                )
                QuickAccessButton(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(R.string.home_settings),
                    onClick = onSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── Search Bar Section ────────────────────────────────────────────────

@Composable
private fun SearchBarSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NextPageTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = stringResource(R.string.library_search_placeholder),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        NextPageButton(
            onClick = onCloseSearch,
            variant = NextPageButtonVariant.ICON
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.home_close_search)
            )
        }
    }
}

// ─── Search Results List ─────────────────────────────────────────────

@Composable
private fun SearchResultsList(
    results: List<Book>,
    onBookSelected: (String, String, String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.home_search_results),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        results.forEach { book ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onBookSelected(book.id, book.filePath, book.format)
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverThumbnail(
                        coverPath = book.coverPath,
                        modifier = Modifier
                            .width(40.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        book.author?.let { author ->
                            Text(
                                text = author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuickAccessButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(NextPageDimens.spacingSm),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NextPageDimens.spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
