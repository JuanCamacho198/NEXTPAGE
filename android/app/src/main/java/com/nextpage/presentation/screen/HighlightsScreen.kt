package com.nextpage.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.nextpage.R
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.presentation.theme.NextPageColors
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.HighlightsUiState
import com.nextpage.presentation.viewmodel.HighlightsViewModel
import com.nextpage.ui.icons.NextPageIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageEmptyState
import com.nextpage.ui.components.atoms.NextPageTextField
import com.nextpage.ui.components.molecules.FilterTab
import com.nextpage.ui.components.molecules.HighlightAnnotationModal
import com.nextpage.ui.components.molecules.NextPageFilterTabs
import com.nextpage.ui.components.molecules.NextPageHeader
import com.nextpage.ui.components.molecules.NextPageHighlightCard
import com.nextpage.ui.components.molecules.NextPageSectionHeader
import com.nextpage.ui.components.molecules.NextPageSelector
import com.nextpage.ui.components.molecules.SelectorOption

@Composable
fun HighlightsScreen(
    contentPadding: PaddingValues,
    viewModel: HighlightsViewModel,
    authSession: AuthSession? = null,
    onOpenAccount: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    // Option 1: silent pull in background when entering screen — local Flow shows instantly
    LaunchedEffect(Unit) {
        viewModel.syncHighlights()
    }
    HighlightsScreenContent(
        uiState = uiState,
        contentPadding = contentPadding,
        authSession = authSession,
        onOpenAccount = onOpenAccount,
        syncState = syncState,
        onSyncRefresh = { viewModel.syncHighlights(force = true) },
        onBookFilterChanged = viewModel::onBookFilterChanged,
        onTagFilterChanged = viewModel::onTagFilterChanged,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onTypeFilterChanged = viewModel::onTypeFilterChanged,
        onColorFilterChanged = viewModel::onColorFilterChanged,
        onColorFilterReset = viewModel::onColorFilterReset,
        onCopyHighlight = viewModel::onCopyHighlight,
        onEditHighlightNote = viewModel::onEditHighlightNote,
        onChangeHighlightColor = viewModel::onChangeHighlightColor,
        onViewInBook = viewModel::onViewInBook,
        onAddHighlightTag = viewModel::onAddHighlightTag,
        onDeleteHighlight = viewModel::onDeleteHighlight,
        onSaveHighlightNote = viewModel::onSaveHighlightNote,
        onDismissEditHighlight = viewModel::dismissEditHighlight,
        onDismissDeleteHighlightDialog = viewModel::dismissDeleteHighlightDialog,
        onConfirmDeleteHighlight = viewModel::confirmDeleteHighlight,
        onDismissColorPicker = viewModel::dismissColorPicker,
        onConfirmColorChange = viewModel::onConfirmColorChange,
        onDismissTagEdit = viewModel::dismissTagEdit,
        onTagEditTextChanged = viewModel::onTagEditTextChanged,
        onSaveHighlightTag = viewModel::onSaveHighlightTag
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightsScreenContent(
    uiState: HighlightsUiState,
    contentPadding: PaddingValues,
    authSession: AuthSession? = null,
    onOpenAccount: () -> Unit = {},
    syncState: com.nextpage.presentation.viewmodel.HighlightsSyncState = com.nextpage.presentation.viewmodel.HighlightsSyncState.Idle,
    onSyncRefresh: () -> Unit = {},
    onBookFilterChanged: (String?) -> Unit,
    onTagFilterChanged: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTypeFilterChanged: (String) -> Unit,
    onColorFilterChanged: (String) -> Unit,
    onColorFilterReset: () -> Unit,
    onCopyHighlight: (Highlight) -> Unit,
    onEditHighlightNote: (Highlight) -> Unit,
    onChangeHighlightColor: (Highlight) -> Unit,
    onViewInBook: (Highlight) -> Unit,
    onAddHighlightTag: (Highlight) -> Unit,
    onDeleteHighlight: (Highlight) -> Unit,
    onSaveHighlightNote: (String) -> Unit,
    onDismissEditHighlight: () -> Unit,
    onDismissDeleteHighlightDialog: () -> Unit,
    onConfirmDeleteHighlight: () -> Unit,
    onDismissColorPicker: () -> Unit,
    onConfirmColorChange: (String) -> Unit,
    onDismissTagEdit: () -> Unit,
    onTagEditTextChanged: (String) -> Unit,
    onSaveHighlightTag: (String) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var showBookSelector by remember { mutableStateOf(false) }

    var showTagSelector by remember { mutableStateOf(false) }

    val typeTabs = listOf(
        FilterTab("all", R.string.highlights_tab_all, NextPageIcons.Sparkle),
        FilterTab("quotes", R.string.highlights_tab_quotes, NextPageIcons.Quote),
        FilterTab("ideas", R.string.highlights_tab_ideas, NextPageIcons.Lightbulb),
        FilterTab("passages", R.string.highlights_tab_passages, NextPageIcons.Sparkle)
    )

    val bookOptions = remember(uiState.books) {
        listOf(
            SelectorOption("all", R.string.highlights_filter_all_books, icon = NextPageIcons.Book)
        ) + uiState.books.map { SelectorOption(it.id, labelRes = null, label = it.title) }
    }

    if (showBookSelector) {
        NextPageSelector(
            title = stringResource(R.string.highlights_filter_book),
            options = bookOptions,
            selectedOptionId = uiState.bookFilter ?: "all",
            onOptionSelected = { option ->
                onBookFilterChanged(if (option.id == "all") null else option.id)
                showBookSelector = false
            },
            onDismiss = { showBookSelector = false }
        )
    }

    val tagOptions = remember(uiState.availableTags) {
        listOf(
            SelectorOption("all", R.string.highlights_filter_all_tags)
        ) + uiState.availableTags.map { SelectorOption(it, labelRes = null, label = it) }
    }

    if (showTagSelector) {
        NextPageSelector(
            title = stringResource(R.string.highlights_filter_tag),
            options = tagOptions,
            selectedOptionId = uiState.tagFilter ?: "all",
            onOptionSelected = { option ->
                onTagFilterChanged(if (option.id == "all") null else option.id)
                showTagSelector = false
            },
            onDismiss = { showTagSelector = false }
        )
    }

    val filterBookTitle = remember(uiState.books, uiState.bookFilter) {
        uiState.bookFilter?.let { bookId ->
            uiState.books.find { it.id == bookId }?.title
        }
    }
    val bookMap = remember(uiState.books) {
        uiState.books.associate { it.id to it.title }
    }

    val isSyncing = syncState is com.nextpage.presentation.viewmodel.HighlightsSyncState.Syncing
    val isSynced = syncState is com.nextpage.presentation.viewmodel.HighlightsSyncState.Synced
    val pullState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = onSyncRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
            ) {
                item {
                    NextPageHeader(
                        title = stringResource(R.string.home_nextpage_title),
                        avatarImageUrl = authSession?.photoUrl,
                        avatarInitials = authSession?.displayName?.take(2)?.uppercase() ?: "NP",
                        onAvatarClick = onOpenAccount,
                        avatarContentDescription = stringResource(R.string.home_avatar_content_description),
                        onSearchClick = { showSearch = !showSearch }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.highlights_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.highlights_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Circular ↻ refresh button only on Highlights screen
                        IconButton(
                            onClick = onSyncRefresh,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.highlights_refresh_content_description),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Subtle sync indicator chip (Sincronizando... / Sincronizado)
                if (isSyncing || isSynced) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = stringResource(R.string.highlights_syncing),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    } else {
                                        Icon(
                                            imageVector = NextPageIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(R.string.highlights_synced),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

        if (showSearch) {
            item {
                NextPageTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.highlights_search),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = NextPageIcons.Close,
                    trailingIconContentDescription = stringResource(R.string.reader_settings_close)
                )
            }
        }

        item {
            NextPageFilterTabs(
                tabs = typeTabs,
                selectedTabId = uiState.typeFilter,
                onTabSelected = onTypeFilterChanged
            )
        }

        item {
            FilterControlsRow(
                bookFilterTitle = filterBookTitle,
                tagFilter = uiState.tagFilter,
                onBookFilterClick = { showBookSelector = true },
                onTagFilterClick = { showTagSelector = true }
            )
        }

        item {
            ColorSwatchRow(
                selectedColors = uiState.colorFilter,
                highlightColors = HighlightColor.entries,
                onColorToggled = onColorFilterChanged,
                onTodosSelected = onColorFilterReset
            )
        }

        item {
            NextPageSectionHeader(
                title = stringResource(R.string.highlights_recent),
                actionLabel = stringResource(R.string.home_ver_todo),
                onActionClick = { }
            )
        }

        if (uiState.filteredHighlights.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NextPageEmptyState(
                        icon = NextPageIcons.Quote,
                        title = stringResource(R.string.highlights_empty),
                        subtitle = stringResource(R.string.highlights_empty_subtitle)
                    )
                }
            }
        } else {
            items(uiState.filteredHighlights, key = { it.id }) { highlight ->
                NextPageHighlightCard(
                    content = stripSurroundingQuotes(
                        highlight.textContent.replace("\\n", " ").replace("\n", " ")
                    ),
                    accentColor = parseHighlightColor(highlight.color),
                    note = highlight.note,
                    tag = highlight.tag,
                    attribution = bookMap[highlight.bookId],
                    onCopyText = { onCopyHighlight(highlight) },
                    onEditNote = { onEditHighlightNote(highlight) },
                    onChangeColor = { onChangeHighlightColor(highlight) },
                    onViewInBook = { onViewInBook(highlight) },
                    onAddTag = { onAddHighlightTag(highlight) },
                    onDelete = { onDeleteHighlight(highlight) },
                    onTagClick = { tag -> onTagFilterChanged(tag) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // ── Note Edit Modal ────────────────────────────────────────
    uiState.highlightToEdit?.let { highlight ->
        HighlightAnnotationModal(
            titleRes = R.string.note_modal_title,
            hintRes = R.string.annotation_textarea_note_hint,
            snippetLabelRes = R.string.annotation_snippet_label,
                    selectedText = highlight.textContent.replace("\\n", " ").replace("\n", " "),
            initialText = uiState.editNoteText,
            onSave = onSaveHighlightNote,
            onDismiss = onDismissEditHighlight
        )
    }

    // ── Delete Confirmation Dialog ─────────────────────────────
    uiState.highlightToDelete?.let { highlight ->
        AlertDialog(
            onDismissRequest = onDismissDeleteHighlightDialog,
            title = { Text(text = stringResource(R.string.highlights_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.highlights_delete_message,
                        highlight.textContent.replace("\\n", " ").replace("\n", " ").take(60)
                    )
                )
            },
            confirmButton = {
                NextPageButton(
                    onClick = onConfirmDeleteHighlight,
                    variant = NextPageButtonVariant.TEXT
                ) {
                    Text(text = stringResource(R.string.highlights_delete_confirm))
                }
            },
            dismissButton = {
                NextPageButton(
                    onClick = onDismissDeleteHighlightDialog,
                    variant = NextPageButtonVariant.TEXT
                ) {
                    Text(text = stringResource(R.string.reader_cancel))
                }
            }
        )
    }

    // ── Change Color Dialog ──────────────────────────────────────
    uiState.selectedHighlightForColorChange?.let { highlight ->
        AlertDialog(
            onDismissRequest = onDismissColorPicker,
            title = { Text(stringResource(R.string.highlights_menu_change_color)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (color in HighlightColor.entries) {
                        val isActive = color.hex.equals(highlight.color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(parseHighlightColor(color.hex))
                                .clickable { onConfirmColorChange(color.hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .border(2.dp, Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissColorPicker) {
                    Text(stringResource(R.string.reader_cancel))
                }
            }
        )
    }

    // ── Tag Edit Dialog ──────────────────────────────────────────
    uiState.selectedHighlightForTagEdit?.let { _ ->
        AlertDialog(
            onDismissRequest = onDismissTagEdit,
            title = {
                Text(
                    stringResource(
                        if (uiState.editTagText.isBlank()) R.string.highlights_menu_add_tag
                        else R.string.highlights_menu_edit_tag
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = uiState.editTagText,
                    onValueChange = onTagEditTextChanged,
                    placeholder = { Text(stringResource(R.string.highlights_tag_dialog_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onSaveHighlightTag(uiState.editTagText) }) {
                    Text(stringResource(R.string.reader_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissTagEdit) {
                    Text(stringResource(R.string.reader_cancel))
                }
            }
        )
    }
}

@Composable
private fun FilterControlsRow(
    bookFilterTitle: String?,
    tagFilter: String?,
    onBookFilterClick: () -> Unit,
    onTagFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = bookFilterTitle != null,
            onClick = onBookFilterClick,
            label = { Text(bookFilterTitle ?: stringResource(R.string.highlights_filter_book)) },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = tagFilter != null,
            onClick = onTagFilterClick,
            label = { Text(tagFilter ?: stringResource(R.string.highlights_filter_tag)) },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Parses a hex color string into a [Color].
 *
 * Accepts 6-digit RGB (`RRGGBB`, rendered opaque) and 8-digit ARGB
 * (`AARRGGBB`, rendered as-is). Anything else (invalid, non-hex, or a
 * transparent-alpha value) resolves to `null`, so the caller can fall
 * back to a theme-aware color that is always visible.
 */
internal fun resolveHighlightColorHex(hex: String): Color? {
    val s = hex.trim().removePrefix("#")
    return when (s.length) {
        6 -> runCatching { Color(("FF" + s).toLong(16)) }.getOrNull()
        8 -> runCatching { Color(s.toLong(16)) }.getOrNull()
        else -> null
    }.takeIf { it?.alpha != 0f }
}

@Composable
private fun parseHighlightColor(hex: String): Color {
    return resolveHighlightColorHex(hex) ?: MaterialTheme.colorScheme.primary
}

@Composable
private fun ColorSwatchRow(
    selectedColors: Set<String>,
    highlightColors: List<HighlightColor>,
    onColorToggled: (String) -> Unit,
    onTodosSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        // "Todos" — dashed stroked circle
        item {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onTodosSelected() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                    )
                    drawCircle(
                        color = Color.Gray,
                        radius = size.minDimension / 2f - 2.dp.toPx() / 2f,
                        style = stroke
                    )
                }
            }
        }

        // Color swatches
        items(highlightColors, key = { it.hex }) { highlightColor ->
            val isSelected = highlightColor.hex in selectedColors
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(parseHighlightColor(highlightColor.hex))
                    .clickable { onColorToggled(highlightColor.hex) }
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, Color.White, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = NextPageIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    }
}

private fun stripSurroundingQuotes(text: String): String {
    return text.removeSurrounding("\"").removeSurrounding("'")
}

@Preview(showBackground = true)
@Composable
private fun HighlightsScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        val sampleBook = Book(
            id = "book-1",
            title = "Deep Work",
            author = "Cal Newport",
            coverPath = null,
            filePath = "/books/deep-work.epub",
            format = "epub",
            updatedAtEpochMillis = 1L
        )
        val sampleHighlight = Highlight(
            id = "hl-1",
            bookId = "book-1",
            cfiRange = "epubcfi(/6/14)",
            textContent = "Deep work is the ability to focus without distraction on a cognitively demanding task.",
            note = "Core definition.",
            color = HighlightColor.YELLOW.hex,
            updatedAtEpochMillis = 1L,
            deletedAtEpochMillis = null,
            tag = "focus",
            type = "quote"
        )
        HighlightsScreenContent(
            uiState = HighlightsUiState(
                highlights = listOf(sampleHighlight),
                bookmarks = listOf(
                    Bookmark(
                        id = "bm-1",
                        bookId = "book-1",
                        cfiLocation = "epubcfi(/6/20)",
                        titleOrSnippet = "Deep work",
                        updatedAtEpochMillis = 1L,
                        deletedAtEpochMillis = null
                    )
                ),
                books = listOf(sampleBook),
                typeFilter = "all",
                bookFilter = null,
                colorFilter = emptySet(),
                tagFilter = null,
                searchQuery = "",
                filteredHighlights = listOf(sampleHighlight),
                availableTags = listOf("focus"),
                isLoading = false,
                errorMessage = null,
                highlightToEdit = null,
                highlightToDelete = null,
                editNoteText = "",
                colorCounts = mapOf(HighlightColor.YELLOW.hex to 1),
                selectedHighlightForColorChange = null,
                selectedHighlightForTagEdit = null,
                editTagText = ""
            ),
            contentPadding = PaddingValues(16.dp),
            onBookFilterChanged = {},
            onTagFilterChanged = {},
            onSearchQueryChange = {},
            onTypeFilterChanged = {},
            onColorFilterChanged = {},
            onColorFilterReset = {},
            onCopyHighlight = {},
            onEditHighlightNote = {},
            onChangeHighlightColor = {},
            onViewInBook = {},
            onAddHighlightTag = {},
            onDeleteHighlight = {},
            onSaveHighlightNote = {},
            onDismissEditHighlight = {},
            onDismissDeleteHighlightDialog = {},
            onConfirmDeleteHighlight = {},
            onDismissColorPicker = {},
            onConfirmColorChange = {},
            onDismissTagEdit = {},
            onTagEditTextChanged = {},
            onSaveHighlightTag = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HighlightsScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        val sampleBook = Book(
            id = "book-1",
            title = "Deep Work",
            author = "Cal Newport",
            coverPath = null,
            filePath = "/books/deep-work.epub",
            format = "epub",
            updatedAtEpochMillis = 1L
        )
        val sampleHighlight = Highlight(
            id = "hl-1",
            bookId = "book-1",
            cfiRange = "epubcfi(/6/14)",
            textContent = "Deep work is the ability to focus without distraction on a cognitively demanding task.",
            note = "Core definition.",
            color = HighlightColor.YELLOW.hex,
            updatedAtEpochMillis = 1L,
            deletedAtEpochMillis = null,
            tag = "focus",
            type = "quote"
        )
        HighlightsScreenContent(
            uiState = HighlightsUiState(
                highlights = listOf(sampleHighlight),
                bookmarks = listOf(
                    Bookmark(
                        id = "bm-1",
                        bookId = "book-1",
                        cfiLocation = "epubcfi(/6/20)",
                        titleOrSnippet = "Deep work",
                        updatedAtEpochMillis = 1L,
                        deletedAtEpochMillis = null
                    )
                ),
                books = listOf(sampleBook),
                typeFilter = "all",
                bookFilter = null,
                colorFilter = emptySet(),
                tagFilter = null,
                searchQuery = "",
                filteredHighlights = listOf(sampleHighlight),
                availableTags = listOf("focus"),
                isLoading = false,
                errorMessage = null,
                highlightToEdit = null,
                highlightToDelete = null,
                editNoteText = "",
                colorCounts = mapOf(HighlightColor.YELLOW.hex to 1),
                selectedHighlightForColorChange = null,
                selectedHighlightForTagEdit = null,
                editTagText = ""
            ),
            contentPadding = PaddingValues(16.dp),
            onBookFilterChanged = {},
            onTagFilterChanged = {},
            onSearchQueryChange = {},
            onTypeFilterChanged = {},
            onColorFilterChanged = {},
            onColorFilterReset = {},
            onCopyHighlight = {},
            onEditHighlightNote = {},
            onChangeHighlightColor = {},
            onViewInBook = {},
            onAddHighlightTag = {},
            onDeleteHighlight = {},
            onSaveHighlightNote = {},
            onDismissEditHighlight = {},
            onDismissDeleteHighlightDialog = {},
            onConfirmDeleteHighlight = {},
            onDismissColorPicker = {},
            onConfirmColorChange = {},
            onDismissTagEdit = {},
            onTagEditTextChanged = {},
            onSaveHighlightTag = {}
        )
    }
}
