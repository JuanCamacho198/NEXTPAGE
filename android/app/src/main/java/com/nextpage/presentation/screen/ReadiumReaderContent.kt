@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.nextpage.presentation.screen

import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.nextpage.debug.DebugDual
import com.nextpage.debug.DebugEvent
import com.nextpage.debug.DebugLog
import com.nextpage.debug.DebugStateHolder
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.presentation.screen.readium.findWebView
import com.nextpage.presentation.screen.readium.highlightsToDecorations
import com.nextpage.presentation.screen.readium.installActionModeCallback
import com.nextpage.presentation.screen.readium.toEpubPreferences
import com.nextpage.presentation.viewmodel.CfiMigrator
import com.nextpage.presentation.viewmodel.ReaderViewModel
import org.readium.r2.shared.publication.Link
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
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

private const val TAG = "ReadiumReaderContent"
private const val ESTIMATED_CHAPTER_CHARS_FALLBACK = 10000

/**
 * [ActionMode.Callback] that suppresses Readium's default selection toolbar
 * (the native Copy / Share / Translate / overflow floating bar).
 *
 * Returning `false` from [onCreateActionMode] prevents the ActionMode from
 * being created at all, so only our custom [SelectionOverlay] is shown.
 * Copy/Share remain available through the custom menu.
 */
// SuppressSelectionActionMode moved to readium/ReadiumViewUtils.kt

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
    initialLocator: Locator? = null,
    inspectHighlightsHtmlTrigger: SharedFlow<Unit> = MutableSharedFlow(),
    logWebViewTreeTrigger: SharedFlow<Unit> = MutableSharedFlow(),
    onShowChrome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current as FragmentActivity
    val fragmentManager = remember { context.supportFragmentManager }
    val containerId = remember { View.generateViewId() }
    // Coroutine scope used by the decoration listener to probe
    // selectable.currentSelection() (a suspend function) when it can't
    // decide synchronously whether the user is creating a new selection
    // or tapping an existing highlight.
    val tapProbeScope = rememberCoroutineScope()

    // Create the EpubNavigatorFactory — Readium 3.x pattern
    val navigatorFactory = remember(publication, navigatorConfig) {
        EpubNavigatorFactory(publication, navigatorConfig)
    }

    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var containerReady by remember { mutableStateOf(false) }
    // Keep highlights fresh inside the DisposableEffect so the decoration
    // listener always sees the latest list — the effect only restarts when
    // navigatorFragment changes, so without this it would capture a stale
    // empty list.
    val latestHighlights by rememberUpdatedState(highlights)

    // ── Reset container readiness when the publication changes ──
    // We also remove the stale fragment from the previous book here,
    // BEFORE resetting containerReady. The FragmentContainerView is
    // already attached (the AndroidView is in the tree), so the
    // OnAttachStateChangeListener that used to gate containerReady
    // would not refire on subsequent loads — that's the original bug.
    // Instead, onGloballyPositioned (see the AndroidView below) sets
    // containerReady = true on the next layout pass, which is triggered
    // by removing/adding fragments or by recomposition.
    LaunchedEffect(publication) {
        val tag = "ReadiumNavigator"
        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            fragmentManager.commit { remove(existing) }
            fragmentManager.executePendingTransactions()
        }
        containerReady = false
    }

    // ── Commit EpubNavigatorFragment ──────────────────────────────
    // Keyed on BOTH publication and containerReady so the effect re-fires
    // when a new book is loaded (publication changes) and when the
    // container becomes ready. The stale fragment from the previous load
    // is removed in the publication-keyed LaunchedEffect above; here we
    // just create the new one once the container is laid out
    // (containerReady = true from onGloballyPositioned).
    LaunchedEffect(publication, containerReady) {
        if (!containerReady) return@LaunchedEffect
        val tag = "ReadiumNavigator"
        val resolvedLocator = initialLocator
            ?: publication.readingOrder.firstOrNull()?.let {
                publication.locatorFromLink(it)
            }
        if (resolvedLocator == null) return@LaunchedEffect
        // Note: the native ActionMode (Copy/Share/Translate) is suppressed
        // by installing [SuppressSelectionActionMode] on the underlying
        // WebView once the fragment view is created (see below). In newer
        // Readium versions this can be done via fragment configuration,
        // but 3.2.0 does not expose that hook.
        val factory = navigatorFactory.createFragmentFactory(
            initialLocator = resolvedLocator
        )
        fragmentManager.fragmentFactory = factory
        fragmentManager.commit {
            add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag)
        }
        delay(200)
        navigatorFragment = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment
    }

    // ── Suppress native ActionMode on the WebView ─────────────────
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        // Retry briefly until the fragment view is ready.
        repeat(20) {
            val view = frag.view
            if (view != null) {
                installActionModeCallback(view)
                DebugStateHolder.setActionModeInstalled(true)
                return@LaunchedEffect
            }
            delay(50)
        }
        Log.d("ReadiumReaderContent", "WebView not ready for action-mode suppression")
        DebugLog.warn("Readium", "WebView not ready for action-mode suppression after 1s")
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect currentLocator from EpubNavigatorFragment", e)
        }
    }

    // ── currentSelection (poll + diff + debug) → ViewModel ──────
    // DEBUG: run `adb logcat -s SelectionDebug` to trace the pipeline
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        Log.d("SelectionDebug", "navigatorFragment acquired, casting to SelectableNavigator...")
        val selectable = frag as? SelectableNavigator
        if (selectable == null) {
            Log.e("SelectionDebug", "CAST FAILED: EpubNavigatorFragment is NOT a SelectableNavigator")
            DebugLog.error("Readium", "CAST FAILED: EpubNavigatorFragment is NOT a SelectableNavigator")
            return@LaunchedEffect
        }
        Log.d("SelectionDebug", "CAST OK: SelectableNavigator=${selectable.hashCode()}")
        DebugLog.info("Readium", "SelectableNavigator acquired (hash=${selectable.hashCode()})")
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
                DebugLog.error("Readium", "currentSelection() threw: ${e::class.simpleName}: ${e.message}")
                null
            }
            if (pollCount % 10 == 0) {
                DebugLog.info(
                    "Readium",
                    "Poll #$pollCount: ${if (sel != null) "selection present" else "no selection"}"
                )
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

    // ── Highlight tap → custom menu (DecorableNavigator.Listener) ─
    // Registers a listener so tapping an existing highlight opens our
    // FloatingContextMenu anchored to the highlight rect, instead of doing
    // nothing.
    //
    // Guard: if the user is currently creating a NEW text selection (long-press
    // + drag) that happens to overlap a decoration span, Readium fires
    // onDecorationActivated. We must NOT treat that as a highlight tap — the
    // polling loop will resolve the new selection correctly. Only proceed when
    // no selection is being created.
    DisposableEffect(navigatorFragment) {
        val frag = navigatorFragment
        val decorable = frag as? DecorableNavigator
        val selectable = frag as? SelectableNavigator
        val listener = if (decorable != null) {
            object : DecorableNavigator.Listener {
                override fun onDecorationActivated(
                    event: DecorableNavigator.OnActivatedEvent
                ): Boolean {
                    val rectString = event.rect?.toString() ?: "null"
                    DebugLog.info(
                        "Readium",
                        "onDecorationActivated: group=${event.group}, id=${event.decoration.id}, rect=$rectString"
                    )
                    // Diagnostic instrumentation: record every activation,
                    // regardless of whether the group matches.
                    DebugStateHolder.recordDecorationEvent(
                        event.decoration.id,
                        event.group,
                        event.rect
                    )
                    // Only react to our own highlight group.
                    if (event.group != DECORATION_GROUP) {
                        DebugLog.warn("Readium", "Decoration group mismatch (got ${event.group})")
                        return false
                    }
                    // If the user is creating a new text selection that happens
                    // to touch a decoration span, this is a long-press, not a
                    // highlight tap. Defer to the polling loop which will set
                    // the correct New / Existing state.
                    //
                    // currentSelection() is suspend, so we probe it on the
                    // composable scope after a short delay. If by then a
                    // selection is being created, skip the highlight tap.
                    val rect: RectF = event.rect ?: return false
                    val highlight = latestHighlights.firstOrNull { it.id == event.decoration.id }
                    if (highlight == null) {
                        val knownIds = latestHighlights.map { it.id }
                        DebugLog.warn(
                            "Readium",
                            "onDecorationActivated: no highlight found for id=${event.decoration.id} — known IDs: $knownIds"
                        )
                        return false
                    }
                    DebugStateHolder.recordHighlightActivation(event.decoration.id, rectString)
                    if (selectable != null) {
                        tapProbeScope.launch {
                            try {
                                delay(80)
                                val currentSel = try {
                                    selectable.currentSelection()
                                } catch (_: Throwable) { null }
                                if (currentSel != null) {
                                    DebugLog.info(
                                        "Readium",
                                        "onDecorationActivated SKIPPED: user is creating a new selection (id=${event.decoration.id})"
                                    )
                                    return@launch
                                }
                            } catch (_: Throwable) {
                                // Probe failed — fall through and treat as a tap
                            }
                            viewModel.onHighlightTapped(highlight, rect)
                        }
                        return true
                    }
                    viewModel.onHighlightTapped(highlight, rect)
                    return true
                }
            }
        } else null

        if (decorable != null && listener != null) {
            decorable.addDecorationListener(DECORATION_GROUP, listener)
            DebugStateHolder.setHighlightListenerRegistered(true)
            DebugStateHolder.setListenerRegistered(true)
            DebugLog.info("Readium", "Decoration listener registered for group=$DECORATION_GROUP")
        } else {
            DebugStateHolder.setHighlightListenerRegistered(false)
            DebugStateHolder.setListenerRegistered(false)
        }
        onDispose {
            if (decorable != null && listener != null) {
                decorable.removeDecorationListener(listener)
                DebugStateHolder.setHighlightListenerRegistered(false)
                DebugStateHolder.setListenerRegistered(false)
                DebugLog.info("Readium", "Decoration listener removed")
            }
        }
    }

    // ── Force a re-apply of decorations after the listener is registered ─
    // Hypothesis: Readium fires onDecorationActivated only when decorations
    // are applied/changed AFTER the listener is registered. The standard
    // applyDecorations LaunchedEffect below handles the high-level sync; this
    // separate one-shot kick is a defensive nudge to trigger activation on
    // existing decorations once the listener is live.
    //
    // Re-apply with a short retry loop: when the reader is reopened the
    // highlights StateFlow may still be empty for a few frames while Room
    // emits the persisted list, so a single 100ms kick can push 0 decorations
    // and the decorations are never painted. Retrying until highlights arrive
    // (or a timeout) guarantees the last-known decorations are applied.
    // Publication readingOrder used for epubcfi fallback; recompose when publication changes
    val readingOrder = remember(publication) { publication.readingOrder }

    LaunchedEffect(navigatorFragment, publication) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        val decorable = frag as? DecorableNavigator ?: return@LaunchedEffect
        delay(100)
        repeat(10) {
            val currentDecorations = highlightsToDecorations(highlights, readingOrder, publication)
            if (currentDecorations.isNotEmpty() || highlights.isNotEmpty()) {
                decorable.applyDecorations(currentDecorations, DECORATION_GROUP)
                DebugLog.info(
                    "Readium",
                    "Post-listener re-apply: pushed ${currentDecorations.size} decorations"
                )
                DebugDual.logHighlightApplied(currentDecorations.size)
                DebugDual.d(DebugDual.TAG_SYNC, "Post-listener re-apply dual log count=${currentDecorations.size}")
                DebugStateHolder.recordApplied(currentDecorations.size)
                return@LaunchedEffect
            }
            delay(200)
        }
        DebugLog.info("Readium", "Post-listener re-apply: gave up waiting for highlights (still empty)")
    }

    // ── Decoration sync (highlights → decorations) ────────────────
    // Always re-apply (even when empty) — ensures deleted highlights are cleared.
    // Previous early-return on empty left stale decorations when a highlight was deleted
    // (local Flow emitted empty after soft-delete but we skipped clear). Now we always
    // call applyDecorations so tombstoned highlights disappear immediately.
    LaunchedEffect(highlights, navigatorFragment, publication) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        val decorable = frag as? DecorableNavigator ?: return@LaunchedEffect
        val decorations = highlightsToDecorations(highlights, readingOrder, publication)
        if (highlights.isEmpty()) {
            DebugLog.info("Readium", "Decoration sync: clearing decorations (highlights empty)")
            decorable.applyDecorations(emptyList(), DECORATION_GROUP)
            DebugDual.logHighlightApplied(0)
            DebugStateHolder.recordApplied(0)
            return@LaunchedEffect
        }
        DebugLog.info("Readium", "Decoration sync: pushing ${decorations.size} decorations")
        DebugDual.d(DebugDual.TAG_SYNC, "Decoration sync: pushing ${decorations.size} decorations via ${if (readingOrder.isNotEmpty()) "readingOrder(${readingOrder.size})" else "no-order"}")
        decorable.applyDecorations(decorations, DECORATION_GROUP)
        DebugDual.logHighlightApplied(decorations.size)
        DebugStateHolder.recordApplied(decorations.size)
    }

    // ── Settings sync (readerSettings → EpubPreferences) ──────────
    // Debounced 300ms to avoid rapid-setting flicker (D3).
    LaunchedEffect(readerSettings) {
        delay(300)
        val frag = navigatorFragment ?: return@LaunchedEffect
        try {
            val prefs = readerSettings.toEpubPreferences()
            frag.submitPreferences(prefs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit Readium reader preferences", e)
        }
    }

    // ── Diagnostic: Inspect highlights HTML ──────────────────────
    // Runs a JS query that collects every <span>/<a>/<mark> with a non-transparent
    // backgroundColor, then logs the tag, computed bg, attributes, and first 50
    // chars of text. Triggered from the debug panel.
    LaunchedEffect(navigatorFragment) {
        inspectHighlightsHtmlTrigger.collect {
            val frag = navigatorFragment
            if (frag == null) {
                DebugLog.warn("InspectHL", "No navigator fragment available")
                return@collect
            }
            val js = """
                (function(){
                    var results = [];
                    var spans = document.querySelectorAll('span, a, mark');
                    for (var i = 0; i < spans.length; i++) {
                        var el = spans[i];
                        var style = window.getComputedStyle(el);
                        var bg = style.backgroundColor;
                        if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
                            var attrs = {};
                            for (var j = 0; j < el.attributes.length; j++) {
                                var a = el.attributes[j];
                                attrs[a.name] = a.value;
                            }
                            results.push({tag: el.tagName, bg: bg, attrs: attrs, text: el.textContent.substring(0, 50)});
                        }
                    }
                    return JSON.stringify(results);
                })()
            """.trimIndent()
            try {
                val result = frag.evaluateJavascript(js)
                if (result.isNullOrBlank()) {
                    DebugLog.info("InspectHL", "Found 0 highlighted elements (empty result)")
                    return@collect
                }
                // Strip surrounding quotes that WebView wraps on the result.
                val trimmed = result.trim().removeSurrounding("\"")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                val arr = JSONArray(trimmed)
                val count = arr.length()
                DebugLog.info("InspectHL", "Found $count highlighted elements")
                for (i in 0 until count) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val tag = obj.optString("tag")
                    val bg = obj.optString("bg")
                    val text = obj.optString("text")
                    val attrs = obj.optJSONObject("attrs")
                    val attrsStr = attrs?.toString() ?: "{}"
                    DebugLog.info(
                        "InspectHL",
                        "tag=$tag, bg=$bg, attrs=$attrsStr, text=$text"
                    )
                }
            } catch (t: Throwable) {
                DebugLog.error("InspectHL", "Failed: ${t::class.simpleName}: ${t.message}")
            }
        }
    }

    // ── Diagnostic: Log WebView view hierarchy ──────────────────
    // Traverses the WebView's view tree and logs every View with class name,
    // id, visibility, and clickable state. Long trees are split into multiple
    // log entries (30 lines per chunk) to keep each line bounded.
    LaunchedEffect(navigatorFragment) {
        logWebViewTreeTrigger.collect {
            val frag = navigatorFragment
            val rootView = frag?.view
            if (rootView == null) {
                DebugLog.warn("WebViewTree", "No fragment view available")
                return@collect
            }
            val webView = rootView.findWebView()
            if (webView == null) {
                DebugLog.warn("WebViewTree", "No WebView found in fragment hierarchy")
                return@collect
            }
            val sb = StringBuilder()
            fun dumpView(v: View, depth: Int) {
                sb.append("  ".repeat(depth))
                    .append(v::class.java.simpleName)
                    .append(" id=").append(v.id)
                    .append(" visible=").append(v.visibility)
                    .append(" clickable=").append(v.isClickable)
                    .append('\n')
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        val child = v.getChildAt(i) ?: continue
                        dumpView(child, depth + 1)
                    }
                }
            }
            dumpView(webView, 0)
            val full = sb.toString()
            val lines = full.lines()
            DebugLog.info("WebViewTree", "Dumped ${lines.size} lines from WebView root")
            val chunkSize = 30
            if (lines.size <= chunkSize) {
                DebugLog.info("WebViewTree", full.trimEnd())
            } else {
                var chunkIndex = 0
                var i = 0
                while (i < lines.size) {
                    val end = (i + chunkSize).coerceAtMost(lines.size)
                    val chunk = lines.subList(i, end).joinToString("\n")
                    DebugLog.info("WebViewTree", "[chunk $chunkIndex]\n$chunk")
                    chunkIndex++
                    i = end
                }
            }
        }
    }

    // ── navigateToLocator event flow → navigator.go() (SEA-2) ────
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        viewModel.navigateToLocator.collect { locator ->
            try {
                frag.go(locator, animated = false)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to navigate Readium navigator to locator", e)
            }
        }
    }

    // ── Clear WebView selection when user picks color / copies ─────
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        viewModel.clearSelectionEvent.collect {
            try {
                frag.evaluateJavascript(
                    "(function(){var s=window.getSelection();if(s)s.removeAllRanges();})()"
                )
                Log.d("SelectionDebug", "WebView selection cleared via JS")
            } catch (e: Throwable) {
                Log.e("SelectionDebug", "Failed to clear WebView selection", e)
            }
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

    // Keep latest onShowChrome reachable from the long-lived pointerInput
    // without restarting the gesture detector on every recomposition.
    val currentOnShowChrome by rememberUpdatedState(onShowChrome)

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                FragmentContainerView(ctx).apply {
                    id = containerId
                }
            },
            modifier = Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                // Mark the container ready on the first layout pass. The
                // FragmentContainerView is already attached when this fires,
                // so this is more reliable than OnAttachStateChangeListener
                // (which only fires on attach/detach transitions and would
                // never refire on subsequent book loads — the original bug).
                if (!containerReady) containerReady = true
                viewModel.onReadiumViewportChanged(coordinates.size.height, coordinates.size.width)
            }
        )
        // Edge-tap zones (top + bottom 5%) for re-showing the chrome.
        // The middle 90% has NO overlay, so the WebView receives touches
        // natively — long-press for text selection, scroll, link taps all
        // work without interference. See [ChromeEdgeTapZones] for the
        // implementation shared with the PDF reader.
        if (currentOnShowChrome != null) {
            ChromeEdgeTapZones(onShowChrome = { currentOnShowChrome?.invoke() })
        }
    }
}

