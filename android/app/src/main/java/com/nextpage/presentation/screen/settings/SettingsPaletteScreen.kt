package com.nextpage.presentation.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.ui.components.molecules.HighlightPaletteSection
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage

@Composable
fun SettingsPaletteScreen(
    customHighlightColors: List<String>?,
    onUpdateCustomHighlightColor: (Int, String) -> Unit,
    onResetCustomHighlightColors: () -> Unit,
    onBack: () -> Unit
) {
    NextPageSettingsSubPage(
        title = stringResource(R.string.palette_section_title),
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        HighlightPaletteSection(
            customColors = customHighlightColors,
            onUpdateColor = { index, hex ->
                onUpdateCustomHighlightColor(index, hex)
            },
            onReset = {
                onResetCustomHighlightColors()
            }
        )
    }
}
