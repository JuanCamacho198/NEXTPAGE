package com.nextpage.presentation.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextpage.R
import com.nextpage.data.storage.CoverStorage
import com.nextpage.domain.model.Book
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.EditBookMetadataUiState
import com.nextpage.presentation.viewmodel.EditBookMetadataViewModel
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.molecules.GenreChips
import com.nextpage.ui.components.molecules.TagChips
import com.nextpage.ui.icons.NextPageIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Title/author/publisher field max length (REQ-edit-screen-4/5). */
private const val MAX_SHORT_FIELD = 200
/** Synopsis textarea max length (REQ-edit-screen-4). */
private const val MAX_SYNOPSIS = 2000
/** Common languages offered by the SELECT dropdown (REQ-edit-screen-5). */
private val LANGUAGE_OPTIONS = listOf(
    "en", "es", "fr", "de", "it", "pt", "nl", "ru", "zh", "ja", "ko",
    "ar", "pl", "sv", "tr", "el", "da", "fi", "no", "cs", "hu", "ro", "uk", "hi"
)

/**
 * Full-screen edit-metadata editor (design A9ymv, route `book_edit/{bookId}`).
 * Loads the book, exposes the form fields with counters, the cover preview
 * (raw coverPath string — Coil resolves absolute paths AND cloud URLs, fixing
 * the blank-cover bug), the read-only Size/Format rows, a DatePicker for the
 * publication date, a language SELECT, and editable genre/tag chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookMetadataScreen(
    contentPadding: PaddingValues,
    bookId: String,
    libraryRepository: LibraryRepository,
    coverStorage: CoverStorage,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EditBookMetadataViewModel = viewModel(
        factory = EditBookMetadataViewModel.Factory(
            bookId = bookId,
            libraryRepository = libraryRepository,
            coverStorage = coverStorage,
            appContext = context.applicationContext,
            onSaved = onNavigateBack
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            if (event is UiEvent.ShowSnackbar) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) viewModel.onCoverSelected(uri, bytes)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_metadata_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
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
                    NextPageButton(
                        onClick = viewModel::save,
                        enabled = state.book != null && !state.isSaving,
                        variant = NextPageButtonVariant.ICON
                    ) {
                        Icon(
                            imageVector = NextPageIcons.Check,
                            contentDescription = stringResource(R.string.edit_metadata_save_changes)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                NextPageButton(
                    onClick = viewModel::save,
                    enabled = state.book != null && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.edit_metadata_save_changes))
                }
            }
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                    EditMetadataContent(
                        state = state,
                        onChangeCover = { coverPickerLauncher.launch("image/*") },
                        onTitleChange = { viewModel.onTitleChange(it.take(MAX_SHORT_FIELD)) },
                        onAuthorChange = { viewModel.onAuthorChange(it.take(MAX_SHORT_FIELD)) },
                        onDescriptionChange = { viewModel.onDescriptionChange(it.take(MAX_SYNOPSIS)) },
                        onPublisherChange = { viewModel.onPublisherChange(it.take(MAX_SHORT_FIELD)) },
                        onLanguageChange = viewModel::onLanguageChange,
                        onPublishedDateChange = viewModel::onPublishedDateChange,
                        onGenreAdd = viewModel::onGenreAdd,
                        onGenreRemove = viewModel::onGenreRemove,
                        onTagAdd = viewModel::onTagAdd,
                        onTagRemove = viewModel::onTagRemove
                    )
                }
            }
        }
    }
}

@Composable
private fun EditMetadataContent(
    state: EditBookMetadataUiState,
    onChangeCover: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPublisherChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onPublishedDateChange: (String?) -> Unit,
    onGenreAdd: (String) -> Unit,
    onGenreRemove: (String) -> Unit,
    onTagAdd: (String) -> Unit,
    onTagRemove: (String) -> Unit
) {
    val book = state.book ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CoverSection(
            book = book,
            coverUri = state.coverUri,
            onChangeCover = onChangeCover
        )

        // ── Basic fields (REQ-edit-screen-4) ──────────────────────────
        FormField(
            label = stringResource(R.string.edit_metadata_field_title),
            value = state.title,
            onValueChange = onTitleChange,
            counter = stringResource(R.string.edit_metadata_counter, state.title.length, MAX_SHORT_FIELD),
            singleLine = true
        )
        FormField(
            label = stringResource(R.string.edit_metadata_field_author),
            value = state.author,
            onValueChange = onAuthorChange,
            counter = stringResource(R.string.edit_metadata_counter, state.author.length, MAX_SHORT_FIELD),
            singleLine = true
        )
        FormField(
            label = stringResource(R.string.edit_metadata_field_synopsis),
            value = state.description,
            onValueChange = onDescriptionChange,
            counter = stringResource(R.string.edit_metadata_counter, state.description.length, MAX_SYNOPSIS),
            minLines = 4
        )

        // ── Details section (REQ-edit-screen-5) ───────────────────────
        SectionHeader(stringResource(R.string.edit_metadata_section_details))

        var showDatePicker by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.edit_metadata_field_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { showDatePicker = true }) {
                Text(
                    text = formatPublishedDate(state.publishedDate),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = isoToEpochMillis(state.publishedDate)
                    ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                onPublishedDateChange(epochMillisToIso(millis))
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text(text = stringResource(R.string.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        LanguageDropdown(selectedCode = state.language, onSelect = onLanguageChange)

        FormField(
            label = stringResource(R.string.edit_metadata_field_publisher),
            value = state.publisher,
            onValueChange = onPublisherChange,
            counter = stringResource(R.string.edit_metadata_counter, state.publisher.length, MAX_SHORT_FIELD),
            singleLine = true
        )

        ReadOnlyDetailRow(
            label = stringResource(R.string.edit_metadata_field_size),
            value = remember(book.filePath) { formatSizeMb(book.filePath) }
        )
        ReadOnlyDetailRow(
            label = stringResource(R.string.edit_metadata_field_format),
            value = book.format.uppercase()
        )

        // ── Genres & tags (REQ-edit-screen-6/7) ───────────────────────
        SectionHeader(stringResource(R.string.edit_metadata_genres))
        GenreChips(
            genres = state.genres,
            max = 5,
            onAdd = onGenreAdd,
            onRemove = onGenreRemove,
            modifier = Modifier.fillMaxWidth()
        )

        SectionHeader(stringResource(R.string.edit_metadata_tags))
        TagChips(
            tags = state.tags,
            max = 10,
            onAdd = onTagAdd,
            onRemove = onTagRemove,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Cover preview + "Change cover" (REQ-edit-screen-3). The request is keyed on
 * the RAW coverPath string (no `File(...)` wrapper) so Coil resolves both
 * absolute local paths and cloud URLs — the blank-cover fix (design D8).
 */
@Composable
private fun CoverSection(
    book: Book,
    coverUri: Uri?,
    onChangeCover: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverRequest = remember(context, density, coverUri, book.coverPath) {
        ImageRequest.Builder(context)
            .data(coverUri ?: book.coverPath?.takeIf { it.isNotBlank() })
            .size(
                width = with(density) { 128.dp.toPx().toInt() },
                height = with(density) { 192.dp.toPx().toInt() }
            )
            .placeholder(R.drawable.cover_placeholder)
            .error(R.drawable.cover_error)
            .fallback(R.drawable.cover_placeholder)
            .crossfade(true)
            .build()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(128.dp, 192.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = coverRequest,
                contentDescription = stringResource(R.string.library_cover_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        NextPageButton(
            text = stringResource(R.string.edit_metadata_change_cover),
            onClick = onChangeCover,
            variant = NextPageButtonVariant.OUTLINED
        )
    }
}

/** Outlined field with a small fs12 label and an fs10 n/max counter below. */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    counter: String,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, fontSize = MaterialTheme.typography.labelMedium.fontSize) },
        singleLine = singleLine,
        minLines = minLines,
        supportingText = {
            Text(
                text = counter,
                style = MaterialTheme.typography.labelSmall,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** Read-only label/value row for Size and Format (REQ-edit-screen-5). */
@Composable
private fun ReadOnlyDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Language SELECT (REQ-edit-screen-5); display names localized by the OS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedCode: String?,
    onSelect: (String) -> Unit
) {
    val options = remember(selectedCode) {
        if (selectedCode != null && LANGUAGE_OPTIONS.none { it.equals(selectedCode, ignoreCase = true) }) {
            listOf(selectedCode) + LANGUAGE_OPTIONS
        } else {
            LANGUAGE_OPTIONS
        }
    }
    var expanded by remember { mutableStateOf(false) }
    val displayName = selectedCode?.let { displayLanguageName(it) }.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.edit_metadata_field_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(text = displayLanguageName(code)) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun displayLanguageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())

/** Formats the ISO `yyyy-MM-dd` date for display; "—" when unset. */
private fun formatPublishedDate(iso: String?): String =
    if (iso.isNullOrBlank()) "—" else iso

private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0

/** File size in MB computed from the book file; "—" when the file is missing. */
private fun formatSizeMb(filePath: String): String {
    val bytes = runCatching { File(filePath).length() }.getOrNull()
        ?: return "—"
    if (bytes <= 0L) return "—"
    val mb = bytes / BYTES_PER_MEGABYTE
    return if (mb >= 100) {
        "${mb.toInt()} MB"
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun isoToEpochMillis(iso: String?): Long? = try {
    iso?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
} catch (_: Exception) {
    null
}

private fun epochMillisToIso(millis: Long?): String? = millis?.let {
    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
}

// ─── Previews ─────────────────────────────────────────────────────────

private val PreviewBook = Book(
    id = "preview-book-1",
    title = "The Hobbit",
    author = "J.R.R. Tolkien",
    coverPath = null,
    filePath = "/preview/the-hobbit.epub",
    format = "epub",
    description = "A journey into Middle-earth.",
    updatedAtEpochMillis = 0L,
    genre = "Fantasy, Classics",
    language = "en",
    publisher = "George Allen & Unwin",
    tags = "favorites, adventure",
    publishedDate = "1937-09-21"
)

@Preview(showBackground = true)
@Composable
private fun EditBookMetadataScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        EditMetadataContent(
            state = EditBookMetadataUiState(
                book = PreviewBook,
                isLoading = false,
                title = PreviewBook.title,
                author = PreviewBook.author.orEmpty(),
                description = PreviewBook.description.orEmpty(),
                genres = listOf("Fantasy", "Classics"),
                tags = listOf("favorites", "adventure"),
                language = PreviewBook.language,
                publisher = PreviewBook.publisher.orEmpty(),
                publishedDate = PreviewBook.publishedDate
            ),
            onChangeCover = {},
            onTitleChange = {},
            onAuthorChange = {},
            onDescriptionChange = {},
            onPublisherChange = {},
            onLanguageChange = {},
            onPublishedDateChange = {},
            onGenreAdd = {},
            onGenreRemove = {},
            onTagAdd = {},
            onTagRemove = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditBookMetadataScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        EditMetadataContent(
            state = EditBookMetadataUiState(
                book = PreviewBook,
                isLoading = false,
                title = PreviewBook.title,
                author = PreviewBook.author.orEmpty(),
                description = PreviewBook.description.orEmpty(),
                genres = listOf("Fantasy"),
                tags = listOf("favorites"),
                language = PreviewBook.language,
                publisher = PreviewBook.publisher.orEmpty(),
                publishedDate = PreviewBook.publishedDate
            ),
            onChangeCover = {},
            onTitleChange = {},
            onAuthorChange = {},
            onDescriptionChange = {},
            onPublisherChange = {},
            onLanguageChange = {},
            onPublishedDateChange = {},
            onGenreAdd = {},
            onGenreRemove = {},
            onTagAdd = {},
            onTagRemove = {}
        )
    }
}
