import { describe, expect, it, vi } from 'vitest';
import { JSDOM } from 'jsdom';
import { IFRAME_HIGHLIGHT_OVERLAY_SCRIPT } from '$lib/features/reader/viewer-epub/epubHighlightOverlayIframe';
import { IFRAME_CFI_BRIDGE_SCRIPT } from '$lib/features/reader/viewer-epub/cfiBridgeIframe';

// ────────────────────────────────────────────────────────────────────
// PR 2 full test suite for the CSS Custom Highlight API overlay.
//
// PR 1 rewrote the overlay to registration-based rendering via
// CSS.highlights + ::highlight(). jsdom does NOT implement the CSS
// Custom Highlight API (no `CSS.highlights`, no `Highlight`, and no
// `document.caretRangeFromPoint`), so this suite installs a faithful
// mock BEFORE evaluating the overlay script:
//   - `Highlight` mock capturing `.ranges` (added via `.add()`).
//   - `HighlightRegistry` mock (Map-like: set/delete/clear/get).
//   - a `document.caretRangeFromPoint` stub for hit-test boundary tests.
// The REAL cfiBridge iframe script is also evaluated so CFIs resolve to
// genuine jsdom Ranges (real comparePoint/collapsed/toString/text), which
// is far closer to production than hand-built fake ranges.
// ────────────────────────────────────────────────────────────────────

const FIXTURE_HTML = `<!DOCTYPE html>
<html>
  <head><meta charset="utf-8"><title>Chapter</title></head>
  <body>
    <p>Primer parrafo. Contiene algo de texto para resaltar.</p>
    <p>Segundo parrafo. Tambien tiene contenido relevante.</p>
    <p>Tercer parrafo. Y este completa la seleccion.</p>
  </body>
</html>`;

const CHAPTER_HREF = 'OEBPS/Text/ch1.xhtml';

interface HighlightLike {
  ranges: Range[];
  add(range: Range): void;
}

class HighlightRegistryMock {
  private store = new Map<string, HighlightLike>();
  set(name: string, hl: HighlightLike): void {
    this.store.set(name, hl);
  }
  delete(name: string): boolean {
    return this.store.delete(name);
  }
  clear(): void {
    this.store.clear();
  }
  get(name: string): HighlightLike | undefined {
    return this.store.get(name);
  }
  names(): string[] {
    return [...this.store.keys()];
  }
  rangesFor(name: string): Range[] {
    return this.store.get(name)?.ranges ?? [];
  }
  entries(): Array<[string, HighlightLike]> {
    return [...this.store.entries()];
  }
}

interface OverlayWindow extends Window {
  __epubHighlightOverlay?: {
    render: (
      highlights: Array<{ id: string; color: string; pageNumber: number; cfi: string | null }>,
      chapterHref: string,
      currentChapterIndex: number,
    ) => void;
    hitTest: (x: number, y: number) => { id: string; color: string; text: string } | null;
    rangeOverlapsHighlight: (range: Range) => boolean;
  };
  __cfiBridge?: {
    setSpine: (hrefs: string[]) => void;
    cfiToRange: (cfi: string, chapterHref: string, doc: Document) => Range | null;
    rangeToCFI: (range: Range, chapterHref: string, doc: Document) => string | null;
  };
  Highlight?: new () => HighlightLike;
  CSS?: { highlights: HighlightRegistryMock };
  // jsdom Window exposes `eval`; lib.dom.d.ts Window does not.
  eval: (code: string) => unknown;
}

interface OverlayHarness {
  win: OverlayWindow;
  doc: Document;
  registry: HighlightRegistryMock;
  /** postMessages captured through the stubbed `window.parent`. */
  messages: Array<Record<string, unknown>>;
}

function setupOverlay(opts: { cssHighlights?: boolean; parentSpy?: boolean } = {}): OverlayHarness {
  const dom = new JSDOM(FIXTURE_HTML, {
    runScripts: 'outside-only',
    url: 'http://localhost/',
  });
  const doc = dom.window.document;
  const win = dom.window as unknown as OverlayWindow;

  // Install the CSS Custom Highlight API mock unless the test wants the
  // feature-detect path (absent API).
  const registry = new HighlightRegistryMock();
  if (opts.cssHighlights !== false) {
    class HighlightMock {
      ranges: Range[] = [];
      add(range: Range): void {
        this.ranges.push(range);
      }
    }
    win.Highlight = HighlightMock;
    win.CSS = { highlights: registry };
  }

  // Optional parent postMessage spy to observe epub-hl-failed payloads.
  const messages: Array<Record<string, unknown>> = [];
  if (opts.parentSpy !== false) {
    Object.defineProperty(win, 'parent', {
      value: { postMessage: (msg: unknown) => messages.push(msg as Record<string, unknown>) },
      configurable: true,
    });
  }

  // Install the real CFI bridge so CFIs resolve to genuine jsdom ranges.
  win.eval(IFRAME_CFI_BRIDGE_SCRIPT);
  win.__cfiBridge!.setSpine([CHAPTER_HREF]);

  // Install the overlay.
  win.eval(IFRAME_HIGHLIGHT_OVERLAY_SCRIPT);

  return { win, doc, registry, messages };
}

/** Select the first occurrence of `text` and return its Range. */
function selectText(doc: Document, text: string): Range | null {
  const walker = doc.createTreeWalker(doc.body, 0x4 /* SHOW_TEXT */);
  let node: Node | null = walker.nextNode();
  while (node) {
    const value = node.nodeValue ?? '';
    const idx = value.indexOf(text);
    if (idx >= 0) {
      const range = doc.createRange();
      range.setStart(node, idx);
      range.setEnd(node, idx + text.length);
      return range;
    }
    node = walker.nextNode();
  }
  return null;
}

/** Compute a resolvable CFI for the first occurrence of `text`. */
function cfiFor(win: OverlayWindow, doc: Document, text: string): string {
  const range = selectText(doc, text);
  if (!range) throw new Error(`fixture text not found: ${text}`);
  const cfi = win.__cfiBridge!.rangeToCFI(range, CHAPTER_HREF, doc);
  if (!cfi) throw new Error(`could not compute CFI for ${text}`);
  return cfi;
}

function hl(id: string, color: string, cfi: string, pageNumber = 0) {
  return { id, color, pageNumber, cfi };
}

// ────────────────────────────────────────────────────────────────────
// Smoke surface (kept from PR 1 — mount surface, idempotent eval,
// feature-detect no-op).
// ────────────────────────────────────────────────────────────────────
describe('epubHighlightOverlay — overlay surface (smoke)', () => {
  it('mounts render/hitTest/rangeOverlapsHighlight', () => {
    const { win } = setupOverlay();
    expect(win.__epubHighlightOverlay).toBeTruthy();
    expect(typeof win.__epubHighlightOverlay!.render).toBe('function');
    expect(typeof win.__epubHighlightOverlay!.hitTest).toBe('function');
    expect(typeof win.__epubHighlightOverlay!.rangeOverlapsHighlight).toBe('function');
  });

  it('is idempotent across repeated evaluations', () => {
    const { win } = setupOverlay();
    const first = win.__epubHighlightOverlay;
    win.eval(IFRAME_HIGHLIGHT_OVERLAY_SCRIPT);
    expect(win.__epubHighlightOverlay).toBe(first);
  });

  it('feature-detect: warns and renders nothing when CSS.highlights is absent', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    try {
      const { win, doc, registry } = setupOverlay({ cssHighlights: false });
      const before = doc.documentElement.outerHTML;
      win.__epubHighlightOverlay!.render(
        [hl('hl-1', '#FACC15', cfiFor(win, doc, 'Segundo'))],
        CHAPTER_HREF,
        0,
      );
      expect(registry.names().length).toBe(0);
      expect(doc.querySelectorAll('.epub-hl').length).toBe(0);
      expect(doc.documentElement.outerHTML).toBe(before);
      expect(warn).toHaveBeenCalled();
    } finally {
      warn.mockRestore();
    }
  });
});

// ────────────────────────────────────────────────────────────────────
// T12 — registry semantics (REQ-RENDERING)
// ────────────────────────────────────────────────────────────────────
describe('epubHighlightOverlay — registry semantics (T12)', () => {
  it('render registers ranges grouped under the correct color names', () => {
    const { win, doc, registry } = setupOverlay();
    const yellowCfi = cfiFor(win, doc, 'Primer parrafo');
    const yellowCfi2 = cfiFor(win, doc, 'Contiene algo');
    const blueCfi = cfiFor(win, doc, 'Segundo parrafo');

    win.__epubHighlightOverlay!.render(
      [
        hl('a', '#FACC15', yellowCfi), // canonical yellow
        hl('b', '#FACC15', yellowCfi2), // canonical yellow
        hl('c', '#60A5FA', blueCfi), // canonical blue
      ],
      CHAPTER_HREF,
      0,
    );

    expect(registry.names().sort()).toEqual(['epub-hl-blue', 'epub-hl-yellow']);
    expect(registry.rangesFor('epub-hl-yellow').length).toBe(2);
    expect(registry.rangesFor('epub-hl-blue').length).toBe(1);
    // The ranges hold the real selected text (the substring matched by
    // the fixture selector, not the full paragraph).
    const yellowTexts = registry.rangesFor('epub-hl-yellow').map((r) => r.toString().trim());
    expect(yellowTexts).toEqual(expect.arrayContaining(['Primer parrafo', 'Contiene algo']));
    expect(registry.rangesFor('epub-hl-blue')[0].toString()).toBe('Segundo parrafo');
  });

  it('render only registers highlights matching the current pageNumber', () => {
    const { win, doc, registry } = setupOverlay();
    const cfi = cfiFor(win, doc, 'Segundo');
    win.__epubHighlightOverlay!.render(
      [
        hl('a', '#FACC15', cfi, 0), // current chapter
        hl('b', '#4ADE80', cfi, 1), // other chapter → ignored
      ],
      CHAPTER_HREF,
      0,
    );
    expect(registry.names().sort()).toEqual(['epub-hl-yellow']);
  });

  it('idempotent re-render: second render with an extra highlight keeps both', () => {
    const { win, doc, registry } = setupOverlay();
    const cfiA = cfiFor(win, doc, 'Primer parrafo');
    const cfiB = cfiFor(win, doc, 'Segundo parrafo');

    win.__epubHighlightOverlay!.render([hl('a', '#FACC15', cfiA)], CHAPTER_HREF, 0);
    win.__epubHighlightOverlay!.render(
      [hl('a', '#FACC15', cfiA), hl('b', '#4ADE80', cfiB)],
      CHAPTER_HREF,
      0,
    );

    expect(registry.names().sort()).toEqual(['epub-hl-green', 'epub-hl-yellow']);
    expect(registry.rangesFor('epub-hl-yellow').length).toBe(1);
    expect(registry.rangesFor('epub-hl-green').length).toBe(1);
  });

  it('delete: re-render without a highlight removes its range; others remain', () => {
    const { win, doc, registry } = setupOverlay();
    const cfiA = cfiFor(win, doc, 'Primer parrafo');
    const cfiB = cfiFor(win, doc, 'Segundo parrafo');

    win.__epubHighlightOverlay!.render(
      [hl('a', '#FACC15', cfiA), hl('b', '#4ADE80', cfiB)],
      CHAPTER_HREF,
      0,
    );
    expect(registry.rangesFor('epub-hl-green').length).toBe(1);

    win.__epubHighlightOverlay!.render([hl('a', '#FACC15', cfiA)], CHAPTER_HREF, 0);

    expect(registry.names().sort()).toEqual(['epub-hl-yellow']);
    expect(registry.rangesFor('epub-hl-yellow').length).toBe(1);
    expect(registry.rangesFor('epub-hl-green').length).toBe(0);
  });

  it('recolor: re-render moves the range to the new color Highlight', () => {
    const { win, doc, registry } = setupOverlay();
    const cfi = cfiFor(win, doc, 'Primer parrafo');

    win.__epubHighlightOverlay!.render([hl('a', '#FACC15', cfi)], CHAPTER_HREF, 0);
    expect(registry.names().sort()).toEqual(['epub-hl-yellow']);

    win.__epubHighlightOverlay!.render([hl('a', '#60A5FA', cfi)], CHAPTER_HREF, 0);

    expect(registry.names().sort()).toEqual(['epub-hl-blue']);
    expect(registry.rangesFor('epub-hl-blue').length).toBe(1);
    expect(registry.rangesFor('epub-hl-yellow').length).toBe(0);
  });

  it('collapsed range is skipped with a warn; other highlights still register', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    try {
      const { win, doc, registry } = setupOverlay();
      const goodCfi = cfiFor(win, doc, 'Segundo');
      // A zero-length selection collapses to a single point → CFI with
      // start == end terminuses resolves to a collapsed range.
      const collapsedRange = doc.createRange();
      const p = doc.querySelector('p')!;
      const tn = p.firstChild!;
      collapsedRange.setStart(tn, 2);
      collapsedRange.setEnd(tn, 2);
      const collapsedCfi = win.__cfiBridge!.rangeToCFI(collapsedRange, CHAPTER_HREF, doc)!;

      win.__epubHighlightOverlay!.render(
        [hl('collapsed', '#FACC15', collapsedCfi), hl('good', '#4ADE80', goodCfi)],
        CHAPTER_HREF,
        0,
      );

      // No throw; collapsed skipped; the good one still registers.
      expect(registry.names().sort()).toEqual(['epub-hl-green']);
      expect(registry.rangesFor('epub-hl-green').length).toBe(1);
      expect(warn).toHaveBeenCalled();
    } finally {
      warn.mockRestore();
    }
  });
});

// ────────────────────────────────────────────────────────────────────
// T14 — colorMap (REQ-OBSERVABILITY, D2)
// ────────────────────────────────────────────────────────────────────
describe('epubHighlightOverlay — colorMap (T14)', () => {
  it('maps a legacy hex to the nearest canonical color and posts epub-hl-failed unknown-color', () => {
    const { win, doc, registry, messages } = setupOverlay();
    const cfi = cfiFor(win, doc, 'Primer parrafo');

    // #F9A8D4 (a pink) is nearest to canonical purple (Euclidean).
    win.__epubHighlightOverlay!.render([hl('pink', '#F9A8D4', cfi)], CHAPTER_HREF, 0);

    expect(registry.names().sort()).toEqual(['epub-hl-purple']);
    expect(registry.rangesFor('epub-hl-purple').length).toBe(1);
    expect(messages).toContainEqual(
      expect.objectContaining({
        type: 'epub-hl-failed',
        id: 'pink',
        reason: 'unknown-color',
        pageNumber: 0,
      }),
    );
  });

  it.each([
    ['#FBDC6B', 'epub-hl-yellow'],
    ['#86EFAC', 'epub-hl-green'],
    ['#93C5FD', 'epub-hl-blue'],
    ['#C4B5FD', 'epub-hl-purple'],
    ['#FDBA74', 'epub-hl-orange'],
  ])('legacy hex %s maps to the nearest canonical %s', (hex, expectedName) => {
    const { win, doc, registry } = setupOverlay();
    const cfi = cfiFor(win, doc, 'Primer parrafo');
    win.__epubHighlightOverlay!.render([hl('h', hex, cfi)], CHAPTER_HREF, 0);
    expect(registry.names().sort()).toEqual([expectedName]);
  });

  it('tie-breaks exact Euclidean equidistance to yellow (first canonical)', () => {
    // #7B9D11 is exactly equidistant (18354) between yellow and orange;
    // the strict '<' tie-break resolves to yellow because yellow is first
    // in the canonical list.
    const { win, doc, registry } = setupOverlay();
    const cfi = cfiFor(win, doc, 'Primer parrafo');
    win.__epubHighlightOverlay!.render([hl('tie', '#7B9D11', cfi)], CHAPTER_HREF, 0);
    expect(registry.names().sort()).toEqual(['epub-hl-yellow']);
  });

  it('a canonical hex maps to itself without an unknown-color failure', () => {
    const { win, doc, registry, messages } = setupOverlay();
    const cfi = cfiFor(win, doc, 'Primer parrafo');
    win.__epubHighlightOverlay!.render([hl('c', '#FACC15', cfi)], CHAPTER_HREF, 0);
    expect(registry.names().sort()).toEqual(['epub-hl-yellow']);
    expect(messages.some((m) => m.type === 'epub-hl-failed')).toBe(false);
  });

  it('invalid color posts epub-hl-failed invalid-color and skips the range', () => {
    const { win, doc, registry, messages } = setupOverlay();
    const goodCfi = cfiFor(win, doc, 'Segundo');
    win.__epubHighlightOverlay!.render(
      [hl('bad', 'not-a-color', goodCfi), hl('ok', '#4ADE80', goodCfi)],
      CHAPTER_HREF,
      0,
    );
    expect(registry.names().sort()).toEqual(['epub-hl-green']);
    expect(messages).toContainEqual(
      expect.objectContaining({ type: 'epub-hl-failed', id: 'bad', reason: 'invalid-color' }),
    );
  });
});

// ────────────────────────────────────────────────────────────────────
// T13 — hit-test boundaries (REQ-CLICK, D1)
// ────────────────────────────────────────────────────────────────────
describe('epubHighlightOverlay — hit-test boundaries (T13)', () => {
  interface HitTestHarness {
    win: OverlayWindow;
    range: Range;
    /** set the caret that document.caretRangeFromPoint will return. */
    setCaret: (c: { startContainer: Node; startOffset: number } | null) => void;
  }

  function setupHitTest(text: string): HitTestHarness {
    const { win, doc } = setupOverlay();
    const range = selectText(doc, text)!;
    const cfi = win.__cfiBridge!.rangeToCFI(range, CHAPTER_HREF, doc)!;
    win.__epubHighlightOverlay!.render([hl('hit', '#FACC15', cfi)], CHAPTER_HREF, 0);

    // Stub document.caretRangeFromPoint to return the preset caret.
    let caret: { startContainer: Node; startOffset: number } | null = null;
    const stub = (() => {
      const fn = () => caret;
      fn.__set = (c: { startContainer: Node; startOffset: number } | null) => {
        caret = c;
      };
      return fn;
    })() as unknown as Document['caretRangeFromPoint'] & { __set: (c: unknown) => void };
    Object.defineProperty(doc, 'caretRangeFromPoint', { value: stub, configurable: true });

    return { win, range, setCaret: (c) => stub.__set(c) };
  }

  it('first character of the highlight resolves (start boundary inclusive)', () => {
    const { win, range, setCaret } = setupHitTest('Segundo');
    setCaret({ startContainer: range.startContainer, startOffset: range.startOffset });

    const hit = win.__epubHighlightOverlay!.hitTest(10, 10);
    expect(hit).not.toBeNull();
    expect(hit!.id).toBe('hit');
  });

  it('last character of the highlight resolves', () => {
    const { win, range, setCaret } = setupHitTest('Segundo');
    setCaret({ startContainer: range.endContainer, startOffset: range.endOffset - 1 });

    const hit = win.__epubHighlightOverlay!.hitTest(10, 10);
    expect(hit).not.toBeNull();
    expect(hit!.id).toBe('hit');
    expect(hit!.text).toBe('Segundo');
  });

  it('exact end boundary resolves (D1: end boundary inclusive)', () => {
    const { win, range, setCaret } = setupHitTest('Segundo');
    setCaret({ startContainer: range.endContainer, startOffset: range.endOffset });

    const hit = win.__epubHighlightOverlay!.hitTest(10, 10);
    expect(hit).not.toBeNull();
    expect(hit!.id).toBe('hit');
  });

  it('adjacent plain text just past the end boundary misses', () => {
    const { win, range, setCaret } = setupHitTest('Segundo');
    // One char past the end boundary within the same text node.
    setCaret({ startContainer: range.endContainer, startOffset: range.endOffset + 1 });

    expect(win.__epubHighlightOverlay!.hitTest(10, 10)).toBeNull();
  });

  it('plain text before the highlight misses', () => {
    // "Contiene algo" begins mid-text-node (offset > 0) inside the first
    // paragraph, so a caret one char before it is a valid in-text position.
    const { win, range, setCaret } = setupHitTest('Contiene algo');
    expect(range.startOffset).toBeGreaterThan(0);
    setCaret({ startContainer: range.startContainer, startOffset: range.startOffset - 1 });

    expect(win.__epubHighlightOverlay!.hitTest(10, 10)).toBeNull();
  });

  it('hit-testing over multiple highlights resolves the correct one by id', () => {
    const { win, doc } = setupOverlay();
    const rangeA = selectText(doc, 'Primer parrafo')!;
    const rangeB = selectText(doc, 'Segundo parrafo')!;
    win.__epubHighlightOverlay!.render(
      [
        hl('a', '#FACC15', win.__cfiBridge!.rangeToCFI(rangeA, CHAPTER_HREF, doc)!),
        hl('b', '#4ADE80', win.__cfiBridge!.rangeToCFI(rangeB, CHAPTER_HREF, doc)!),
      ],
      CHAPTER_HREF,
      0,
    );

    let caret: { startContainer: Node; startOffset: number } | null = null;
    const stub = (() => {
      const fn = () => caret;
      fn.__set = (c: unknown) => {
        caret = c as typeof caret;
      };
      return fn;
    })() as unknown as Document['caretRangeFromPoint'] & { __set: (c: unknown) => void };
    Object.defineProperty(doc, 'caretRangeFromPoint', { value: stub, configurable: true });
    stub.__set({ startContainer: rangeB.startContainer, startOffset: rangeB.startOffset });

    const hit = win.__epubHighlightOverlay!.hitTest(5, 5);
    expect(hit).not.toBeNull();
    expect(hit!.id).toBe('b');
  });

  it('returns null when there is no caret at the point (outside text)', () => {
    const { win, setCaret } = setupHitTest('Segundo');
    setCaret(null);
    expect(win.__epubHighlightOverlay!.hitTest(10, 10)).toBeNull();
  });
});
