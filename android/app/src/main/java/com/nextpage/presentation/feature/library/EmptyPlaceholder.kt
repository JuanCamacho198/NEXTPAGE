package com.nextpage.presentation.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageEmptyState
import com.nextpage.ui.icons.NextPageIcons

@Composable
fun EmptyShelfPlaceholder(isImporting: Boolean, onImportClick: () -> Unit) {
    NextPageEmptyState(icon = NextPageIcons.LibraryBooks, title = stringResource(R.string.library_empty), subtitle = stringResource(R.string.library_import_formats), modifier = Modifier.fillMaxWidth().padding(horizontal = NextPageDimens.spacingMd, vertical = NextPageDimens.spacingLg), action = { NextPageButton(onClick = onImportClick, enabled = !isImporting, variant = NextPageButtonVariant.OUTLINED, border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary)) { Text(text = stringResource(R.string.library_import_book)) } })
}
