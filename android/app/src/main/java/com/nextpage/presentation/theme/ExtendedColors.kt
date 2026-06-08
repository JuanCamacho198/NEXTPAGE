package com.nextpage.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val readingBackground: Color,
    val chartAccent: Color,
    val bgHeader: Color,
    val borderSubtle: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        readingBackground = Color.Unspecified,
        chartAccent = Color.Unspecified,
        bgHeader = Color.Unspecified,
        borderSubtle = Color.Unspecified
    )
}

val darkExtendedColors = ExtendedColors(
    readingBackground = ReadingBackgroundDark,
    chartAccent = ChartAccent,
    bgHeader = BgHeader,
    borderSubtle = BorderSubtle
)

val lightExtendedColors = ExtendedColors(
    readingBackground = ReadingBackgroundLight,
    chartAccent = ChartAccent,
    bgHeader = BgHeader,
    borderSubtle = BorderSubtle
)
