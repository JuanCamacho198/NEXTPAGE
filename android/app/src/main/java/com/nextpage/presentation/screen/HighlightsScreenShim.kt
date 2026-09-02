package com.nextpage.presentation.screen

import androidx.compose.ui.graphics.Color
import com.nextpage.presentation.feature.highlights.resolveHighlightColorHex as featureResolve
import com.nextpage.presentation.feature.highlights.stripSurroundingQuotes as featureStrip

/** Shim preserving old FQN for tests after feature move. */
fun resolveHighlightColorHex(hex: String): Color? = featureResolve(hex)

fun stripSurroundingQuotes(text: String): String = featureStrip(text)
