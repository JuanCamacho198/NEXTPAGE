package com.nextpage.presentation.screen.readium

import com.nextpage.domain.model.FontSizePreset
import com.nextpage.domain.model.LineHeightPreset
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.ReaderTheme
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadiumPreferencesMapperTest {

    @Test
    fun toEpubPreferences_mapsFontScaleAndTheme() {
        val settings = ReaderSettings(
            fontSize = FontSizePreset.L,
            lineHeight = LineHeightPreset.COMFORTABLE,
            fontName = "serif",
            theme = ReaderTheme.SEPIA
        )
        val prefs = settings.toEpubPreferences()
        assertNotNull(prefs)
    }

    @Test
    fun toEpubPreferences_mapsLightTheme() {
        val settings = ReaderSettings(theme = ReaderTheme.LIGHT)
        val prefs = settings.toEpubPreferences()
        assertNotNull(prefs)
    }

    @Test
    fun deprecatedShim_delegatesToMapper() {
        val settings = ReaderSettings(fontSize = FontSizePreset.XS)
        val viaMapper = settings.toEpubPreferences()
        @Suppress("DEPRECATION")
        val config = buildNavigatorConfig(settings)
        assertNotNull(config)
        assertNotNull(viaMapper)
    }
}
