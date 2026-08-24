package com.nextpage.presentation.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nextpage.presentation.theme.NextPageTheme

@Composable
fun SettingsAboutScreen(
    onBack: () -> Unit
) {
    AboutScreen(onBack = onBack)
}

// ─── Previews ─────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SettingsAboutScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        SettingsAboutScreen(onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsAboutScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        SettingsAboutScreen(onBack = {})
    }
}
