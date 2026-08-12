package com.nextpage.presentation.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.presentation.viewmodel.BookDetailViewModel
import com.nextpage.ui.components.atoms.CoverThumbnail
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.icons.NextPageIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    contentPadding: PaddingValues,
    bookId: String,
    libraryRepository: LibraryRepository,
    onNavigateBack: () -> Unit,
    onContinueReading: (String, String?, String) -> Unit
) {
    val viewModel: BookDetailViewModel = viewModel(
        factory = BookDetailViewModel.Factory(bookId, libraryRepository)
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    NextPageButton(
                        onClick = onNavigateBack,
                        variant = NextPageButtonVariant.ICON
                    ) {
                        Icon(
                            imageVector = NextPageIcons.ArrowBack,
                            contentDescription = stringResource(R.string.reader_cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.book == null -> {
                    Text(
                        text = stringResource(R.string.book_detail_error_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    BookDetailContent(
                        book = state.book!!,
                        progress = state.readingProgress,
                        onRatingChanged = { viewModel.updateRating(it) },
                        onContinueReading = onContinueReading
                    )
                }
            }
        }
    }
}

@Composable
private fun BookDetailContent(
    book: Book,
    progress: ReadingProgress?,
    onRatingChanged: (Int?) -> Unit,
    onContinueReading: (String, String?, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        // Section 1: Hero
        BookHeroSection(
            book = book,
            currentRating = book.userRating ?: 0,
            onRatingChanged = onRatingChanged
        )

        // Section 2: Metadata Grid
        MetadataGridCard(
            book = book,
            progress = progress
        )

        // Section 3: Synopsis
        SynopsisSection(synopsis = book.description)

        // Section 4: Reading Progress
        ReadingProgressSection(
            progress = progress,
            book = book,
            onContinueReading = onContinueReading
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ─── Section 1: Hero ───────────────────────────────────────────────────

@Composable
private fun BookHeroSection(
    book: Book,
    currentRating: Int,
    onRatingChanged: (Int?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cover image
        CoverThumbnail(
            coverPath = book.coverPath,
            modifier = Modifier
                .width(128.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(NextPageDimens.cardCornerRadius))
        )

        // Title
        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Author
        Text(
            text = book.author ?: stringResource(R.string.library_author_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Rating Stars
        RatingStars(
            currentRating = currentRating,
            onRatingChanged = onRatingChanged
        )
    }
}

@Composable
private fun RatingStars(
    currentRating: Int,
    onRatingChanged: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.Center) {
        (1..5).forEach { star ->
            IconButton(onClick = {
                val newRating = if (currentRating == star) 0 else star
                onRatingChanged(newRating)
            }) {
                Icon(
                    imageVector = if (star <= currentRating) NextPageIcons.Star else NextPageIcons.StarBorder,
                    contentDescription = stringResource(
                        R.string.book_detail_rating_content_description, star
                    ),
                    tint = if (star <= currentRating) com.nextpage.presentation.theme.AccentYellow else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ─── Section 2: Metadata Grid Card ─────────────────────────────────────

@Composable
private fun MetadataGridCard(
    book: Book,
    progress: ReadingProgress?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NextPageDimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetadataCell(
                label = stringResource(R.string.book_detail_pages_label),
                value = getPagesDisplayText(book)
            )
            MetadataCell(
                label = stringResource(R.string.book_detail_chapters_label),
                value = getChaptersDisplayText(book)
            )
            MetadataCell(
                label = stringResource(R.string.book_detail_progress_label),
                value = if (progress != null) {
                    stringResource(R.string.format_percent, progress.percentage.toInt())
                } else {
                    stringResource(R.string.format_percent, 0)
                }
            )
            MetadataCell(
                label = stringResource(R.string.book_detail_last_read_label),
                value = if (progress != null) {
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                        progress.updatedAtEpochMillis
                    ).toString()
                } else {
                    stringResource(R.string.book_detail_na)
                }
            )
        }
    }
}

@Composable
private fun getPagesDisplayText(book: Book): String {
    return when {
        book.format == "pdf" && book.totalPages != null ->
            book.totalPages.toString() // PDF has real page count
        book.format == "epub" && book.totalPages != null ->
            // EPUB estimated pages with ≈ prefix
            stringResource(R.string.book_detail_estimated_pages, book.totalPages)
        else ->
            stringResource(R.string.book_detail_na)
    }
}

@Composable
private fun getChaptersDisplayText(book: Book): String {
    return if (book.format == "epub" && book.chapterCount != null) {
        book.chapterCount.toString()
    } else {
        stringResource(R.string.book_detail_na)
    }
}

@Composable
private fun MetadataCell(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Section 3: Synopsis ───────────────────────────────────────────────

@Composable
private fun SynopsisSection(synopsis: String?) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.book_detail_synopsis),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = synopsis ?: stringResource(R.string.book_detail_no_synopsis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (synopsis != null && synopsis.length > 200) {
            NextPageButton(
                onClick = { expanded = !expanded },
                variant = NextPageButtonVariant.TEXT
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.book_detail_show_less)
                    } else {
                        stringResource(R.string.book_detail_show_more)
                    }
                )
            }
        }
    }
}

// ─── Section 4: Reading Progress ───────────────────────────────────────

@Composable
private fun ReadingProgressSection(
    progress: ReadingProgress?,
    book: Book,
    onContinueReading: (String, String?, String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.book_detail_progress_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Page indicator
        Text(
            text = getPageDisplayText(progress, book),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar (percentage is stored as 0-100, convert to 0-1 for Compose)
        LinearProgressIndicator(
            progress = { (progress?.percentage ?: 0f) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(NextPageDimens.progressBarHeight)
                .clip(RoundedCornerShape(9999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Continue/Start button
        NextPageButton(
            onClick = {
                onContinueReading(book.id, book.filePath, book.format)
            },
            variant = NextPageButtonVariant.FILLED,
            shape = RoundedCornerShape(NextPageDimens.cardCornerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = if (progress != null && progress.percentage > 0f) {
                    stringResource(R.string.book_detail_continue_reading)
                } else {
                    stringResource(R.string.book_detail_start_reading)
                }
            )
        }
    }
}

@Composable
private fun getPageDisplayText(
    progress: ReadingProgress?,
    book: Book
): String {
    if (book.format == "epub") {
        // EPUB is reflowable — show progress percentage instead of page numbers
        val pct = progress?.percentage?.toInt()
        return if (pct != null) {
            stringResource(R.string.book_detail_progress_percent, pct)
        } else {
            stringResource(R.string.book_detail_no_pages)
        }
    }
    // PDF: show page X of Y
    val page = progress?.currentPage
    val total = book.totalPages
    return when {
        page != null && total != null -> stringResource(
            R.string.book_detail_page_x_of_y, page, total
        )
        page != null -> stringResource(R.string.book_detail_page_x, page)
        total != null -> stringResource(R.string.book_detail_page_x_of_y, 0, total)
        else -> stringResource(R.string.book_detail_no_pages)
    }
}
