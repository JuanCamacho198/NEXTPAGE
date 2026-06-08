package com.nextpage.presentation.theme

import androidx.compose.ui.graphics.Color

// ─── Design Tokens - Backgrounds ──────────────────────────────────────
val BgMain = Color(0xFF0B1120)          // $bg_main: Dark, almost black blue
val BgSurface = Color(0xFF161F33)       // $bg_surface: Card/surface background
val BgCardHover = Color(0xFF1E293B)     // $bg-card-hover

// ─── Design Tokens - Borders ─────────────────────────
val BorderSubtle = Color(0xFF242A3A)

// ─── Design Tokens - Surfaces ────────────────────────
val BgHeader = Color(0xFF0D1322)

// ─── Design Tokens - Primary / Brand ──────────────────────────────────
val PrimaryBlue = Color(0xFF3B82F6)     // $primary: Main brand color, buttons, accents
val PrimaryBlueHover = Color(0xFF2563EB) // $primary-blue-hover
val IconBgBlue = Color(0xFF1E3A8A)      // $icon-bg-blue: Darker blue for icon backgrounds

// ─── Design Tokens - Text ────────────────────────────────────────────
val TextPrimary = Color(0xFFFFFFFF)      // $text-primary: White
val TextSecondary = Color(0xFF94A3B8)    // $text-secondary: Slate gray
val TextAccent = Color(0xFF3B82F6)       // $text-accent: Blue accent

// ─── Design Tokens - Status / Category Colors ─────────────────────────
val AccentYellow = Color(0xFFEAB308)
val BgYellowTransparent = Color(0xFF423419)
val AccentGreen = Color(0xFF22C55E)
val BgGreenTransparent = Color(0xFF143A27)
val AccentPurple = Color(0xFFA855F7)
val BgPurpleTransparent = Color(0xFF311C4A)
val AccentBlue = Color(0xFF3B82F6)
val BgBlueTransparent = Color(0xFF1E3A8A)

// ─── Navigation Bar ──────────────────────────────────
val NavBarActive = Color(0xFFADC6FF)
val NavBarInactive = Color(0xFFC2C6D6)
val NavBarOverlay = Color(0x0A4D8EFF)

// ─── Legacy aliases (backward compat) ─────────────────────────────────
val DeepDarkBlue = BgMain
val AccentPrimary = PrimaryBlue
val AccentSecondary = AccentGreen
val ChartAccent = AccentPurple
val ReadingBackgroundLight = Color(0xFFF9F9F9)
val ReadingBackgroundDark = Color(0xFF000000)

val ColorPrimary = TextPrimary
val ColorSecondary = Color(0xFFE0E0E0)
val ColorTertiary = Color(0xFF9E9E9E)
val ColorNeutral = Color(0xFF000000)

val BackgroundDark = BgMain
val SurfaceDark = BgSurface
val OutlineDark = Color(0xFF30363D)
val ErrorSoft = Color(0xFFF85149)
