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

## Pre-flight

1. Enable the debug panel: open the in-app debug overlay (Ctrl+Shift+D
   or whatever the project's shortcut is — see AGENTS.md / config).
   Confirm the `epub` section is visible with `failedHighlightIds: []`.
2. Open the test EPUB in the reader.

## Step 1 — Bug B: cross-paragraph wrap has no whitespace bleed

1. Navigate to a chapter with at least two consecutive paragraphs.
2. Mouse-select text that **starts in the middle of p1 and ends at
   the visual start of p2** (i.e. right before the first character
   of p2's content, after the leading whitespace).
3. Click a color in Menu 1 to save the highlight.
4. **Expected**: the highlight wrap covers exactly the text you
   selected. The wrap's right edge sits flush at the visual start
   of p2 — p2's leading whitespace is **not** highlighted. No
   `data-id`-less wrap orphan is left behind.
5. **Before the fix** (regression marker): a single trailing space
   inside p2 was highlighted (the wrap "overshot" into p2's leading
   whitespace). After the fix, no such space is included.

## Step 2 — Bug B: byte-equal round-trip after page navigation

1. With the highlight from Step 1 saved, navigate away (next
   chapter) and back.
2. **Expected**: the highlight re-renders in the **same** position.
   Same start char, same end char, no shift.
3. **Before the fix** (regression marker): the highlight could shift
   forward by one character or wrap a different span.

## Step 3 — Bug C-sub1: Menu 2 only opens on real wrap click

1. With the cross-paragraph highlight from Step 1 visible, click
   **on the highlight wrap** (inside the colored span).
2. **Expected**: Menu 2 opens with the color picker / tag / note
   / delete / close controls.
3. Close Menu 2. Now click on **plain text immediately adjacent
   to the wrap** (one pixel above the top edge, or one pixel to
   the left of the left edge).
4. **Expected**: Menu 2 does **not** open. `target.closest('.epub-hl')`
   returns `null` (verifiable in DevTools).
5. **Before the fix** (regression marker): Menu 2 fired on plain-
   text clicks adjacent to the wrap because the wrap bounds
   overshot.

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
     `[epub-hl] cfi did not resolve for highlight hl-manual-test-1`
   - Followed by a `console.warn` from the parent:
     `[epub-hl] highlight failed to apply` with
     `{ id: 'hl-manual-test-1', reason: 'cfi-unresolved', pageNumber: N }`
   - The debug panel's `epub.failedHighlightIds` array now
     contains `'hl-manual-test-1'`.
2. **Before the fix** (regression marker): the failure was
   `console.warn`-only inside the iframe and invisible to the
   parent. No id ever surfaced to the debug panel.

## Step 5 — Bug A: invalid color surfaces with `reason: 'invalid-color'`

Re-run the same render call, but with a malformed color:

```js
window.__epubHighlightOverlay.render(
  [
    {
      id: 'hl-manual-test-2',
      color: '#XYZ123', // invalid hex
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
console, or grab it from the persisted highlights via
`window.__epubHighlightOverlay`.)

1. **Expected**:
   - No `rgba(NaN, NaN, ...)` is applied (the wrap either does not
     render, or renders with the fallback — but `parent.postMessage`
     still fires).
   - The console logs `[epub-hl] invalid color for highlight
hl-manual-test-2`.
   - The parent console logs `console.warn` with
     `{ reason: 'invalid-color', ... }`.
   - `debugState.epub.failedHighlightIds` now contains
     `'hl-manual-test-2'`.
2. **Before the fix** (regression marker): the wrap rendered with
   `rgba(NaN, NaN, 35, 0.4)` (a transparent broken color) and no
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
- byte-equal round-trip broken → Bug B algorithmic
