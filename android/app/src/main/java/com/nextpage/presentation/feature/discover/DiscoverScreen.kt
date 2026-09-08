package com.nextpage.presentation.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextpage.R
import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.data.remote.catalog.CatalogErrorCode
import com.nextpage.presentation.viewmodel.DiscoverDetailStatus
import com.nextpage.presentation.viewmodel.DiscoverStatus
import com.nextpage.presentation.viewmodel.DiscoverUiState
import com.nextpage.presentation.viewmodel.DiscoverViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    contentPadding: PaddingValues,
    viewModel: DiscoverViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(uiState.query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_discover),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.onQueryChange(it)
            },
            placeholder = { Text(stringResource(R.string.discover_search_hint)) },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = viewModel::searchFirstPage) {
                    Text(stringResource(R.string.discover_search))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        DiscoverDetailSection(
            detail = uiState.detail,
            detailStatus = uiState.detailStatus,
            onDismiss = viewModel::dismissDetail,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState.status) {
                DiscoverStatus.IDLE ->
                    DiscoverStatusText(stringResource(R.string.discover_idle))
                DiscoverStatus.LOADING ->
                    DiscoverStatusText(stringResource(R.string.discover_loading))
                DiscoverStatus.EMPTY ->
                    DiscoverStatusText(stringResource(R.string.discover_empty))
                DiscoverStatus.OFFLINE, DiscoverStatus.ERROR -> {
                    val message = when {
                        uiState.status == DiscoverStatus.OFFLINE -> stringResource(R.string.discover_offline)
                        uiState.errorCode == CatalogErrorCode.INVALID_PAGE ->
                            stringResource(R.string.discover_error_invalid_page)
                        uiState.errorCode == CatalogErrorCode.NOT_FOUND ->
                            stringResource(R.string.discover_error_not_found)
                        else -> stringResource(R.string.discover_error_upstream)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiscoverStatusText(message)
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.discover_retry))
                        }
                    }
                }
                DiscoverStatus.LOADED, DiscoverStatus.LOADING_MORE -> {
                    val books = uiState.books
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                            DiscoverCard(book = book, onOpen = viewModel::openDetail)
                            if (index >= books.lastIndex - 3 && uiState.nextPage != null) {
                                LaunchedEffect(books.size) { viewModel.loadNextPage() }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val footer = when {
                                uiState.status == DiscoverStatus.LOADING_MORE ->
                                    stringResource(R.string.discover_loading_more)
                                uiState.nextPage == null ->
                                    stringResource(R.string.discover_end_of_results)
                                else -> null
                            }
                            if (footer != null) {
                                Text(
                                    text = footer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverStatusText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverDetailSection(
    detail: CatalogBook?,
    detailStatus: DiscoverDetailStatus,
    onDismiss: () -> Unit,
) {
    if (detailStatus == DiscoverDetailStatus.CLOSED) return
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when {
            detailStatus == DiscoverDetailStatus.LOADING ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            detailStatus == DiscoverDetailStatus.NOT_FOUND || detail == null ->
                Text(
                    text = stringResource(
                        if (detailStatus == DiscoverDetailStatus.NOT_FOUND) {
                            R.string.discover_detail_not_found
                        } else {
                            R.string.discover_error_upstream
                        },
                    ),
                    modifier = Modifier.padding(24.dp),
                )
            else -> DiscoverDetailContent(detail = detail, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun DiscoverDetailContent(detail: CatalogBook, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = detail.title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.discover_by_authors, detail.authors.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail.languages.isNotEmpty()) {
            Text(
                text = stringResource(R.string.discover_languages, detail.languages.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (detail.subjects.isNotEmpty()) {
            Text(
                text = stringResource(R.string.discover_subjects, detail.subjects.take(8).joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider()
        if (detail.downloadUrl != null) {
            TextButton(onClick = { uriHandler.openUri(detail.downloadUrl) }) {
                Text(stringResource(R.string.discover_download))
            }
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.discover_dismiss))
        }
    }
}
