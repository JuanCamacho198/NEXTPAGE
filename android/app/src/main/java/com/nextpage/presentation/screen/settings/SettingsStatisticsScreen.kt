package com.nextpage.presentation.screen.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nextpage.R
import com.nextpage.presentation.screen.StatisticsScreen
import com.nextpage.presentation.viewmodel.StatisticsViewModel
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage

@Composable
fun SettingsStatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit
) {
    NextPageSettingsSubPage(
        title = stringResource(R.string.statistics_title),
        onBack = onBack
    ) {
        StatisticsScreen(
            contentPadding = PaddingValues(),
            viewModel = viewModel
        )
    }
}
