package com.nextpage.presentation.screen

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.presentation.viewmodel.CfiMigrator
import com.nextpage.presentation.viewmodel.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** Group identifier for all highlight decorations. */
private const val DECORATION_GROUP = "com.nextpage.highlights"

/**
 * Readium-powered EPUB reader content.
 *
 * Hosts Readium's [EpubNavigatorFragment] inside a [FragmentContainerView]
 * via [AndroidView].  Creates an [EpubNavigatorFactory] from the
 * [Publication] and uses its [EpubNavigatorFactory.createFragmentFactory]
 * to set up the fragment so it can render EPUB content natively.
 */
@Composable
fun ReadiumReaderContent(
    publication: Publication,
    navigatorConfig: EpubNavigatorFactory.Configuration,
    highlights: List<Highlight>,
    readerSettings: ReaderSettings,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current as FragmentActivity
    val fragmentManager = remember { context.supportFragmentManager }
    val containerId = remember { View.generateViewId() }

    // Create the EpubNavigatorFactory — Readium 3.x pattern
    val navigatorFactory = remember(publication, navigatorConfig) {
        EpubNavigatorFactory(publication, navigatorConfig)
    }

    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var containerReady by remember { mutableStateOf(false) }

    // ── Commit EpubNavigatorFragment ──────────────────────────────
    LaunchedEffect(containerReady) {
        if (!containerReady) return@LaunchedEffect
        val tag = "ReadiumNavigator"
        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            val initialLocator = publication.readingOrder.firstOrNull()?.let {
                publication.locatorFromLink(it)
            }
            val factory = navigatorFactory.createFragmentFactory(
                initialLocator = initialLocator!!
            )
            fragmentManager.fragmentFactory = factory
            fragmentManager.commit {
                add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag)
            }
        }
        delay(200)
        navigatorFragment = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment
    }

    // ── currentLocator → ViewModel ────────────────────────────────
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        try {
            @Suppress("UNCHECKED_CAST")
            val locatorFlow = frag.currentLocator as StateFlow<Locator>
            locatorFlow.collect { locator ->
                viewModel.onReadiumLocatorChanged(locator)
            }
        } catch (_: Exception) { }
    }

    // ── currentSelection (poll + diff + debug) → ViewModel ──────
    // DEBUG: run `adb logcat -s SelectionDebug` to trace the pipeline
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        Log.d("SelectionDebug", "navigatorFragment acquired, casting to SelectableNavigator...")
        val selectable = frag as? SelectableNavigator
        if (selectable == null) {
            Log.e("SelectionDebug", "CAST FAILED: EpubNavigatorFragment is NOT a SelectableNavigator")
            return@LaunchedEffect
        }
        Log.d("SelectionDebug", "CAST OK: SelectableNavigator=${selectable.hashCode()}")
        var lastSelection: Boolean = false
        var pollCount = 0
        while (isActive) {
            delay(300)
            pollCount++
            Log.d("SelectionDebug", "Poll #$pollCount — calling currentSelection()...")
            val sel: org.readium.r2.navigator.Selection? = try {
                selectable.currentSelection()
            } catch (e: Throwable) {
                Log.e("SelectionDebug", "currentSelection() THREW: ${e::class.simpleName}: ${e.message}", e)
                null
            }
            Log.d("SelectionDebug", "Poll #$pollCount — currentSelection() returned: ${if (sel != null) "non-null (locator=${sel.locator.href})" else "null"}")
            if (sel != null) {
                Log.d("SelectionDebug", "sel.rect=${sel.rect}, sel.locator.locations.totalProgression=${sel.locator.locations.totalProgression}")
                val selRect = sel.rect
                if (selRect == null) {
                    Log.w("SelectionDebug", "sel.rect is null — skipping")
                    if (lastSelection) {
                        Log.d("SelectionDebug", "Clearing previous selection (rect was null)")
                        viewModel.onSelectionCleared()
                    }
                    lastSelection = false
                    continue
                }
                // Extract selected text
                Log.d("SelectionDebug", "Attempting evaluateJavascript...")
                val text: String = try {
                    val jsResult = frag.evaluateJavascript(
                        "(function(){var s=window.getSelection();return s?s.toString():'';})()"
                    )
                    Log.d("SelectionDebug", "evaluateJavascript result: '$jsResult'")
                    if (jsResult.isNullOrBlank()) {
                        val fallback = sel.locator.text?.let { "${it.before ?: ""}${it.after ?: ""}" } ?: ""
                        Log.d("SelectionDebug", "JS result empty, using locator.text fallback: '$fallback'")
                        fallback
                    } else {
                        jsResult
                    }
                } catch (e: Throwable) {
                    Log.e("SelectionDebug", "evaluateJavascript THREW: ${e::class.simpleName}: ${e.message}", e)
                    val fallback = sel.locator.text?.let { "${it.before ?: ""}${it.after ?: ""}" } ?: ""
                    Log.d("SelectionDebug", "Using locator.text fallback after exception: '$fallback'")
                    fallback
                }
                Log.d("SelectionDebug", "Calling viewModel.onReadiumSelection(text='${text.take(50)}', rect=$selRect)")
                try {
                    viewModel.onReadiumSelection(
                        locator = sel.locator,
                        rect = selRect,
                        text = text
                    )
                    Log.d("SelectionDebug", "viewModel.onReadiumSelection OK")
                } catch (e: Throwable) {
                    Log.e("SelectionDebug", "viewModel.onReadiumSelection THREW: ${e::class.simpleName}: ${e.message}", e)
                }
                lastSelection = true
            } else {
                if (lastSelection) {
                    Log.d("SelectionDebug", "Selection cleared (user tapped away)")
                    viewModel.onSelectionCleared()
                }
                lastSelection = false
            }
        }
    }

    // ── Decoration sync (highlights → decorations) ────────────────
    // Reapplies ALL decorations whenever the highlights list changes (HL-5).
    LaunchedEffect(highlights, navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        val decorable = frag as? DecorableNavigator ?: return@LaunchedEffect
        if (highlights.isEmpty()) {
            decorable.applyDecorations(emptyList(), DECORATION_GROUP)
            return@LaunchedEffect
        }
        val decorations = highlightsToDecorations(highlights)
        decorable.applyDecorations(decorations, DECORATION_GROUP)
    }

    // ── Settings sync (readerSettings → EpubPreferences) ──────────
    // Debounced 300ms to avoid rapid-setting flicker (D3).
    LaunchedEffect(readerSettings) {
        delay(300)
        val frag = navigatorFragment ?: return@LaunchedEffect
        try {
            val prefs = readerSettings.toEpubPreferences()
            frag.submitPreferences(prefs)
        } catch (_: Exception) { }
    }

    // ── navigateToLocator event flow → navigator.go() (SEA-2) ────
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        viewModel.navigateToLocator.collect { locator ->
            try {
                frag.go(locator, animated = false)
            } catch (_: Exception) { }
        }
    }

    // ── Clean up ──────────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            val frag = navigatorFragment
            if (frag != null && frag.isAdded) {
                fragmentManager.commit { remove(frag) }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).also { it.id = containerId; containerReady = true }
        },
        modifier = modifier.onGloballyPositioned { coordinates ->
            viewModel.onReadiumViewportChanged(coordinates.size.height)
        }
    )
}

// ── Utilities ──────────────────────────────────────────────────────

/**
 * Maps a [Highlight] list to a [Decoration] list for Readium's
 * [DecorableNavigator.applyDecorations].
 */
private fun highlightsToDecorations(highlights: List<Highlight>): List<Decoration> {
    return highlights.mapNotNull { h ->
        val json = h.locatorJson ?: return@mapNotNull null
        val locator = CfiMigrator.jsonToLocator(json) ?: return@mapNotNull null
        val tint = try {
            android.graphics.Color.parseColor(h.color)
        } catch (_: Exception) {
            android.graphics.Color.YELLOW
        }
        Decoration(
            id = h.id,
            locator = locator,
            style = Decoration.Style.Highlight(tint = tint, isActive = true)
        )
    }
}

/**
 * Maps [ReaderSettings] to Readium's [EpubPreferences] for
 * [EpubNavigatorFragment.submitPreferences].
 */
private fun ReaderSettings.toEpubPreferences(): EpubPreferences {
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
 */
fun buildNavigatorConfig(settings: ReaderSettings): EpubNavigatorFactory.Configuration {
    return EpubNavigatorFactory.Configuration(
        defaults = EpubDefaults(
            pageMargins = 1.4
        )
    )
}
