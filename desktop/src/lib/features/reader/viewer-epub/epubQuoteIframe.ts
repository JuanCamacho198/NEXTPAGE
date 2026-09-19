/**
 * Plain-JS string of the EPUB paragraph-quote extractor, inlined into the
 * chapter iframe's `srcdoc` (design Decision 8, REQ-DRE-005).
 *
 * It lives here, not inside `cfiBridge.ts` / `cfiBridgeIframe.ts`: those two
 * are kept byte-for-byte equivalent and carry a lockstep gate, and the design
 * explicitly forbids extending them. The extraction runs inside the iframe
 * because the live `Range` only exists there; keeping it as a standalone
 * fragment also makes it directly unit-testable in jsdom through
 * `new Function(IFRAME_QUOTE_SCRIPT)`.
 *
 * Exposed as `window.__epubQuote.extract(range, document)` returning
 * `{ quote: string | null }`. The block element is used only to derive the
 * quote and is never returned or plumbed.
 *
 * Contract (REQ-DRE-005):
 *  - walk from `range.commonAncestorContainer` (falling back to
 *    `startContainer`) to the nearest ancestor whose computed `display` is
 *    `block`, `list-item` or `table-cell`, stopping before `document.body`;
 *  - no block ancestor -> `{ quote: null }` (never a fabricated quote);
 *  - a selection spanning several paragraphs captures the START paragraph only;
 *  - quote = collapsed `textContent`, truncation at the cap is lossy but never
 *    blocks the capture.
 */
import { MAX_QUOTE_LENGTH } from '$lib/shared/dictionary/dictionaryKey';

export const IFRAME_QUOTE_SCRIPT = `
(function() {
  if (window.__epubQuote) return; // idempotent
  var BLOCK_DISPLAYS = { block: 1, 'list-item': 1, 'table-cell': 1 };
  var MAX_QUOTE = ${MAX_QUOTE_LENGTH};

  function displayOf(doc, el) {
    var view = doc && doc.defaultView;
    if (!view || typeof view.getComputedStyle !== 'function') return '';
    try {
      return view.getComputedStyle(el).display || '';
    } catch (e) {
      return '';
    }
  }

  function isBlockElement(doc, el) {
    return !!BLOCK_DISPLAYS[displayOf(doc, el)];
  }

  // Nearest ancestor (inclusive) with a block/list-item/table-cell display,
  // stopping before document.body. Returns null when none exists.
  function nearestBlock(doc, node) {
    var body = doc && doc.body;
    while (node && node !== body) {
      if (node.nodeType === 1 && isBlockElement(doc, node)) return node;
      node = node.parentNode;
    }
    return null;
  }

  function collapse(text) {
    return String(text == null ? '' : text).replace(/\\s+/g, ' ').trim();
  }

  function extract(range, doc) {
    try {
      if (!range || !doc || !doc.body) return { quote: null };
      var startContainer = range.startContainer || null;
      var fromCommonAncestor = nearestBlock(
        doc,
        range.commonAncestorContainer || startContainer,
      );
      var fromStart = nearestBlock(doc, startContainer);

      // Cross-paragraph selection: the common ancestor can be a block wrapper
      // that spans past the selection start. The requirement is the START
      // paragraph only, so prefer the start container's block whenever it is
      // the tighter one and does not contain the selection end.
      var block = fromCommonAncestor;
      if (fromStart && fromStart !== fromCommonAncestor && !fromStart.contains(range.endContainer)) {
        block = fromStart;
      }

      if (!block) return { quote: null };
      var quote = collapse(block.textContent);
      if (quote.length > MAX_QUOTE) {
        quote = quote.slice(0, MAX_QUOTE) + '\u2026';
      }
      return { quote: quote };
    } catch (e) {
      return { quote: null };
    }
  }

  window.__epubQuote = { extract: extract };
})();
`;
