/**
 * Plain-JS string of the EPUB highlight overlay, inlined into the
 * EPUB iframe's `srcdoc`. Mirrors `PdfSelectionOverlay.svelte` (the
 * parent-side PDF reference) but lives inside the iframe so it has
 * direct access to the chapter DOM.
 *
 * Mounts `window.__epubHighlightOverlay` with a single
 * `render(highlights, chapterHref, currentChapterIndex)` function.
 * The parent calls it on every chapter change and whenever
 * `persistedHighlights` changes.
 *
 * For each highlight whose `pageNumber === currentChapterIndex`, the
 * overlay resolves its CFI to a DOM Range and wraps the range in a
 * `<span class="epub-hl" data-id="..." style="background: ...">`.
 * Click handlers are wired via event delegation on `document.body`
 * (in the selection script) and post
 * `{ type: 'epub-highlight-click', id, x, y, color, pageNumber }` to
 * the parent. Legacy highlights (cfi === null) are skipped silently.
 */
export const IFRAME_HIGHLIGHT_OVERLAY_SCRIPT = `
(function() {
  if (window.__epubHighlightOverlay) return; // idempotent
  // hexToRgba: now returns null on invalid input instead of falling back
  // to a default yellow. The caller (render) detects null and posts an
  // 'epub-hl-failed' message with reason: 'invalid-color'. isFinite
  // guards against hex strings that parse to NaN (e.g. '#XYZ123' would
  // produce rgba(NaN, NaN, 35, ...) under the old behavior).
  function hexToRgba(hex, alpha) {
    if (typeof hex !== 'string') return null;
    var h = hex.replace('#', '');
    if (h.length !== 6) return null;
    var r = parseInt(h.slice(0, 2), 16);
    var g = parseInt(h.slice(2, 4), 16);
    var b = parseInt(h.slice(4, 6), 16);
    if (!isFinite(r) || !isFinite(g) || !isFinite(b)) return null;
    return 'rgba(' + r + ', ' + g + ', ' + b + ', ' + (alpha == null ? 0.4 : alpha) + ')';
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
  function clearHighlights(doc) {
    var existing = doc.querySelectorAll('.epub-hl');
    for (var i = 0; i < existing.length; i++) {
      var el = existing[i];
      var parent = el.parentNode;
      if (!parent) continue;
      while (el.firstChild) parent.insertBefore(el.firstChild, el);
      parent.removeChild(el);
    }
    if (doc.body) doc.body.normalize();
  }
  function wrapRange(range, hl, rgba) {
    var span = document.createElement('span');
    span.className = 'epub-hl';
    span.setAttribute('data-id', hl.id);
    span.style.background = rgba;
    span.style.borderRadius = '2px';
    try {
      range.surroundContents(span);
    } catch (e) {
      try {
        var contents = range.extractContents();
        span.appendChild(contents);
        range.insertNode(span);
      } catch (e2) {
        console.warn('epub-hl: failed to wrap range', e2);
      }
    }
  }
  function render(highlights, chapterHref, currentChapterIndex) {
    var doc = document;
    clearHighlights(doc);
    if (!Array.isArray(highlights) || !chapterHref) return;
    for (var i = 0; i < highlights.length; i++) {
      var hl = highlights[i];
      if (!hl || hl.pageNumber !== currentChapterIndex) continue;
      if (!hl.cfi) continue; // legacy or PDF
      var range = null;
      try {
        if (window.__cfiBridge && typeof window.__cfiBridge.cfiToRange === 'function') {
          range = window.__cfiBridge.cfiToRange(hl.cfi, chapterHref, doc);
        }
      } catch (e) {
        range = null;
      }
      if (!range) {
        console.warn('epub-hl: cfi did not resolve for highlight', hl.id);
        postFailure(hl.id, 'cfi-unresolved', currentChapterIndex);
        continue;
      }
      var rgba = hexToRgba(hl.color, 0.4);
      if (rgba == null) {
        console.warn('epub-hl: invalid color for highlight', hl.id, hl.color);
        postFailure(hl.id, 'invalid-color', currentChapterIndex);
        continue;
      }
      wrapRange(range, hl, rgba);
    }
  }
  window.__epubHighlightOverlay = { render: render };
})();
`;
