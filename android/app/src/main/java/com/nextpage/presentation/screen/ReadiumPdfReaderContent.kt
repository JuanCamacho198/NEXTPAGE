package com.nextpage.presentation.screen

import android.os.Bundle
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
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider

/** Group identifier for all highlight decorations. */
private const val DECORATION_GROUP = "com.nextpage.highlights"

/**
 * Readium-powered PDF reader content.
 *
 * Hosts Readium's [PdfNavigatorFragment] inside a [FragmentContainerView]
 * via [AndroidView].  Creates a [PdfNavigatorFactory] from the [Publication]
 * and a [PdfiumEngineProvider], then uses [PdfNavigatorFactory.createFragmentFactory]
 * to set up the fragment for native PDF rendering.
 */
@Composable
fun ReadiumPdfReaderContent(
    publication: Publication,
    highlights: List<Highlight>,
    readerSettings: ReaderSettings,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current as FragmentActivity
    val fragmentManager = remember { context.supportFragmentManager }
    val containerId = remember { View.generateViewId() }

    // Create PdfiumEngineProvider — the adapter engine for PdfNavigatorFragment
    val pdfEngineProvider = remember {
        PdfiumEngineProvider()
    }

    // Create the PdfNavigatorFactory — does NOT use a Configuration class
    val navigatorFactory = remember(publication, pdfEngineProvider) {
        PdfNavigatorFactory(publication, pdfEngineProvider)
    }

    var navigatorFragment by remember { mutableStateOf<PdfNavigatorFragment<*, *>?>(null) }
    var containerReady by remember { mutableStateOf(false) }

    // ── Commit PdfNavigatorFragment ───────────────────────────────
    LaunchedEffect(containerReady) {
        if (!containerReady) return@LaunchedEffect
        val tag = "ReadiumPdfNavigator"
        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            val initialLocator = publication.readingOrder.firstOrNull()?.let {
                publication.locatorFromLink(it)
            }
            val factory = navigatorFactory.createFragmentFactory(
                initialLocator = initialLocator
            )
            fragmentManager.fragmentFactory = factory
            fragmentManager.commit {
                add(containerId, PdfNavigatorFragment::class.java, Bundle(), tag)
            }
        }
        delay(200)
        @Suppress("UNCHECKED_CAST")
        navigatorFragment = fragmentManager.findFragmentByTag(tag) as? PdfNavigatorFragment<*, *>
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

    // ── currentSelection (poll + diff) → ViewModel ────────────────
    // Note: PdfNavigatorFragment does NOT implement evaluateJavascript,
    // so we fall back to extracting text from the locator's text context.
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        val selectable = frag as? SelectableNavigator ?: return@LaunchedEffect
        var lastSelection: Boolean = false
        while (isActive) {
            delay(300)
            val sel = runCatching { selectable.currentSelection() }.getOrNull()
            if (sel != null) {
                val selRect = sel.rect ?: run {
                    if (lastSelection) viewModel.onSelectionCleared()
                    lastSelection = false
                    continue
                }
                // Derive text from the locator's text context (no JS bridge for PDF)
                val text = sel.locator.text?.let { "${it.before ?: ""}${it.after ?: ""}" } ?: ""
                viewModel.onReadiumSelection(
                    locator = sel.locator,
                    rect = selRect,
                    text = text
                )
                lastSelection = true
            } else {
                if (lastSelection) {
                    viewModel.onSelectionCleared()
                }
                lastSelection = false
            }
        }
    }

    // ── Decoration sync (highlights → decorations) ────────────────
    LaunchedEffect(highlights, navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        val decorable = frag as? DecorableNavigator ?: return@LaunchedEffect
        if (highlights.isEmpty()) {
            decorable.applyDecorations(emptyList(), DECORATION_GROUP)
            return@LaunchedEffect
        }
        val decorations = highlightsToPdfDecorations(highlights)
        decorable.applyDecorations(decorations, DECORATION_GROUP)
    }

    // ── navigateToLocator event flow → navigator.go() ────────────
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

// ── Utilities ────────────────────────────────────────────────────────

/**
 * Maps a [Highlight] list to a [Decoration] list for Readium's
 * [DecorableNavigator.applyDecorations].
 */
private fun highlightsToPdfDecorations(highlights: List<Highlight>): List<Decoration> {
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
