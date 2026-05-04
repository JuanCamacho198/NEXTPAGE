package com.nextpage.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageDimens

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(NextPageDimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Settings coming soon.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}