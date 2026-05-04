package com.nextpage.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val readingBackground: Color,
    val chartAccent: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        readingBackground = Color.Unspecified,
        chartAccent = Color.Unspecified
    )
}

val darkExtendedColors = ExtendedColors(
    readingBackground = ReadingBackgroundDark,
    chartAccent = ChartAccent
)

val lightExtendedColors = ExtendedColors(
    readingBackground = ReadingBackgroundLight,
    chartAccent = ChartAccent
)
