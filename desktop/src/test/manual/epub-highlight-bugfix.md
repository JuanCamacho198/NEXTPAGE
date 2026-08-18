# Manual Test: epub-highlight-bugfix

**Target**: Tauri dev build (`bun run tauri:dev`)
**Fixtures**: Any 2-chapter EPUB with reflowed text. Prefer one with
multi-paragraph chapters and visible leading whitespace between
paragraphs (most public-domain EPUBs work — e.g. a Project Gutenberg
title rendered by Calibre or Sigil).
**Scope**: Verify the three bugs fixed by `epub-highlight-bugfix`
land in the user-visible flow. The unit test in
`src/test/unit/epub/cfiBridge.test.ts` covers the algorithmic
side; this script covers the post-fix runtime behavior on a real
EPUB (CFI math, postMessage plumbing, debug panel mirroring).

Highlights now render through the CSS Custom Highlight API
(`CSS.highlights` + `::highlight()`): the overlay registers
CFI-derived ranges per canonical color and never mutates the chapter
DOM (no `<span class="epub-hl">` wraps). Clicks are hit-tested
against those registered ranges via `document.caretRangeFromPoint`.
The steps below re-test the original bug classes against this
registration-based renderer.

## Pre-flight

1. Enable the debug panel: open the in-app debug overlay (Ctrl+Shift+D
   or whatever the project's shortcut is — see AGENTS.md / config).
   Confirm the `epub` section is visible with `failedHighlightIds: []`.
2. Open the test EPUB in the reader.

## Step 1 — Bug B: cross-paragraph highlight has no whitespace bleed

1. Navigate to a chapter with at least two consecutive paragraphs.
2. Mouse-select text that **starts in the middle of p1 and ends at
   the visual start of p2** (i.e. right before the first character
   of p2's content, after the leading whitespace).
3. Click a color in Menu 1 to save the highlight.
4. **Expected**: the highlighted background covers exactly the text
   you selected. The highlight's right edge sits flush at the visual
   start of p2 — p2's leading whitespace is **not** highlighted.
   Rendering registers CFI ranges only; no DOM elements are added or
   removed.
5. **Before the fix** (regression marker): a single trailing space
   inside p2 was highlighted (the old wrap renderer "overshot" into
   p2's leading whitespace). After the fix, no such space is included.

## Step 2 — Bug B: byte-equal round-trip after page navigation

1. With the highlight from Step 1 saved, navigate away (next
   chapter) and back.
2. **Expected**: the highlight re-renders in the **same** position.
   Same start char, same end char, no shift.
3. **Before the fix** (regression marker): the highlight could shift
   forward by one character or render a different span.

## Step 3 — Bug C-sub1: Menu 2 only opens on a real highlight hit

**This is the hit-test acceptance test**: with registration-based
rendering there is no element to click on, so the reader resolves
clicks with `document.caretRangeFromPoint(x, y)` and an inclusive
boundary check against the registered ranges (design D1).

1. With the cross-paragraph highlight from Step 1 visible, click
   **on the highlighted text** (inside the colored region).
2. **Expected**: Menu 2 opens with the color picker / tag / note
   / delete / close controls.
3. Close Menu 2. Now click on **plain text immediately adjacent
   to the highlighted region** (one pixel above the top edge, or
   one pixel to the left of the left edge).
4. **Expected**: Menu 2 does **not** open and no `epub-highlight-click`
   postMessage is emitted. Optional DevTools check in the iframe
   console: `window.__epubHighlightOverlay.hitTest(x, y)` returns
   `null` at the adjacent point, and the highlight's
   `{ id, color, text }` inside the colored region.
5. **Before the fix** (regression marker): Menu 2 fired on plain-
   text clicks adjacent to the highlight because the old wrap
   bounds overshot.

## Step 4 — Bug A: simulated CFI-resolution failure surfaces in debug panel

This step simulates a `cfiToRange` failure without breaking the
book. Open the chapter in DevTools and run:

```js
window.__epubHighlightOverlay.render(
  [
    {
      id: 'hl-manual-test-1',
      color: '#FACC15',
      pageNumber: <current chapter index, 0-based>,
      cfi: 'epubcfi(/6/1!/999/999,/1:0,/1:0)', // unresolvable
    },
  ],
  <current chapter href>,
  <current chapter index, 0-based>
);
```

(`<current chapter index>` is visible in the EpubControls footer
as `page X of Y` — convert to 0-based. `<current chapter href>`
is the href of the chapter currently rendered; you can grab it
from `iframeEl.contentDocument.querySelector('a')` or via
`iframeEl.contentDocument.title`.)

1. **Expected**:
   - The browser console logs:
     `epub-hl: cfi did not resolve for highlight hl-manual-test-1`
   - Followed by a `console.warn` from the parent:
     `epub-hl: highlight failed to apply` with
     `{ id: 'hl-manual-test-1', reason: 'cfi-unresolved', pageNumber: N }`
   - The debug panel's `epub.failedHighlightIds` array now
     contains `'hl-manual-test-1'`.
2. **Before the fix** (regression marker): the failure was
   `console.warn`-only inside the iframe and invisible to the
   parent. No id ever surfaced to the debug panel.

## Step 5 — Bug A: unknown color maps to nearest canonical + surfaces in debug panel

Re-run the same render call, but with a non-canonical color:

```js
window.__epubHighlightOverlay.render(
  [
    {
      id: 'hl-manual-test-2',
      color: '#F9A8D4', // soft pink — not one of the 5 canonical colors
      pageNumber: <N>,
      cfi: '<a known-good CFI from the current chapter>',
    },
  ],
  <current chapter href>,
  <N>
);
```

(To grab a known-good CFI, select any text in the chapter and read
the `cfi` field from the `epub-selection` postMessage in the
console.)

Non-canonical hex colors are mapped to the nearest canonical color
via Euclidean RGB distance (design D2) — `#F9A8D4` maps to purple —
and the highlight still renders under that canonical color.

1. **Expected**:
   - The highlight renders, tinted with the nearest canonical color
     (purple for `#F9A8D4`; the registry name is `epub-hl-purple`).
   - The console logs `epub-hl: unknown color mapped to purple for
highlight hl-manual-test-2`.
   - The parent console logs `console.warn` with
     `{ reason: 'unknown-color', ... }`.
   - `debugState.epub.failedHighlightIds` now contains
     `'hl-manual-test-2'`.
2. **Variant — truly invalid color**: with an unparseable hex (e.g.
   `#XYZ123`), the highlight is **skipped** and the reason is
   `invalid-color` instead (same console + debug-panel flow).
3. **Before the fix** (regression marker): an unknown color rendered
   as a transparent broken fill (`rgba(NaN, NaN, ...)`) and no
   failure message surfaced.

## Step 6 — Bug A: backend save failure mirrors to debug panel

This step exercises the parent-side mirror on
`saveHighlight` reject. The simplest way to provoke a save
rejection is to delete the SQLite row's parent highlight first
(via a color update race) or to mock the Tauri `saveHighlight`
command in DevTools. Easiest: temporarily patch the Tauri
command:

```js
// In DevTools console, before saving a new highlight:
window.__TAURI_INTERNALS__ = window.__TAURI_INTERNALS__ || {};
// Force the next saveHighlight to reject:
const origInvoke = window.__TAURI_INTERNALS__.invoke;
window.__TAURI_INTERNALS__.invoke = (cmd, args) => {
  if (cmd === 'save_highlight' || cmd === 'saveHighlight') {
    return Promise.reject(new Error('manual test forced reject'));
  }
  return origInvoke(cmd, args);
};
```

(If the project uses a different Tauri API surface, use
`@tauri-apps/api/core` `invoke` directly — the goal is to make
the next `saveHighlight` reject.)

1. Now select any text in the EPUB, pick a color in Menu 1.
2. **Expected**:
   - The highlight does **not** persist (no row in SQLite; the
     next chapter re-render drops it).
   - The console logs `console.warn('Failed to save highlight:', ...)`.
   - `debugState.epub.failedHighlightIds` now contains the new
     highlight's UUID.
3. Restore the Tauri invoke patch:
   `delete window.__TAURI_INTERNALS__.invoke;` (or close/reopen
   the dev window).

## Step 7 — Lockstep smoke (iframe plain-JS byte-for-byte)

The plain-JS in `cfiBridgeIframe.ts` is the production code path
for the iframe; the TS in `cfiBridge.ts` is what the unit test
exercises. The lockstep is enforced by code review + the
`// LOCKSTEP: see cfiBridge.test.ts "cross-paragraph round-trip"`
comment in both files. To smoke-test the iframe path:

1. Open DevTools while a chapter is loaded.
2. Grab a known text-node position in the iframe:
   ```js
   const t = iframeEl.contentDocument.querySelector('p').firstChild;
   ```
3. Build a Range in the iframe:
   ```js
   const r = iframeEl.contentDocument.createRange();
   r.setStart(t, 0);
   r.setEnd(t, 5);
   ```
4. Round-trip:
   ```js
   const cfi = iframeEl.contentWindow.__cfiBridge.rangeToCFI(
     r,
     iframeEl.contentDocument.querySelector('a')?.getAttribute('href') || 'OEBPS/Text/ch1.xhtml',
     iframeEl.contentDocument,
   );
   const restored = iframeEl.contentWindow.__cfiBridge.cfiToRange(
     cfi,
     iframeEl.contentDocument.querySelector('a')?.getAttribute('href') || 'OEBPS/Text/ch1.xhtml',
     iframeEl.contentDocument,
   );
   restored.toString(); // should equal the first 5 chars of the <p>
   ```
5. **Expected**: `restored.toString()` matches the first 5 chars
   of the `<p>`. If the iframe-side algorithm has drifted from
   the TS-side, this round-trip will fail or produce wrong text.
6. **Before the fix** (regression marker): any text-node range
   near a paragraph boundary could shift by one character.

## Pass criteria

All 7 steps produce the expected behavior. If any step fails,
re-run with the parent's DevTools console open and capture the
warning payloads — the `reason` field tells you which bug class
regressed:

- `reason: 'cfi-unresolved'` → Bug A plumbing
- `reason: 'invalid-color'` → Bug C-sub1 plumbing
- `reason: 'unknown-color'` → color mapping (design D2)
- byte-equal round-trip broken → Bug B algorithmic
