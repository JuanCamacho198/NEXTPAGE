package com.nextpage.presentation.screen.readium

import com.nextpage.domain.model.ReaderSettings
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme

/**
 * Maps [ReaderSettings] to Readium's [EpubPreferences] for
 * [org.readium.r2.navigator.epub.EpubNavigatorFragment.submitPreferences].
 */
internal fun ReaderSettings.toEpubPreferences(): EpubPreferences {
    return EpubPreferences(
        fontSize = when (fontSize) {
            com.nextpage.domain.model.FontSizePreset.XS -> 0.75
            com.nextpage.domain.model.FontSizePreset.S -> 0.875
            com.nextpage.domain.model.FontSizePreset.SM -> 1.0
            com.nextpage.domain.model.FontSizePreset.M -> 1.125
            com.nextpage.domain.model.FontSizePreset.ML -> 1.25
            com.nextpage.domain.model.FontSizePreset.L -> 1.5
            com.nextpage.domain.model.FontSizePreset.XL -> 1.75
            com.nextpage.domain.model.FontSizePreset.XXL -> 2.0
        },
        fontFamily = when (fontName.lowercase()) {
            "serif" -> ReadiumFontFamily.SERIF
            "sans-serif", "arial" -> ReadiumFontFamily.SANS_SERIF
            else -> ReadiumFontFamily.SERIF
        },
        theme = when (theme) {
            com.nextpage.domain.model.ReaderTheme.DARK -> ReadiumTheme.DARK
            com.nextpage.domain.model.ReaderTheme.SEPIA -> ReadiumTheme.SEPIA
            com.nextpage.domain.model.ReaderTheme.LIGHT, com.nextpage.domain.model.ReaderTheme.OLED -> ReadiumTheme.LIGHT
        },
        lineHeight = lineHeight.value.toDouble(),
        scroll = scrollMode == com.nextpage.domain.model.ScrollMode.VERTICAL || verticalScroll,
        publisherStyles = true,
        pageMargins = 1.4
    )
}

/**
 * Builds a [EpubNavigatorFactory.Configuration] from [ReaderSettings].
 * @deprecated Use [toEpubPreferences] instead.
 */
@Deprecated("Use toEpubPreferences()", ReplaceWith("settings.toEpubPreferences()"))
fun buildNavigatorConfig(settings: ReaderSettings): EpubNavigatorFactory.Configuration {
    // Delegate to mapper to keep mapping single-sourced; result not embedded in Configuration
    // but call ensures future changes to preferences stay coordinated.
    settings.toEpubPreferences()
    return EpubNavigatorFactory.Configuration(
        defaults = EpubDefaults(
            pageMargins = 1.4
        )
    )
}
