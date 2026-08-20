/**
 * Plain-JS string of the EPUB highlight overlay, inlined into the
 * EPUB iframe's `srcdoc`. Mirrors `PdfSelectionOverlay.svelte` (the
 * parent-side PDF reference) but lives inside the iframe so it has
 * direct access to the chapter DOM.
 *
 * Mounts `window.__epubHighlightOverlay` with:
 * - `render(highlights, chapterHref, currentChapterIndex)` — the
 *   parent calls it on every chapter change and whenever
 *   `persistedHighlights` changes.
 * - `hitTest(x, y)` — resolves a viewport point to the highlight whose
 *   Range contains it (used by the selection script's click handler).
 * - `rangeOverlapsHighlight(range)` — true when a selection Range
 *   overlaps any registered highlight (used to suppress Menu 1 when
 *   the user is interacting with an existing highlight).
 *
 * Rendering is **registration-based** via the CSS Custom Highlight
 * API (`CSS.highlights` + `::highlight()`): for each highlight whose
 * `pageNumber === currentChapterIndex` (where currentChapterIndex is the
 * spine index passed as 3rd arg — spine authority, not TOC position),
 * the overlay resolves its CFI
 * to a DOM Range, maps its color to the nearest canonical color,
 * groups ranges per color, and registers one `Highlight` per color
 * named `epub-hl-<label>`. The chapter DOM is NEVER mutated, so CFI
 * capture and resolution always see identical structure — this
 * eliminates the wrap-mutation bug class (second-highlight race,
 * restart drift) structurally. Legacy highlights (cfi === null) are
 * skipped silently.
 */
export const IFRAME_HIGHLIGHT_OVERLAY_SCRIPT = `
(function() {
  if (window.__epubHighlightOverlay) return; // idempotent

  // ─── Feature detection ─────────────────────────────────────────
  // The CSS Custom Highlight API shipped in Chromium 105 (WebView2
  // evergreen). When absent we render nothing (no wrap fallback) and
  // warn once at mount.
  var HAS_CSS_HIGHLIGHTS = typeof CSS !== 'undefined' && !!CSS.highlights;
  if (!HAS_CSS_HIGHLIGHTS) {
    console.warn('epub-hl: CSS Custom Highlight API (CSS.highlights) is not supported; highlights will not render');
  }

  function postFailure(id, reason, pageNumber) {
    try {
      window.parent.postMessage({
        type: 'epub-hl-failed',
        id: id,
        reason: reason,
        pageNumber: pageNumber
      }, '*');
    } catch (e) {
      console.warn('epub-hl: failed to post failure message', e);
    }
  }

  // ─── Canonical color mapping ───────────────────────────────────
  // The 5 canonical highlight colors (single source of truth in
  // highlightColors.ts). Order is meaningful: yellow is first, so the
  // strict '<' tie-break below maps exact equidistance to yellow.
  var CANONICAL_COLORS = [
    { label: 'yellow', hex: '#FACC15', r: 250, g: 204, b: 21 },
    { label: 'green', hex: '#4ADE80', r: 74, g: 222, b: 128 },
    { label: 'blue', hex: '#60A5FA', r: 96, g: 165, b: 250 },
    { label: 'purple', hex: '#C084FC', r: 192, g: 132, b: 252 },
    { label: 'orange', hex: '#FB923C', r: 251, g: 146, b: 60 }
  ];

  function parseHex(hex) {
    if (typeof hex !== 'string') return null;
    var h = hex.charAt(0) === '#' ? hex.slice(1) : hex;
    if (h.length !== 6) return null;
    var r = parseInt(h.slice(0, 2), 16);
    var g = parseInt(h.slice(2, 4), 16);
    var b = parseInt(h.slice(4, 6), 16);
    if (!isFinite(r) || !isFinite(g) || !isFinite(b)) return null;
    return { r: r, g: g, b: b };
  }

  // Map any hex to the nearest canonical color by Euclidean RGB
  // distance. Returns { status: 'invalid' } for unparseable input,
  // { status: 'canonical', label, hex } for exact canonical matches,
  // or { status: 'unknown', label, hex } for a mapped legacy color.
  function mapColor(hex) {
    var rgb = parseHex(hex);
    if (!rgb) return { status: 'invalid' };
    var best = null;
    var bestDist = Infinity;
    for (var i = 0; i < CANONICAL_COLORS.length; i++) {
      var c = CANONICAL_COLORS[i];
      var dr = rgb.r - c.r;
      var dg = rgb.g - c.g;
      var db = rgb.b - c.b;
      var dist = dr * dr + dg * dg + db * db;
      if (dist < bestDist) {
        bestDist = dist;
        best = c;
      }
    }
    var isCanonical = best && String(hex).toLowerCase() === best.hex.toLowerCase();
    return {
      status: isCanonical ? 'canonical' : 'unknown',
      label: best.label,
      hex: best.hex,
      rgb: { r: best.r, g: best.g, b: best.b }
    };
  }

  // ─── Hit testing ───────────────────────────────────────────────
  // Inclusive boundary test (D1): the caret point resolves to the
  // highlight iff start <= point <= end. comparePoint returns 1 at the
  // exact end boundary (strictly outside in Chromium), which would
  // silently miss the last character of a highlight; compensating with
  // a +1 offset tolerance is fragile (the offset may be a child index,
  // not a char count). Instead we treat the EXACT (endContainer,
  // endOffset) identity as inside, keeping the comparison exact and
  // unambiguous.
  function pointInRangeInclusive(range, container, offset) {
    var cmp = range.comparePoint(container, offset);
    if (cmp < 0) return false; // strictly before the start
    if (cmp === 0) return true; // exactly at start (or end) boundary
    // cmp === 1: at-or-after the end. Inclusive: only the exact end
    // boundary point resolves (D1 last-char case).
    return container === range.endContainer && offset === range.endOffset;
  }

  function hitTest(x, y) {
    if (!HAS_CSS_HIGHLIGHTS) return null;
    var caret = document.caretRangeFromPoint(x, y);
    if (!caret) return null;
    var container = caret.startContainer;
    var offset = caret.startOffset;
    for (var i = 0; i < registered.length; i++) {
      var entry = registered[i];
      if (pointInRangeInclusive(entry.range, container, offset)) {
        return { id: entry.id, color: entry.color, text: entry.text };
      }
    }
    return null;
  }

  // True when the selection range overlaps any registered highlight
  // (used by the selection script to skip Menu 1 — Menu 2 owns the
  // gesture). Two ranges overlap iff neither ends before the other
  // starts.
  function rangeOverlapsHighlight(range) {
    if (!HAS_CSS_HIGHLIGHTS || !range || range.collapsed) return false;
    for (var i = 0; i < registered.length; i++) {
      var r = registered[i].range;
      var rEndsBeforeSel = r.compareBoundaryPoints(Range.END_TO_START, range) < 0;
      var selEndsBeforeR = range.compareBoundaryPoints(Range.END_TO_START, r) < 0;
      if (!rEndsBeforeSel && !selEndsBeforeR) return true;
    }
    return false;
  }

  // ─── Registration-based render ─────────────────────────────────
  // Per-id side table mirroring the per-color registry: rebuilt on
  // every full re-register. Hit-testing, delete and recolor are per-id
  // operations that the color-grouped CSS.highlights registry cannot
  // express, hence the parallel Map<id, {id, range, color, text}>.
  var registered = [];
  // Chromium imposes an implementation-specific cap on registered
  // ranges per document; normal chapters are far below it. Guard +
  // log for observability (D3).
  var RANGE_CAP = 5000;

  // ─── Timing robustness ─────────────────────────────────────────
  // The parent's highlight $effect can call render() BEFORE the iframe's
  // injected cfiBridge script has executed (the srcdoc scripts run when
  // the document finishes parsing, which races the parent's reactive
  // effect). At that moment window.__cfiBridge is undefined, so every
  // CFI "fails to resolve" — a FALSE negative that produces spurious
  // epub-hl-failed cfi-unresolved and no visible highlight until the
  // iframe onload re-renders. "Sometimes works, sometimes not" depends
  // on whether the effect happens to run before or after the bridge
  // mounts. Fix: defer the render when the bridge is not ready, and
  // auto re-run it the moment the bridge appears. The bridge mounts
  // once per iframe load, so the deferred render fires at most once per
  // load and the onload re-render becomes a harmless idempotent no-op.
  var pendingRender = null;
  var bridgeReady = false;
  var pendingCheckTimer = null;
  function checkBridgeAndFlush() {
    if (bridgeReady) return;
    if (window.__cfiBridge && typeof window.__cfiBridge.cfiToRange === 'function') {
      bridgeReady = true;
      if (pendingRender) {
        var pr = pendingRender;
        pendingRender = null;
        try { render.apply(null, pr); } catch (e) { console.warn('epub-hl: deferred render failed', e); }
      }
      return;
    }
    if (pendingRender) {
      if (pendingCheckTimer) clearTimeout(pendingCheckTimer);
      pendingCheckTimer = setTimeout(checkBridgeAndFlush, 20);
    }
  }

  function render(highlights, chapterHref, currentChapterIndex) {
    if (!Array.isArray(highlights) || !chapterHref) return;
    if (!HAS_CSS_HIGHLIGHTS) return; // feature-detect: render nothing
    // Defer until the cfiBridge is mounted: resolving CFIs against an
    // unmounted bridge would flag every highlight as cfi-unresolved.
    if (!bridgeReady) {
      if (!(window.__cfiBridge && typeof window.__cfiBridge.cfiToRange === 'function')) {
        console.warn('epub-hl: render DEFERRED (bridge not mounted yet), highlights=' + highlights.length + ' chapter=' + currentChapterIndex);
        pendingRender = [highlights, chapterHref, currentChapterIndex];
        checkBridgeAndFlush();
        return;
      }
      bridgeReady = true;
    }
    var doc = document;
    var byColor = Object.create(null);
    var sideTable = [];
    var rangeCount = 0;
    var skippedNoBridge = 0;
    var skippedCollapsed = 0;
    var skippedInvalidColor = 0;
    var failedUnresolved = 0;
    var matchedChapter = 0;
    for (var i = 0; i < highlights.length; i++) {
      var hl = highlights[i];
      if (!hl || hl.pageNumber !== currentChapterIndex) {
        if (hl) console.warn('epub-hl: skip page mismatch hl '+hl.pageNumber+' vs current '+currentChapterIndex+' id='+String(hl.id).slice(0,4));
        continue;
      }
      matchedChapter++;
      if (!hl.cfi) continue; // legacy or PDF
      var range = null;
      try {
        if (window.__cfiBridge && typeof window.__cfiBridge.cfiToRange === 'function') {
          range = window.__cfiBridge.cfiToRange(hl.cfi, chapterHref, doc);
        } else {
          skippedNoBridge++;
        }
      } catch (e) {
        range = null;
      }
      if (!range) {
        console.warn('epub-hl: cfi did not resolve for highlight', hl.id, 'page=' + hl.pageNumber + ' cfi=' + hl.cfi);
        failedUnresolved++;
        postFailure(hl.id, 'cfi-unresolved', currentChapterIndex);
        continue;
      }
      // Chromium throws NotSupportedError when a collapsed range is
      // registered, so skip zero-length CFI ranges.
      if (range.collapsed) {
        console.warn('epub-hl: collapsed range skipped for highlight', hl.id);
        skippedCollapsed++;
        continue;
      }
      var mapped = mapColor(hl.color);
      if (mapped.status === 'invalid') {
        console.warn('epub-hl: invalid color for highlight', hl.id, hl.color);
        skippedInvalidColor++;
        postFailure(hl.id, 'invalid-color', currentChapterIndex);
        continue;
      }
      if (mapped.status === 'unknown') {
        console.warn('epub-hl: unknown color mapped to ' + mapped.label + ' for highlight', hl.id, hl.color);
        postFailure(hl.id, 'unknown-color', currentChapterIndex);
      }
      var list = byColor[mapped.label];
      if (!list) list = byColor[mapped.label] = [];
      list.push(range);
      rangeCount++;
      sideTable.push({
        id: hl.id,
        range: range,
        color: mapped.hex,
        text: (range.toString() || '').trim()
      });
    }
    if (rangeCount > RANGE_CAP) {
      console.warn('epub-hl: ' + rangeCount + ' ranges registered this render (cap guard ' + RANGE_CAP + ')');
    }
    // Full idempotent re-register (D6): clear then set one Highlight
    // per canonical color. Add/delete/recolor fall out naturally and
    // the DOM is never touched.
    try {
      CSS.highlights.clear();
      for (var label in byColor) {
        if (Object.prototype.hasOwnProperty.call(byColor, label)) {
          var ranges = byColor[label];
          var hlObj = new Highlight();
          for (var j = 0; j < ranges.length; j++) hlObj.add(ranges[j]);
          CSS.highlights.set('epub-hl-' + label, hlObj);
        }
      }
    } catch (e) {
      console.warn('epub-hl: failed to register highlights', e);
    }
    registered = sideTable;
    // DIAG: render summary — tells us whether the "highlight doesn't show"
    // bug is a skipped render, a failed CFI resolution, or a color skip.
    console.warn('epub-hl: render done chapter=' + currentChapterIndex +
      ' received=' + highlights.length + ' matchedChapter=' + matchedChapter +
      ' resolved=' + rangeCount + ' unresolved=' + failedUnresolved +
      ' collapsed=' + skippedCollapsed + ' invalidColor=' + skippedInvalidColor +
      ' noBridgeAtResolve=' + skippedNoBridge);
  }

  function isReady() {
    return bridgeReady && HAS_CSS_HIGHLIGHTS && !!(window.__cfiBridge && typeof window.__cfiBridge.cfiToRange === 'function');
  }

  window.__epubHighlightOverlay = {
    render: render,
    hitTest: hitTest,
    rangeOverlapsHighlight: rangeOverlapsHighlight,
    isReady: isReady
  };
})();
`;
