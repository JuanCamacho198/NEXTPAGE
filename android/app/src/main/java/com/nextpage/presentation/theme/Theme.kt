package com.nextpage.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val NextPageDarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextPrimary,
    secondary = TextSecondary,
    tertiary = ColorTertiary,
    background = BgMain,
    surface = BgSurface,
    surfaceVariant = BgSurface,
    outline = OutlineDark,
    error = ErrorSoft,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    onError = ColorNeutral
)

private val NextPageLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextPrimary,
    secondary = TextSecondary,
    tertiary = ColorTertiary,
    background = TextPrimary,
    surface = Color(0xFFF6F8FA),
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E),
    onBackground = ColorNeutral,
    onSurface = ColorNeutral,
    onError = TextPrimary
)

@Composable
fun NextPageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NextPageDarkColorScheme else NextPageLightColorScheme
    val extendedColors = if (darkTheme) darkExtendedColors else lightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NextPageTypography,
            shapes = NextPageShapes,
            content = content
        )
    }
}

object NextPageTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
