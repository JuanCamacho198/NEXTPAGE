package com.nextpage.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic design tokens for NextPage.
 *
 * New code should reference these tokens instead of raw [Color] literals.
 * Legacy aliases in [Color.kt] remain for backward compatibility with
 * reader-specific code that predates this object.
 */
object NextPageColors {
    // Backgrounds
    val background: Color = BgMain
    val surface: Color = BgSurface
    val surfaceVariant: Color = BgCardHover
    val header: Color = BgHeader

    // Borders
    val borderSubtle: Color = BorderSubtle

    // Primary / Brand
    val primary: Color = PrimaryBlue
    val primaryHover: Color = PrimaryBlueHover
    val iconBackground: Color = IconBgBlue

    // Text
    val textPrimary: Color = TextPrimary
    val textSecondary: Color = TextSecondary
    val textAccent: Color = TextAccent

    // Status / Category
    val accentYellow: Color = AccentYellow
    val backgroundYellowTransparent: Color = BgYellowTransparent
    val accentGreen: Color = AccentGreen
    val backgroundGreenTransparent: Color = BgGreenTransparent
    val accentPurple: Color = AccentPurple
    val backgroundPurpleTransparent: Color = BgPurpleTransparent
    val accentBlue: Color = AccentBlue
    val backgroundBlueTransparent: Color = BgBlueTransparent

    // Navigation
    val navBarActive: Color = com.nextpage.presentation.theme.NavBarActive
    val navBarInactive: Color = com.nextpage.presentation.theme.NavBarInactive
    val navBarOverlay: Color = com.nextpage.presentation.theme.NavBarOverlay

    // Utility
    val outline: Color = OutlineDark
    val errorSoft: Color = ErrorSoft
    val readingBackgroundLight: Color = com.nextpage.presentation.theme.ReadingBackgroundLight
    val readingBackgroundDark: Color = com.nextpage.presentation.theme.ReadingBackgroundDark
}
