package com.nextpage.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToHighlights: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBookSelected: (String, String, String) -> Unit,
    onImportEpub: () -> Unit,
    onImportPdf: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = NextPageDimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
    ) {
        // Header
        item { HomeHeader() }

        // Today's Summary
        item { TodaySummarySection(minutesRead = uiState.minutesReadToday) }

        // Continue Reading
        item {
            ContinueReadingSection(
                currentBook = uiState.currentBook,
                onBookSelected = onBookSelected
            )
        }

        // My Bookshelf
        item {
            BookshelfSection(
                books = uiState.recentBooks,
                onViewAll = onNavigateToLibrary,
                onBookSelected = onBookSelected
            )
        }

        // Quick Actions
        item {
            QuickActionsSection(
                onImportEpub = onImportEpub,
                onImportPdf = onImportPdf,
                onNotes = onNavigateToHighlights,
                onStats = onNavigateToLibrary
            )
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(NextPageDimens.spacingMd)) }
    }
}

@Composable
private fun HomeHeader() {
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    val today = dateFormat.format(Date())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NextPageDimens.spacingMd)
    ) {
        Text(
            text = stringResource(R.string.home_welcome),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = today.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodaySummarySection(minutesRead: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NextPageDimens.spacingSm),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(NextPageDimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.home_today_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
            Text(
                text = stringResource(R.string.home_minutes_read_today, minutesRead),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ContinueReadingSection(
    currentBook: Book?,
    onBookSelected: (String, String, String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.home_continue_reading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))

        if (currentBook != null) {
            BookCard(
                book = currentBook,
                onClick = {
                    onBookSelected(currentBook.id, currentBook.filePath, currentBook.format)
                }
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NextPageDimens.spacingSm),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.home_no_book_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(NextPageDimens.spacingMd)
                )
            }
        }
    }
}

@Composable
private fun BookshelfSection(
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
                text = stringResource(R.string.home_my_bookshelf),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onViewAll) {
                Text(text = stringResource(R.string.home_view_all))
            }
        }

        if (books.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
            ) {
                items(books) { book ->
                    BookGridCard(
                        book = book,
                        onClick = {
                            onBookSelected(book.id, book.filePath, book.format)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onImportEpub: () -> Unit,
    onImportPdf: () -> Unit,
    onNotes: () -> Unit,
    onStats: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.home_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
        ) {
            QuickActionButton(
                label = stringResource(R.string.home_action_import_epub),
                onClick = onImportEpub,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = stringResource(R.string.home_action_import_pdf),
                onClick = onImportPdf,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = stringResource(R.string.home_action_notes),
                onClick = onNotes,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = stringResource(R.string.home_action_stats),
                onClick = onStats,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(NextPageDimens.spacingSm),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(NextPageDimens.spacingSm),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookCard(
    book: Book,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(NextPageDimens.spacingSm),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(NextPageDimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.title.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(NextPageDimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BookGridCard(
    book: Book,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(NextPageDimens.spacingSm),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(NextPageDimens.spacingSm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.title.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}