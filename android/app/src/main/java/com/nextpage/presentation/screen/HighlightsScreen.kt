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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.presentation.theme.NextPageColors
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.HighlightsViewModel
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

private val ColorQuotes = NextPageColors.accentBlue
private val ColorIdeas = NextPageColors.accentPurple
private val ColorPassages = NextPageColors.accentGreen
private val ColorFavorites = NextPageColors.accentYellow

@Composable
fun HighlightsScreen(
    contentPadding: PaddingValues,
    viewModel: HighlightsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showBookSelector by remember { mutableStateOf(false) }
    var showColorSelector by remember { mutableStateOf(false) }
    var showTagSelector by remember { mutableStateOf(false) }

    val typeTabs = listOf(
        FilterTab("all", R.string.highlights_tab_all, Icons.Outlined.AutoAwesome),
        FilterTab("quotes", R.string.highlights_tab_quotes, Icons.Outlined.FormatQuote),
        FilterTab("ideas", R.string.highlights_tab_ideas, Icons.Outlined.Lightbulb),
        FilterTab("passages", R.string.highlights_tab_passages, Icons.Outlined.AutoAwesome)
    )

    val bookOptions = listOf(
        SelectorOption("all", R.string.highlights_filter_all_books, icon = Icons.Outlined.Book)
    ) + uiState.books.map { SelectorOption(it.id, labelRes = null, label = it.title) }

    val colorOptions = listOf(
        SelectorOption("all", R.string.highlights_filter_all_colors, icon = Icons.Outlined.Palette)
    ) + HighlightColor.entries.map {
        SelectorOption(it.hex, labelRes = null, label = it.name.lowercase().replaceFirstChar { c -> c.uppercase() })
    }

    if (showBookSelector) {
        NextPageSelector(
            title = stringResource(R.string.highlights_filter_book),
            options = bookOptions,
            selectedOptionId = uiState.bookFilter ?: "all",
            onOptionSelected = { option ->
                viewModel.onBookFilterChanged(if (option.id == "all") null else option.id)
                showBookSelector = false
            },
            onDismiss = { showBookSelector = false }
        )
    }

    if (showColorSelector) {
        NextPageSelector(
            title = stringResource(R.string.highlights_filter_color),
            options = colorOptions,
            selectedOptionId = uiState.colorFilter ?: "all",
            onOptionSelected = { option ->
                viewModel.onColorFilterChanged(if (option.id == "all") null else option.id)
                showColorSelector = false
            },
            onDismiss = { showColorSelector = false }
        )
    }

    val tagOptions = listOf(
        SelectorOption("all", R.string.highlights_filter_all_tags)
    ) + uiState.availableTags.map { SelectorOption(it, labelRes = null, label = it) }

    if (showTagSelector) {
        NextPageSelector(
            title = stringResource(R.string.highlights_filter_tag),
            options = tagOptions,
            selectedOptionId = uiState.tagFilter ?: "all",
            onOptionSelected = { option ->
                viewModel.onTagFilterChanged(if (option.id == "all") null else option.id)
                showTagSelector = false
            },
            onDismiss = { showTagSelector = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
    ) {
        item {
            NextPageHeader(
                title = stringResource(R.string.home_nextpage_title),
                avatarInitials = stringResource(R.string.app_logo_initials),
                onSearchClick = { showSearch = !showSearch }
            )
        }

        item {
            Column {
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
        }

        if (showSearch) {
            item {
                NextPageTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = stringResource(R.string.highlights_search),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = Icons.Outlined.Close,
                    trailingIconContentDescription = stringResource(R.string.reader_settings_close)
                )
            }
        }

        item {
            NextPageFilterTabs(
                tabs = typeTabs,
                selectedTabId = uiState.typeFilter,
                onTabSelected = { viewModel.onTypeFilterChanged(it) }
            )
        }

        item {
            FilterControlsRow(
                bookFilterTitle = uiState.bookFilter?.let { bookId ->
                    uiState.books.find { it.id == bookId }?.title
                },
                colorFilter = uiState.colorFilter,
                tagFilter = uiState.tagFilter,
                onBookFilterClick = { showBookSelector = true },
                onColorFilterClick = { showColorSelector = true },
                onTagFilterClick = { showTagSelector = true }
            )
        }

        item {
            NextPageSectionHeader(
                title = stringResource(R.string.highlights_recent),
                actionLabel = stringResource(R.string.home_ver_todo),
                onActionClick = { }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatWidget(
                    value = "${uiState.highlights.count { it.type == "quote" }}",
                    label = stringResource(R.string.highlights_summary_quotes),
                    color = ColorQuotes
                )
                StatWidget(
                    value = "${uiState.highlights.count { it.type == "idea" }}",
                    label = stringResource(R.string.highlights_summary_ideas),
                    color = ColorIdeas
                )
                StatWidget(
                    value = "${uiState.highlights.count { it.type == "passage" }}",
                    label = stringResource(R.string.highlights_summary_passages),
                    color = ColorPassages
                )
                StatWidget(
                    value = "${uiState.bookmarks.size}",
                    label = stringResource(R.string.highlights_summary_favorites),
                    color = ColorFavorites
                )
            }
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
                        icon = Icons.Outlined.FormatQuote,
                        title = stringResource(R.string.highlights_empty),
                        subtitle = stringResource(R.string.highlights_empty_subtitle)
                    )
                }
            }
        } else {
            items(uiState.filteredHighlights, key = { it.id }) { highlight ->
                NextPageHighlightCard(
                    content = highlight.textContent.replace("\\n", " ").replace("\n", " "),
                    accentColor = parseHighlightColor(highlight.color),
                    note = highlight.note,
                    tag = highlight.tag,
                    colorLabel = HighlightColor.fromHex(highlight.color)?.name?.lowercase()
                        ?.replaceFirstChar { c -> c.uppercase() },
                    onEditNote = { viewModel.onEditHighlightNote(highlight) },
                    onDelete = { viewModel.onDeleteHighlight(highlight) },
                    onTagClick = { tag -> viewModel.onTagFilterChanged(tag) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // ── Note Edit Modal ────────────────────────────────────────
    uiState.highlightToEdit?.let { highlight ->
        HighlightAnnotationModal(
            titleRes = R.string.note_modal_title,
            hintRes = R.string.annotation_textarea_note_hint,
            snippetLabelRes = R.string.annotation_snippet_label,
                    selectedText = highlight.textContent.replace("\\n", " ").replace("\n", " "),
            initialText = uiState.editNoteText,
            onSave = { viewModel.onSaveHighlightNote(it) },
            onDismiss = { viewModel.dismissEditHighlight() }
        )
    }

    // ── Delete Confirmation Dialog ─────────────────────────────
    uiState.highlightToDelete?.let { highlight ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteHighlightDialog() },
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
                    onClick = { viewModel.confirmDeleteHighlight() },
                    variant = NextPageButtonVariant.TEXT
                ) {
                    Text(text = stringResource(R.string.highlights_delete_confirm))
                }
            },
            dismissButton = {
                NextPageButton(
                    onClick = { viewModel.dismissDeleteHighlightDialog() },
                    variant = NextPageButtonVariant.TEXT
                ) {
                    Text(text = stringResource(R.string.reader_cancel))
                }
            }
        )
    }
}

@Composable
private fun FilterControlsRow(
    bookFilterTitle: String?,
    colorFilter: String?,
    tagFilter: String?,
    onBookFilterClick: () -> Unit,
    onColorFilterClick: () -> Unit,
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
            selected = colorFilter != null,
            onClick = onColorFilterClick,
            label = {
                val label = colorFilter?.let { hex ->
                    HighlightColor.entries.find { it.hex.equals(hex, ignoreCase = true) }?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                } ?: stringResource(R.string.highlights_filter_color)
                Text(label)
            },
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

@Composable
private fun StatWidget(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun parseHighlightColor(hex: String): Color {
    return try {
        val sanitized = hex.removePrefix("#").trim()
        val longHex = when (sanitized.length) {
            6 -> "FF$sanitized"
            8 -> sanitized
            else -> "FF000000"
        }
        Color(longHex.toLong(16))
    } catch (_: Exception) {
        Color.Gray
    }
}
