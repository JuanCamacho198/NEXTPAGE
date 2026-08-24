package com.nextpage.presentation.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.BookDetailViewModel
import com.nextpage.presentation.viewmodel.parseChipList
import com.nextpage.ui.components.atoms.CoverThumbnail
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.StarRating
import com.nextpage.ui.components.molecules.GenreChipsDisplay
import com.nextpage.ui.components.molecules.TagChipsDisplay
import com.nextpage.ui.icons.NextPageIcons
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    contentPadding: PaddingValues,
    bookId: String,
    libraryRepository: LibraryRepository,
    onNavigateBack: () -> Unit,
    onEditBook: () -> Unit,
    onContinueReading: (String, String?, String) -> Unit
) {
    val viewModel: BookDetailViewModel = viewModel(
        factory = BookDetailViewModel.Factory(bookId, libraryRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showActionsMenu by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(onClick = { state.book?.let { shareBookFile(context, it) } }) {
                        Icon(
                            imageVector = NextPageIcons.Share,
                            contentDescription = stringResource(R.string.book_detail_share_content_description)
                        )
                    }
                    Box {
                        IconButton(onClick = { showActionsMenu = true }) {
                            Icon(
                                imageVector = NextPageIcons.MoreVert,
                                contentDescription = stringResource(R.string.book_detail_more_content_description)
                            )
                        }
                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = NextPageIcons.Pencil,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = { Text(stringResource(R.string.library_menu_edit_metadata)) },
                                onClick = {
                                    showActionsMenu = false
                                    onEditBook()
                                }
                            )
                        }
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
            val book = state.book
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                book == null -> {
                    Text(
                        text = stringResource(R.string.book_detail_error_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    BookDetailContent(
                        book = book,
                        progress = state.readingProgress,
                        onRatingChanged = { viewModel.updateRating(it) },
                        onContinueReading = onContinueReading
                    )
                }
            }
        }
    }
}

/**
 * Launches the system share sheet for a book file, mirroring the
 * [androidx.core.content.FileProvider] flow used by the library context menu
 * (REQ-detail-screen-6 — Share action). Missing files are ignored silently.
 */
private fun shareBookFile(context: Context, book: Book) {
    val mimeType = when (book.format.lowercase()) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        else -> "*/*"
    }
    runCatching {
        val file = File(book.filePath)
        if (!file.exists()) return@runCatching
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.library_share_chooser_title)
            )
        )
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
    ) {
        // Section 1: Hero (full-bleed gradient header, design b3LCZx)
        BookHeroSection(
            book = book,
            rating = book.userRating,
            onRatingChanged = onRatingChanged,
            onContinueReading = onContinueReading
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Section 2: Metadata grid
            MetadataGrid(book = book)

            // Section 3: Synopsis
            SynopsisSection(synopsis = book.description)

            // Section 4: Reading progress card
            ReadingProgressCard(progress = progress, book = book)

            // Section 5: Genres chips (hidden when empty, REQ-detail-screen-5)
            val genres = parseChipList(book.genre)
            if (genres.isNotEmpty()) {
                DetailChipSection(title = stringResource(R.string.book_detail_genres)) {
                    GenreChipsDisplay(
                        genres = genres,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Section 6: Tags chips (hidden when empty)
            val tags = parseChipList(book.tags)
            if (tags.isNotEmpty()) {
                DetailChipSection(title = stringResource(R.string.edit_metadata_tags)) {
                    TagChipsDisplay(
                        tags = tags,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ─── Section 1: Hero (gradient header) ────────────────────────────────

@Composable
private fun BookHeroSection(
    book: Book,
    rating: Int?,
    onRatingChanged: (Int?) -> Unit,
    onContinueReading: (String, String?, String) -> Unit
) {
    val headerBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.surface
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBrush)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            CoverThumbnail(
                coverPath = book.coverPath,
                modifier = Modifier
                    .width(110.dp)
                    .height(165.dp)
                    .clip(RoundedCornerShape(NextPageDimens.cardCornerRadius))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title (24sp / 700 per design)
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author ?: stringResource(R.string.library_author_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.book_detail_my_rating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StarRating(
                        rating = rating,
                        onRatingChanged = onRatingChanged
                    )
                    if (rating != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.book_detail_rating_value, rating / 2.0),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Read button (REQ-detail-screen-6 — "Leer" keeps the reader wiring)
        NextPageButton(
            onClick = { onContinueReading(book.id, book.filePath, book.format) },
            variant = NextPageButtonVariant.FILLED,
            shape = RoundedCornerShape(NextPageDimens.cardCornerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = if (book.progressPercentage > 0f) {
                    stringResource(R.string.book_detail_continue_reading)
                } else {
                    stringResource(R.string.book_detail_start_reading)
                }
            )
        }
    }
}

// ─── Section 2: Metadata grid (6 cells) ───────────────────────────────

@Composable
private fun MetadataGrid(book: Book) {
    val na = stringResource(R.string.book_detail_na)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NextPageDimens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetadataCell(
                    icon = NextPageIcons.Book,
                    label = stringResource(R.string.book_detail_meta_format),
                    value = book.format.uppercase(),
                    modifier = Modifier.weight(1f)
                )
                MetadataCell(
                    icon = NextPageIcons.Language,
                    label = stringResource(R.string.book_detail_meta_language),
                    value = languageDisplayName(book.language, na),
                    modifier = Modifier.weight(1f)
                )
                MetadataCell(
                    icon = NextPageIcons.Info,
                    label = stringResource(R.string.book_detail_meta_publisher),
                    value = book.publisher ?: na,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetadataCell(
                    icon = NextPageIcons.Clock,
                    label = stringResource(R.string.book_detail_meta_published),
                    value = publishedYear(book.publishedDate, na),
                    modifier = Modifier.weight(1f)
                )
                MetadataCell(
                    icon = NextPageIcons.ListBullets,
                    label = stringResource(R.string.book_detail_meta_pages),
                    value = getPagesDisplayText(book),
                    modifier = Modifier.weight(1f)
                )
                MetadataCell(
                    icon = NextPageIcons.Storage,
                    label = stringResource(R.string.book_detail_meta_size),
                    value = formatSizeMb(book.filePath, na),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetadataCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Displays the language display name for an ISO 639-1 code; raw code when unresolvable. */
private fun languageDisplayName(code: String?, fallback: String): String {
    if (code.isNullOrBlank()) return fallback
    val display = Locale.forLanguageTag(code).getDisplayName()
    return if (display.isNotBlank() && !display.equals(code, ignoreCase = true)) display else code
}

/** Extracts the year from an ISO `yyyy-MM-dd` date; fallback when unset. */
private fun publishedYear(iso: String?, fallback: String): String =
    iso?.take(4)?.takeIf { it.all(Char::isDigit) } ?: fallback

private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0

/** File size in MB computed from the book file; fallback when the file is missing. */
private fun formatSizeMb(filePath: String, fallback: String): String {
    val bytes = runCatching { File(filePath).length() }.getOrNull()
        ?: return fallback
    if (bytes <= 0L) return fallback
    val mb = bytes / BYTES_PER_MEGABYTE
    return if (mb >= 100) {
        "${mb.toInt()} MB"
    } else {
        String.format(Locale.US, "%.1f MB", mb)
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

// ─── Section 3: Synopsis ──────────────────────────────────────────────

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
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.book_detail_show_less)
                    } else {
                        stringResource(R.string.book_detail_show_more_chevron)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) NextPageIcons.ChevronUp else NextPageIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Section 4: Reading progress card ─────────────────────────────────

@Composable
private fun ReadingProgressCard(
    progress: ReadingProgress?,
    book: Book
) {
    val percentage = progress?.percentage ?: book.progressPercentage

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NextPageDimens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.book_detail_progress_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.format_percent, percentage.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            LinearProgressIndicator(
                progress = { (percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(9999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )

            if (book.chapterCount != null || book.totalPages != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    book.chapterCount?.let { chapters ->
                        Text(
                            text = stringResource(
                                R.string.book_detail_chapter_x_of_y,
                                estimatedChapter(percentage, chapters),
                                chapters
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    book.totalPages?.let { total ->
                        Text(
                            text = stringResource(
                                R.string.book_detail_pages_of,
                                estimatedCurrentPage(progress, total),
                                total
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Estimated current chapter from progress %, clamped to the chapter count. */
private fun estimatedChapter(percentage: Float, chapterCount: Int): Int =
    (percentage / 100f * chapterCount).roundToInt().coerceIn(1, chapterCount)

/** Real current page when the reader reports one; else estimated from progress %. */
private fun estimatedCurrentPage(progress: ReadingProgress?, totalPages: Int): Int {
    progress?.currentPage?.takeIf { it > 0 }?.let { return it }
    val percentage = progress?.percentage ?: 0f
    return (percentage / 100f * totalPages).roundToInt().coerceIn(0, totalPages)
}

// ─── Sections 5/6: Chips (genres / tags) ──────────────────────────────

@Composable
private fun DetailChipSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

// ─── Previews ─────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun BookDetailScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        BookDetailScreenPreviewContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun BookDetailScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        BookDetailScreenPreviewContent()
    }
}

@Composable
private fun BookDetailScreenPreviewContent() {
    val sampleBook = Book(
        id = "book-1",
        title = "Hábitos Atómicos",
        author = "James Clear",
        coverPath = null,
        filePath = "/books/atomic-habits.epub",
        format = "epub",
        totalPages = 320,
        chapterCount = 20,
        description = "Un sistema comprobado para construir buenos hábitos y eliminar los malos. Pequeños cambios, resultados extraordinarios.",
        genre = "Desarrollo personal",
        language = "es",
        publisher = "Penguin",
        tags = "favoritos, lectura-pendiente",
        publishedDate = "2018-10-16",
        userRating = 9,
        updatedAtEpochMillis = 1L
    )
    val sampleProgress = ReadingProgress(
        id = "progress-1",
        bookId = "book-1",
        cfiLocation = "epubcfi(/6/10)",
        percentage = 32f,
        updatedAtEpochMillis = 1L
    )
    BookDetailContent(
        book = sampleBook,
        progress = sampleProgress,
        onRatingChanged = {},
        onContinueReading = { _, _, _ -> }
    )
}
