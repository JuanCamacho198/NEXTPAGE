package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.ui.components.atoms.NextPageProgressBar
import com.nextpage.ui.components.atoms.NextPageTypography

@Composable
fun BookCard(
    title: String,
    author: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Placeholder for Cover Image
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(modifier = Modifier.height(12.dp))
                NextPageTypography(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                NextPageTypography(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                NextPageProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Options menu button
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.book_card_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Dropdown menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.book_card_edit_metadata)) },
                onClick = {
                    showMenu = false
                    // TODO: Navigate to edit screen
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.book_card_remove_from_library)) },
                onClick = {
                    showMenu = false
                    onDeleteClick()
                }
            )
        }
    }
}