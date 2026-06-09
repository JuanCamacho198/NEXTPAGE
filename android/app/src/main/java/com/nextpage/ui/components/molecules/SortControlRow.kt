package com.nextpage.ui.components.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun SortControlRow(
    sortBy: String,
    onSortByChanged: (String) -> Unit,
    isGridView: Boolean,
    onViewToggle: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortLabelRes = when (sortBy) {
        "title" -> R.string.library_sort_title
        "author" -> R.string.library_sort_author
        "last_read" -> R.string.library_sort_last_read
        else -> R.string.library_sort_date_added
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
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(text = stringResource(sortLabelRes))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_date_added)) },
                        onClick = {
                            onSortByChanged("date_added")
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_title)) },
                        onClick = {
                            onSortByChanged("title")
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_author)) },
                        onClick = {
                            onSortByChanged("author")
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_sort_last_read)) },
                        onClick = {
                            onSortByChanged("last_read")
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { if (!isGridView) onViewToggle() }) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = "Grid view",
                    tint = if (isGridView) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { if (isGridView) onViewToggle() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ViewList,
                    contentDescription = "List view",
                    tint = if (!isGridView) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
