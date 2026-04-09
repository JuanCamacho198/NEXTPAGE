package com.nextpage.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NextPageDarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorNeutral,
    secondary = ColorSecondary,
    tertiary = ColorTertiary,
    background = BackgroundDark,
    surface = SurfaceDark,
    outline = OutlineDark,
    error = ErrorSoft,
    onBackground = ColorPrimary,
    onSurface = ColorPrimary,
    onError = ColorNeutral
)

@Composable
fun NextPageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NextPageDarkColorScheme,
        typography = NextPageTypography,
        shapes = NextPageShapes,
        content = content
    )
}
