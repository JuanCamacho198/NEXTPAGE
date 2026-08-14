package com.nextpage.presentation.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.presentation.viewmodel.DictionaryViewModel
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageEmptyState
import com.nextpage.ui.components.atoms.NextPageTextField
import com.nextpage.ui.icons.NextPageIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = NextPageIcons.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dictionary_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.dictionary_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            NextPageTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = stringResource(R.string.dictionary_search_placeholder),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.dictionary_word_count, uiState.filteredWords.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.filteredWords.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    NextPageEmptyState(
                        icon = NextPageIcons.LibraryBooks,
                        title = stringResource(R.string.dictionary_empty),
                        subtitle = stringResource(R.string.dictionary_empty_subtitle)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.filteredWords, key = { it.id }) { word ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onRequestEditWord(word) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = word.word,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!word.definition.isNullOrBlank()) {
                                        Text(
                                            text = word.definition,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.dictionary_tap_to_add_definition),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                    Text(
                                        text = formatDate(word.addedAtEpochMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.onRequestDeleteWord(word) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = NextPageIcons.Trash,
                                        contentDescription = stringResource(R.string.dictionary_delete_confirm),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── FAB to add word ─────────────────────────────────────
        FloatingActionButton(
            onClick = { viewModel.onShowAddDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = NextPageIcons.Add,
                contentDescription = stringResource(R.string.dictionary_add_word),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // ── Add Word Dialog ─────────────────────────────────────
        if (uiState.showAddDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onDismissAddDialog() },
                title = { Text(text = stringResource(R.string.dictionary_add_word)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = uiState.addWordText,
                            onValueChange = { viewModel.onAddWordTextChanged(it) },
                            placeholder = {
                                Text(stringResource(R.string.dictionary_add_word_hint))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.addDefinitionText,
                            onValueChange = { viewModel.onAddDefinitionTextChanged(it) },
                            placeholder = {
                                Text(stringResource(R.string.dictionary_add_definition_hint))
                            },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onAddWordConfirm() },
                        enabled = uiState.addWordText.isNotBlank()
                    ) {
                        Text(text = stringResource(R.string.dictionary_add_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onDismissAddDialog() }) {
                        Text(text = stringResource(R.string.reader_cancel))
                    }
                }
            )
        }

        // ── Delete Confirmation Dialog ──────────────────────────
        uiState.wordToDelete?.let { word ->
            AlertDialog(
                onDismissRequest = { viewModel.onDismissDeleteDialog() },
                title = { Text(text = stringResource(R.string.dictionary_delete_title)) },
                text = {
                    Text(
                        text = stringResource(R.string.dictionary_delete_message, word.word)
                    )
                },
                confirmButton = {
                    NextPageButton(
                        onClick = { viewModel.onConfirmDeleteWord() },
                        variant = NextPageButtonVariant.TEXT
                    ) {
                        Text(text = stringResource(R.string.dictionary_delete_confirm))
                    }
                },
                dismissButton = {
                    NextPageButton(
                        onClick = { viewModel.onDismissDeleteDialog() },
                        variant = NextPageButtonVariant.TEXT
                    ) {
                        Text(text = stringResource(R.string.reader_cancel))
                    }
                }
            )
        }

        // ── Edit Definition Dialog ───────────────────────────────
        uiState.wordBeingEdited?.let { word ->
            AlertDialog(
                onDismissRequest = { viewModel.onDismissEditDialog() },
                title = {
                    Text(
                        text = word.word,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    OutlinedTextField(
                        value = uiState.editDefinitionText,
                        onValueChange = { viewModel.onEditDefinitionTextChanged(it) },
                        placeholder = {
                            Text(stringResource(R.string.dictionary_add_definition_hint))
                        },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onEditDefinitionConfirm() }
                    ) {
                        Text(text = stringResource(R.string.dictionary_edit_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onDismissEditDialog() }) {
                        Text(text = stringResource(R.string.reader_cancel))
                    }
                }
            )
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
