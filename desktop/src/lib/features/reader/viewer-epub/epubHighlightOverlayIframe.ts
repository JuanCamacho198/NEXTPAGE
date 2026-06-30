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
 *
 * The render is **incremental**: it tracks the set of currently
 * rendered highlight IDs in `renderedIds` and only adds / updates /
 * removes the delta on each call. This is critical for CFI validity:
 * a "clear everything then re-render everything" strategy would
 * unwrap every existing highlight, then call body.normalize() to
 * merge adjacent text nodes, which would invalidate the CFIs of
 * highlights that the user JUST created (their CFIs were captured
 * against the wrapped DOM). Symptom: new highlights became invisible
 * until the user left the book and came back. The incremental diff
 * avoids touching wraps that are still valid.
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
  function unwrap(el) {
    var parent = el.parentNode;
    if (!parent) return;
    while (el.firstChild) parent.insertBefore(el.firstChild, el);
    parent.removeChild(el);
  }
  function wrapRange(range, hl, rgba) {
    // Fast path: the range is a subset of a single text node.
    // surroundContents works directly and produces valid HTML.
    if (range.startContainer === range.endContainer && range.startContainer.nodeType === 3) {
      var singleSpan = document.createElement('span');
      singleSpan.className = 'epub-hl';
      singleSpan.setAttribute('data-id', hl.id);
      singleSpan.style.background = rgba;
      singleSpan.style.borderRadius = '2px';
      try { range.surroundContents(singleSpan); }
      catch (e) { console.warn('epub-hl: failed to wrap range', e); }
      return;
    }
    // Slow path: the range crosses element boundaries or contains block
    // elements (e.g. the user selected across multiple <p>s, or selected
    // an entire <p>). surroundContents throws when the range contains
    // non-text nodes, and the previous fallback (extractContents + wrap
    // the extracted fragment in a <span>) produced invalid HTML of the
    // form <span class="epub-hl"><p>...</p></span> -- an inline element
    // containing a block element. The browser auto-corrects this on the
    // next render, which (a) breaks the visual highlight and (b) shifts
    // child indices of the parent, invalidating CFIs for OTHER
    // highlights in the same chapter (causing
    // 'epub-cfi: text terminus did not resolve' on subsequent renders).
    //
    // Fix: collect every text node within the range FIRST, then wrap
    // each text node's in-range portion with its own <span>. Block
    // elements (<p>, <br>, etc.) stay in place; the wrap goes INSIDE
    // the <p> around the text nodes, which is valid HTML.
    var walker = document.createTreeWalker(
      range.commonAncestorContainer,
      NodeFilter.SHOW_TEXT,
      { acceptNode: function (node) {
        return range.intersectsNode(node) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      } }
    );
    var textNodes = [];
    var tn = walker.nextNode();
    while (tn) { textNodes.push(tn); tn = walker.nextNode(); }
    for (var i = 0; i < textNodes.length; i++) {
      var node = textNodes[i];
      var startOffset = (node === range.startContainer) ? range.startOffset : 0;
      var endOffset = (node === range.endContainer) ? range.endOffset : node.nodeValue.length;
      if (endOffset <= startOffset) continue;
      var nodeRange = document.createRange();
      nodeRange.setStart(node, startOffset);
      nodeRange.setEnd(node, endOffset);
      var span = document.createElement('span');
      span.className = 'epub-hl';
      span.setAttribute('data-id', hl.id);
      span.style.background = rgba;
      span.style.borderRadius = '2px';
      try { nodeRange.surroundContents(span); }
      catch (e) { console.warn('epub-hl: failed to wrap text node', e); }
    }
  }
  // Incremental render: diff against previously-rendered IDs and apply
  // only the delta. This is the fix for the CFI invalidation bug.
  //
  // Old behavior: clearHighlights() + re-render ALL highlights. The clear
  // step removed every wrap, then body.normalize() merged adjacent text
  // nodes. The re-render tried to re-resolve every CFI on the now-flat
  // DOM, but each CFI was originally captured on the DOM with the
  // existing wraps in place. As soon as the user added a SECOND
  // highlight, the clear step destroyed the wrap for the first one, the
  // re-render restored it on the merged DOM, and the CFI of the SECOND
  // highlight (captured against the wrapped DOM) failed to resolve.
  // Symptom: the new highlight was invisible until the user left the
  // book and came back.
  //
  // New behavior: never touch wraps that are still valid. Remove only
  // the wraps of highlights that were deleted (by ID), add only the
  // wraps of highlights that are new, and update the background of
  // existing wraps whose color changed. CFIs of untouched highlights
  // remain valid because we never mutate the text nodes around them.
  var renderedIds = Object.create(null);
  function render(highlights, chapterHref, currentChapterIndex) {
    if (!Array.isArray(highlights) || !chapterHref) return;
    var doc = document;
    // 1. Index the desired set by ID for the current chapter.
    var desired = Object.create(null);
    var order = [];
    for (var i = 0; i < highlights.length; i++) {
      var hl = highlights[i];
      if (!hl || hl.pageNumber !== currentChapterIndex) continue;
      if (!hl.cfi) continue; // legacy or PDF
      desired[hl.id] = hl;
      order.push(hl.id);
    }
    // 2. Remove wraps for IDs that are no longer in the desired set.
    for (var renderedId in renderedIds) {
      if (Object.prototype.hasOwnProperty.call(renderedIds, renderedId) && !desired[renderedId]) {
        var el = doc.querySelector('.epub-hl[data-id="' + renderedId + '"]');
        if (el) unwrap(el);
        delete renderedIds[renderedId];
      }
    }
    // 3. Add or update wraps for every desired highlight.
    for (var k = 0; k < order.length; k++) {
      var id = order[k];
      var entry = desired[id];
      var existing = doc.querySelector('.epub-hl[data-id="' + id + '"]');
      var rgba = hexToRgba(entry.color, 0.4);
      if (rgba == null) {
        console.warn('epub-hl: invalid color for highlight', id, entry.color);
        postFailure(id, 'invalid-color', currentChapterIndex);
        continue;
      }
      if (existing) {
        // Already rendered: just update the color (cheap and safe).
        existing.style.background = rgba;
        renderedIds[id] = true;
        continue;
      }
      // New: resolve CFI and wrap. We only touch this one node path, so
      // other highlights' CFIs stay valid.
      var range = null;
      try {
        if (window.__cfiBridge && typeof window.__cfiBridge.cfiToRange === 'function') {
          range = window.__cfiBridge.cfiToRange(entry.cfi, chapterHref, doc);
        }
      } catch (e) {
        range = null;
      }
      if (!range) {
        console.warn('epub-hl: cfi did not resolve for highlight', id);
        postFailure(id, 'cfi-unresolved', currentChapterIndex);
        continue;
      }
      wrapRange(range, entry, rgba);
      renderedIds[id] = true;
    }
  }
  window.__epubHighlightOverlay = { render: render };
})();
`;
