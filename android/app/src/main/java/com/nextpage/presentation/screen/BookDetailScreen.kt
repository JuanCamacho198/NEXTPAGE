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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.presentation.theme.BgHeader
import com.nextpage.presentation.theme.BgMain
import com.nextpage.presentation.theme.BgSurface
import com.nextpage.presentation.theme.BorderSubtle
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.presentation.theme.PrimaryBlue
import com.nextpage.presentation.theme.TextPrimary
import com.nextpage.presentation.theme.TextSecondary
import com.nextpage.presentation.viewmodel.BookDetailViewModel

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
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reader_cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgHeader,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BgMain
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
                        color = TextSecondary,
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
        val context = LocalContext.current
        val imageRequest = remember(book.coverPath) {
            ImageRequest.Builder(context)
                .data(book.coverPath?.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.cover_placeholder)
                .error(R.drawable.cover_error)
                .fallback(R.drawable.cover_placeholder)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = stringResource(R.string.library_cover_content_description),
            modifier = Modifier
                .width(128.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(NextPageDimens.cardCornerRadius)),
            contentScale = ContentScale.Crop
        )

        // Title
        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Author
        Text(
            text = book.author ?: stringResource(R.string.library_author_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
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
                    imageVector = if (star <= currentRating) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        R.string.book_detail_rating_content_description, star
                    ),
                    tint = if (star <= currentRating) com.nextpage.presentation.theme.AccentYellow else BorderSubtle,
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
            containerColor = BgSurface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
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
                    "${progress.percentage.toInt()}%"
                } else {
                    "0%"
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
    return if (book.format == "pdf" && book.totalPages != null) {
        book.totalPages.toString()
    } else if (book.format == "epub" && book.totalPages != null) {
        // For EPUB, totalPages represents the number of reading order items
        book.totalPages.toString()
    } else {
        stringResource(R.string.book_detail_na)
    }
}

@Composable
private fun getChaptersDisplayText(book: Book): String {
    return if (book.totalPages != null) {
        book.totalPages.toString()
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
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
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
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = synopsis ?: stringResource(R.string.book_detail_no_synopsis),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (synopsis != null && synopsis.length > 200) {
            TextButton(onClick = { expanded = !expanded }) {
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
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Page indicator
        Text(
            text = getPageDisplayText(progress, book),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar (percentage is stored as 0-100, convert to 0-1 for Compose)
        LinearProgressIndicator(
            progress = { (progress?.percentage ?: 0f) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(NextPageDimens.progressBarHeight)
                .clip(RoundedCornerShape(9999.dp)),
            color = PrimaryBlue,
            trackColor = BorderSubtle,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Continue/Start button
        androidx.compose.material3.Button(
            onClick = {
                onContinueReading(book.id, book.filePath, book.format)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(NextPageDimens.cardCornerRadius)
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
