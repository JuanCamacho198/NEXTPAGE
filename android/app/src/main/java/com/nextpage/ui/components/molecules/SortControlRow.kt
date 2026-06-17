package com.nextpage.ui.components.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant

@Composable
fun SortControlRow(
    sortBy: String,
    onSortByChanged: (String) -> Unit,
    isGridView: Boolean,
    onViewToggle: () -> Unit
) {
    var showSortSelector by remember { mutableStateOf(false) }

    val sortOptions = listOf(
        SelectorOption("date_added", R.string.library_sort_date_added),
        SelectorOption("title", R.string.library_sort_title),
        SelectorOption("author", R.string.library_sort_author),
        SelectorOption("last_read", R.string.library_sort_last_read)
    )

    val selectedSortLabel = sortOptions.find { it.id == sortBy }?.labelRes
        ?: R.string.library_sort_date_added

    if (showSortSelector) {
        NextPageSelector(
            title = stringResource(R.string.library_sort_label),
            options = sortOptions,
            selectedOptionId = sortBy,
            onOptionSelected = { option ->
                onSortByChanged(option.id)
                showSortSelector = false
            },
            onDismiss = { showSortSelector = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_sort_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            NextPageButton(
                onClick = { showSortSelector = true },
                variant = NextPageButtonVariant.TEXT
            ) {
                Text(text = stringResource(selectedSortLabel))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { if (!isGridView) onViewToggle() }) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = stringResource(R.string.library_view_grid),
                    tint = if (isGridView) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { if (isGridView) onViewToggle() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ViewList,
                    contentDescription = stringResource(R.string.library_view_list),
                    tint = if (!isGridView) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
