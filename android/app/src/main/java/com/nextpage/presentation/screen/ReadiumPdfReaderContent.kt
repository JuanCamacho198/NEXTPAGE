package com.nextpage.presentation.screen

import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.nextpage.debug.DebugLog
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
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider

/** Group identifier for all highlight decorations. */
private const val DECORATION_GROUP = "com.nextpage.highlights"

/**
 * [ActionMode.Callback] that suppresses the native text-selection toolbar.
 * Returning `false` from [onCreateActionMode] prevents the ActionMode from
 * being created at all, so only our custom [SelectionOverlay] is shown.
 */
private object SuppressPdfSelectionActionMode : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = false
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
    override fun onDestroyActionMode(mode: ActionMode) = Unit
}

/**
 * Readium-powered PDF reader content.
 *
 * Hosts Readium's [PdfNavigatorFragment] inside a [FragmentContainerView]
 * via [AndroidView].  Creates a [PdfNavigatorFactory] from the [Publication]
 * and a [PdfiumEngineProvider], then uses [PdfNavigatorFactory.createFragmentFactory]
 * to set up the fragment for native PDF rendering.
 */
@Composable
@OptIn(ExperimentalReadiumApi::class)
fun ReadiumPdfReaderContent(
    publication: Publication,
    highlights: List<Highlight>,
    readerSettings: ReaderSettings,
    viewModel: ReaderViewModel,
    onShowChrome: (() -> Unit)? = null,
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

    // ── Suppress native ActionMode if the PDF navigator hosts a WebView ─
    // PdfNavigatorFragment with PdfiumEngineProvider renders via a native PDF
    // view, so a WebView is normally not present and no ActionMode appears.
    // We still install the guard defensively in case the engine changes.
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        repeat(20) {
            val view = frag.view
            if (view != null) {
                installPdfActionModeCallback(view)
                DebugLog.success("PdfReadium", "Installed ActionMode callback (defensive)")
                return@LaunchedEffect
            }
            delay(50)
        }
        Log.d("ReadiumPdfReaderContent", "No selectable view ready for action-mode suppression")
        DebugLog.warn("PdfReadium", "No selectable view ready for action-mode suppression")
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
        val selectable = frag as? SelectableNavigator ?: run {
            DebugLog.warn("PdfReadium", "PdfNavigatorFragment is NOT a SelectableNavigator")
            return@LaunchedEffect
        }
        DebugLog.info("PdfReadium", "SelectableNavigator acquired (hash=${selectable.hashCode()})")
        var lastSelection: Boolean = false
        var pollCount = 0
        while (isActive) {
            delay(300)
            pollCount++
            val sel = runCatching { selectable.currentSelection() }
                .onFailure { DebugLog.error("PdfReadium", "currentSelection() threw: ${it.message}") }
                .getOrNull()
            if (pollCount % 10 == 0) {
                DebugLog.info(
                    "PdfReadium",
                    "Poll #$pollCount: ${if (sel != null) "selection present" else "no selection"}"
                )
            }
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

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                FragmentContainerView(ctx).also { view ->
                    view.id = containerId
                    view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            containerReady = true
                        }
                        override fun onViewDetachedFromWindow(v: android.view.View) {}
                    })
                }
            },
            modifier = Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                viewModel.onReadiumViewportChanged(coordinates.size.height)
            }
        )

        // Edge-tap zones (top + bottom 5%) for re-showing the chrome. See
        // [ChromeEdgeTapZones] in ReadiumReaderContent for the full rationale —
        // the middle 90% has no overlay so the PDF fragment's own gestures
        // (swipe to change page, pinch to zoom) work without interference.
        if (onShowChrome != null) {
            ChromeEdgeTapZones(onShowChrome = { onShowChrome() })
        }
    }
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
            style = Decoration.Style.Highlight(tint = tint, isActive = false)
        )
    }
}

/**
 * Recursively searches [root] for a [WebView].
 */
private fun View.findWebView(): WebView? {
    if (this is WebView) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i)?.findWebView()?.let { return it }
        }
    }
    return null
}

/**
 * Installs [SuppressPdfSelectionActionMode] on the first [WebView] found under
 * [root]. For the current Pdfium-based PDF navigator this is normally a no-op
 * because no WebView exists in the hierarchy, but it protects against future
 * engine changes.
 */
private fun installPdfActionModeCallback(root: View) {
    val webView = root.findWebView() ?: run {
        Log.d("ReadiumPdfReaderContent", "No WebView in PDF navigator — ActionMode not applicable")
        DebugLog.info("ActionMode", "WebView not found in ${root.javaClass.simpleName}")
        return
    }
    installActionModeCallbackOnWebView(webView)
}

private fun installActionModeCallbackOnWebView(webView: WebView) {
    try {
        val method = try {
            // First try the actual runtime class (handles R2WebView overrides)
            webView.javaClass.getMethod(
                "setCustomSelectionActionModeCallback",
                ActionMode.Callback::class.java
            )
        } catch (e: NoSuchMethodException) {
            // Fallback to the public WebView class
            WebView::class.java.getMethod(
                "setCustomSelectionActionModeCallback",
                ActionMode.Callback::class.java
            )
        }
        method.invoke(webView, SuppressPdfSelectionActionMode)
        runCatching { webView.isLongClickable = false }
        runCatching { webView.setOnLongClickListener { true } }
        Log.d("ReadiumReaderContent", "Installed custom selection ActionMode callback on ${webView.javaClass.simpleName}")
        DebugLog.success("ActionMode", "Callback installed on ${webView.javaClass.simpleName}")
    } catch (e: Throwable) {
        Log.e("ReadiumReaderContent", "Failed to install ActionMode callback", e)
        DebugLog.error("ActionMode", "Failed to install callback: ${e::class.java.simpleName}: ${e.message}")
    }
}
