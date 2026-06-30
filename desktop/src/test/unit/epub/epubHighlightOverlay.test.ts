import { describe, expect, it, beforeAll, beforeEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { IFRAME_HIGHLIGHT_OVERLAY_SCRIPT } from '$lib/features/reader/viewer-epub/epubHighlightOverlayIframe';
import {
  setSpine,
  rangeToCFI,
  cfiToRange as realCfiToRange,
} from '$lib/features/reader/viewer-epub/cfiBridge';

// Fixture: a 3-paragraph chapter. The <p>s are on a single line so the
// DOM has no inter-paragraph whitespace text nodes (which would also
// be wrapped by the slow path; correct behavior, but noise for the
// assertions below). Real EPUBs frequently use <p> for paragraphs and
// the user often selects across paragraph boundaries (e.g. an entire
// argument or quote). The wrap must go INSIDE each <p> around the
// text, never AROUND the <p> (which would produce invalid HTML --
// inline containing block -- that the browser auto-corrects on the
// next render, breaking the wrap and shifting child indices).
const FIXTURE_HTML = `<!DOCTYPE html>
<html>
  <head><meta charset="utf-8"><title>Chapter</title></head>
  <body><p>Primer parrafo. Contiene algo de texto para resaltar.</p><p>Segundo parrafo. Tambien tiene contenido relevante.</p><p>Tercer parrafo. Y este completa la seleccion.</p></body>
</html>`;

const P2_TEXT = 'Segundo parrafo. Tambien tiene contenido relevante.';
const P3_TEXT = 'Tercer parrafo. Y este completa la seleccion.';

const CHAPTER_HREF = 'OEBPS/Text/ch1.xhtml';
const SPINE = [CHAPTER_HREF];

interface OverlayWindow extends Window {
  __cfiBridge?: { cfiToRange: (cfi: string, href: string, doc: Document) => Range | null };
  __epubHighlightOverlay?: {
    render: (
      highlights: Array<{ id: string; color: string; pageNumber: number; cfi: string | null }>,
      chapterHref: string,
      currentChapterIndex: number,
    ) => void;
  };
  // jsdom Window exposes `eval`; the lib.dom.d.ts Window type does not.
  eval: (code: string) => unknown;
}

/**
 * Build a fresh jsdom document from the fixture and install both the
 * cfiBridge and the highlight overlay script. Returns the document
 * and a reference to the window so the test can inspect state.
 */
function setupDomWithOverlay(): { doc: Document; win: OverlayWindow } {
  // Use a real (non-opaque) origin so `localStorage` and other
  // origin-gated APIs are available. JSDOM's default `about:blank`
  // origin is opaque, which throws SecurityError when the cfiBridge
  // or overlay script touches any origin-gated state.
  const dom = new JSDOM(FIXTURE_HTML, {
    runScripts: 'outside-only',
    url: 'http://localhost/',
  });
  const doc = dom.window.document;
  const win = dom.window as unknown as OverlayWindow;

  // Real cfiToRange from the TS bridge, so we don't have to hand-roll
  // CFI strings in the test.
  win.__cfiBridge = {
    cfiToRange: (cfi: string, href: string, d: Document): Range | null => {
      // Defer to the real implementation; the cfiBridge module reads
      // its spine registry from setSpine(...) which is set in beforeAll.
      // The bridge is pure (no global state besides the spine list), so
      // we can import and call it directly.
      // We do that via a small helper exposed below.
      return realCfiToRange(cfi, href, d);
    },
  };

  // Eval the overlay script in the jsdom window so it can install
  // __epubHighlightOverlay.
  win.eval(IFRAME_HIGHLIGHT_OVERLAY_SCRIPT);

  return { doc, win };
}

describe('epubHighlightOverlay wrapRange (block-level selections)', () => {
  beforeAll(() => {
    setSpine(SPINE);
  });

  beforeEach(() => {
    // Reset the spine for each test (cfiToRange uses a module-private
    // registry that persists across tests in the same module).
    setSpine(SPINE);
  });

  it('wraps text INSIDE a <p> when the range covers an entire paragraph (regression for invalid <span><p></p></span>)', () => {
    const { doc, win } = setupDomWithOverlay();
    const p2 = doc.querySelectorAll('p')[1]!;
    const textNode = p2.firstChild!;
    const range = doc.createRange();
    range.setStart(textNode, 0);
    range.setEnd(textNode, textNode.nodeValue!.length);

    const cfi = rangeToCFI(range, CHAPTER_HREF, doc);
    expect(cfi).not.toBeNull();

    win.__epubHighlightOverlay!.render(
      [{ id: 'hl-1', color: '#FACC15', pageNumber: 0, cfi }],
      CHAPTER_HREF,
      0,
    );

    // The wrap must be INSIDE the original <p>, not around it.
    const wraps = doc.querySelectorAll('.epub-hl');
    expect(wraps.length).toBe(1);
    const wrap = wraps[0]!;
    expect(wrap.parentElement).toBe(p2);
    // Use the fixture string instead of the original `textNode.nodeValue`:
    // after surroundContents, the original text node reference is no
    // longer a reliable source of truth (jsdom may split or clone).
    expect(wrap.textContent).toBe(P2_TEXT);
    // The <p> itself must still be a direct child of <body>, not wrapped
    // in the highlight span.
    expect(p2.parentElement).toBe(doc.body);
    // The wrap must NOT contain a <p> (the old buggy output was
    // `<span class="epub-hl"><p>...</p></span>` -- a <p> inside the wrap).
    expect(wrap.querySelector('p')).toBeNull();
  });

  it('wraps text inside EACH <p> when the range crosses multiple paragraphs', () => {
    const { doc, win } = setupDomWithOverlay();
    const p2 = doc.querySelectorAll('p')[1]!;
    const p3 = doc.querySelectorAll('p')[2]!;
    const range = doc.createRange();
    range.setStart(p2.firstChild!, 0);
    range.setEnd(p3.firstChild!, p3.firstChild!.nodeValue!.length);

    const cfi = rangeToCFI(range, CHAPTER_HREF, doc);
    expect(cfi).not.toBeNull();

    win.__epubHighlightOverlay!.render(
      [{ id: 'hl-cross', color: '#60A5FA', pageNumber: 0, cfi }],
      CHAPTER_HREF,
      0,
    );

    // Two wraps: one inside p2, one inside p3. No wrap around either <p>.
    const wraps = doc.querySelectorAll('.epub-hl');
    expect(wraps.length).toBe(2);
    const wrapParents = Array.from(wraps).map((w) => w.parentElement);
    expect(wrapParents).toContain(p2);
    expect(wrapParents).toContain(p3);
    // Each wrap contains the right text (matches its parent <p>'s text).
    const wrapByParent = new Map<Element, Element>();
    for (const w of Array.from(wraps)) {
      wrapByParent.set(w.parentElement!, w);
    }
    expect(wrapByParent.get(p2)?.textContent).toBe(P2_TEXT);
    expect(wrapByParent.get(p3)?.textContent).toBe(P3_TEXT);
    // Both <p>s still under <body>, untouched structurally.
    expect(p2.parentElement).toBe(doc.body);
    expect(p3.parentElement).toBe(doc.body);
    // No <p> inside any wrap.
    for (const w of Array.from(wraps)) {
      expect(w.querySelector('p')).toBeNull();
    }
  });

  it('preserves the fast path for a range within a single text node', () => {
    const { doc, win } = setupDomWithOverlay();
    const p1 = doc.querySelectorAll('p')[0]!;
    const textNode = p1.firstChild!;
    // Substring: "Contiene algo"
    const start = textNode.nodeValue!.indexOf('Contiene');
    const end = start + 'Contiene algo'.length;
    const range = doc.createRange();
    range.setStart(textNode, start);
    range.setEnd(textNode, end);

    const cfi = rangeToCFI(range, CHAPTER_HREF, doc);
    expect(cfi).not.toBeNull();

    win.__epubHighlightOverlay!.render(
      [{ id: 'hl-fast', color: '#4ADE80', pageNumber: 0, cfi }],
      CHAPTER_HREF,
      0,
    );

    const wraps = doc.querySelectorAll('.epub-hl');
    expect(wraps.length).toBe(1);
    const wrap = wraps[0]!;
    expect(wrap.parentElement).toBe(p1);
    expect(wrap.textContent).toBe('Contiene algo');
    // Wrap must contain the highlighted text, not the whole <p>.
    expect(p1.textContent).toContain('Contiene algo');
  });
});

// ─── Incremental render regression tests ──────────────────────────
// Reproduces the user-reported bug: "a veces tengo que salir y volver a
// entrar al libro para que pueda verlos". Root cause was the
// "clear-all then re-render-all" strategy: removing every wrap then
// calling body.normalize() to merge adjacent text nodes invalidated
// the CFIs of highlights the user just created (their CFIs were
// captured against the wrapped DOM). The fix makes the render
// incremental: only adds the new wraps, never touches the existing
// ones, never normalizes.
describe('epubHighlightOverlay incremental render (CFI preservation)', () => {
  beforeEach(() => {
    setSpine(SPINE);
  });

  it('keeps the first highlight when a second one is added in the same chapter', () => {
    const { doc, win } = setupDomWithOverlay();
    const p1 = doc.querySelectorAll('p')[0]!;
    const p2 = doc.querySelectorAll('p')[1]!;
    const p1Text = p1.firstChild!;
    const p2Text = p2.firstChild!;

    // First highlight: "Contiene algo" in p1. CFI captured against
    // the bare DOM (no wraps yet).
    const r1 = doc.createRange();
    r1.setStart(p1Text, p1Text.nodeValue!.indexOf('Contiene'));
    r1.setEnd(p1Text, p1Text.nodeValue!.indexOf('Contiene') + 'Contiene algo'.length);
    const cfi1 = rangeToCFI(r1, CHAPTER_HREF, doc);
    expect(cfi1).not.toBeNull();

    win.__epubHighlightOverlay!.render(
      [{ id: 'hl-1', color: '#FACC15', pageNumber: 0, cfi: cfi1 }],
      CHAPTER_HREF,
      0,
    );
    expect(doc.querySelectorAll('.epub-hl').length).toBe(1);
    expect(doc.querySelector('.epub-hl')!.getAttribute('data-id')).toBe('hl-1');

    // Second highlight: "Tambien tiene" in p2. CFI captured against
    // the DOM with hl-1 ALREADY WRAPPED. This is the exact scenario
    // that broke before the fix: clearing+normalizing would have
    // destroyed hl-1's text node position and hl-2's CFI would fail.
    const r2 = doc.createRange();
    r2.setStart(p2Text, p2Text.nodeValue!.indexOf('Tambien'));
    r2.setEnd(p2Text, p2Text.nodeValue!.indexOf('Tambien') + 'Tambien tiene'.length);
    const cfi2 = rangeToCFI(r2, CHAPTER_HREF, doc);
    expect(cfi2).not.toBeNull();

    win.__epubHighlightOverlay!.render(
      [
        { id: 'hl-1', color: '#FACC15', pageNumber: 0, cfi: cfi1 },
        { id: 'hl-2', color: '#60A5FA', pageNumber: 0, cfi: cfi2 },
      ],
      CHAPTER_HREF,
      0,
    );

    // Both wraps must be present. Before the fix, hl-2's CFI failed
    // to resolve with "local path did not resolve" and the highlight
    // was silently missing.
    const wraps = Array.from(doc.querySelectorAll('.epub-hl'));
    expect(wraps.length).toBe(2);
    const ids = wraps.map((w) => w.getAttribute('data-id')).sort();
    expect(ids).toEqual(['hl-1', 'hl-2']);
  });

  it('removes a wrap when its highlight is deleted from the list', () => {
    const { doc, win } = setupDomWithOverlay();
    const p1 = doc.querySelectorAll('p')[0]!;
    const p1Text = p1.firstChild!;
    const r = doc.createRange();
    r.setStart(p1Text, 0);
    r.setEnd(p1Text, 'Primer'.length);
    const cfi = rangeToCFI(r, CHAPTER_HREF, doc);
    expect(cfi).not.toBeNull();

    win.__epubHighlightOverlay!.render(
      [
        { id: 'keep', color: '#FACC15', pageNumber: 0, cfi },
        { id: 'remove', color: '#60A5FA', pageNumber: 0, cfi },
      ],
      CHAPTER_HREF,
      0,
    );
    expect(doc.querySelectorAll('.epub-hl').length).toBe(2);

    // Re-render with only "keep".
    win.__epubHighlightOverlay!.render(
      [{ id: 'keep', color: '#FACC15', pageNumber: 0, cfi }],
      CHAPTER_HREF,
      0,
    );
    const remaining = Array.from(doc.querySelectorAll('.epub-hl'));
    expect(remaining.length).toBe(1);
    expect(remaining[0]!.getAttribute('data-id')).toBe('keep');
  });

  it('updates the background of an existing wrap when its color changes', () => {
    const { doc, win } = setupDomWithOverlay();
    const p1 = doc.querySelectorAll('p')[0]!;
    const p1Text = p1.firstChild!;
    const r = doc.createRange();
    r.setStart(p1Text, 0);
    r.setEnd(p1Text, 'Primer'.length);
    const cfi = rangeToCFI(r, CHAPTER_HREF, doc);
    expect(cfi).not.toBeNull();

    win.__epubHighlightOverlay!.render(
      [{ id: 'hl', color: '#FACC15', pageNumber: 0, cfi }],
      CHAPTER_HREF,
      0,
    );
    const wrap = doc.querySelector('.epub-hl') as HTMLElement;
    expect(wrap.style.background).toBe('rgba(250, 204, 21, 0.4)');

    // Re-render with a new color but same id and cfi.
    win.__epubHighlightOverlay!.render(
      [{ id: 'hl', color: '#60A5FA', pageNumber: 0, cfi }],
      CHAPTER_HREF,
      0,
    );
    // Same wrap node, new background. No duplicate wraps.
    expect(doc.querySelectorAll('.epub-hl').length).toBe(1);
    expect(doc.querySelector('.epub-hl')).toBe(wrap);
    expect(wrap.style.background).toBe('rgba(96, 165, 250, 0.4)');
  });
});
