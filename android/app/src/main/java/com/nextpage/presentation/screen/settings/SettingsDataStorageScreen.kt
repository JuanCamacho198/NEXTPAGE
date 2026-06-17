package com.nextpage.presentation.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.ui.components.molecules.NextPagePreferenceItem
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage

@Composable
fun SettingsDataStorageScreen(
    onNavigateToStatistics: () -> Unit,
    onBack: () -> Unit
) {
    NextPageSettingsSubPage(
        title = stringResource(R.string.settings_data_storage_title),
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        NextPagePreferenceItem(
            icon = Icons.Outlined.Storage,
            label = stringResource(R.string.settings_storage),
            onClick = {}
        )

        NextPagePreferenceItem(
            icon = Icons.Outlined.CloudSync,
            label = stringResource(R.string.settings_sync),
            onClick = {}
        )

        NextPagePreferenceItem(
            icon = Icons.Outlined.BarChart,
            label = stringResource(R.string.settings_statistics_title),
            onClick = onNavigateToStatistics
        )
    }
}
