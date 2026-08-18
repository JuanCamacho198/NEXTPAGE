import { describe, expect, it } from 'vitest';
import { JSDOM } from 'jsdom';
import { IFRAME_HIGHLIGHT_OVERLAY_SCRIPT } from '$lib/features/reader/viewer-epub/epubHighlightOverlayIframe';

// PR 1 smoke suite. The wrap-based renderer (wrapRange / incremental
// diff / pristine-DOM computeCFI / self-heal) is gone; the overlay now
// renders via the CSS Custom Highlight API (CSS.highlights +
// ::highlight()) with zero DOM mutation. jsdom does not implement the
// CSS Custom Highlight API, so the full registry-semantics suite needs
// a CSS.highlights/Highlight mock and lands in PR 2 (T12-T14). This
// suite keeps the file green in the meantime: it asserts the overlay
// surface mounts, the mount is idempotent, and the feature-detect path
// renders nothing when CSS.highlights is absent.
const FIXTURE_HTML = `<!DOCTYPE html>
<html>
  <head><meta charset="utf-8"><title>Chapter</title></head>
  <body><p>Primer parrafo. Contiene algo de texto para resaltar.</p><p>Segundo parrafo. Tambien tiene contenido relevante.</p><p>Tercer parrafo. Y este completa la seleccion.</p></body>
</html>`;

const CHAPTER_HREF = 'OEBPS/Text/ch1.xhtml';

interface OverlayWindow extends Window {
  __epubHighlightOverlay?: {
    render: (
      highlights: Array<{
        id: string;
        color: string;
        pageNumber: number;
        cfi: string | null;
        text?: string | null;
      }>,
      chapterHref: string,
      currentChapterIndex: number,
    ) => void;
    hitTest: (x: number, y: number) => { id: string; color: string; text: string } | null;
    rangeOverlapsHighlight: (range: Range) => boolean;
  };
  // jsdom Window exposes `eval`; the lib.dom.d.ts Window type does not.
  eval: (code: string) => unknown;
}

function setupDomWithOverlay(): { doc: Document; win: OverlayWindow } {
  // Use a real (non-opaque) origin so origin-gated APIs are available.
  const dom = new JSDOM(FIXTURE_HTML, {
    runScripts: 'outside-only',
    url: 'http://localhost/',
  });
  const doc = dom.window.document;
  const win = dom.window as unknown as OverlayWindow;
  win.eval(IFRAME_HIGHLIGHT_OVERLAY_SCRIPT);
  return { doc, win };
}

describe('epubHighlightOverlay (PR 1 smoke)', () => {
  it('mounts the overlay surface (render/hitTest/rangeOverlapsHighlight)', () => {
    const { win } = setupDomWithOverlay();
    expect(win.__epubHighlightOverlay).toBeTruthy();
    expect(typeof win.__epubHighlightOverlay!.render).toBe('function');
    expect(typeof win.__epubHighlightOverlay!.hitTest).toBe('function');
    expect(typeof win.__epubHighlightOverlay!.rangeOverlapsHighlight).toBe('function');
  });

  it('is idempotent across repeated evaluations', () => {
    const { win } = setupDomWithOverlay();
    const first = win.__epubHighlightOverlay;
    win.eval(IFRAME_HIGHLIGHT_OVERLAY_SCRIPT);
    expect(win.__epubHighlightOverlay).toBe(first);
  });

  it('renders nothing and leaves the DOM untouched when CSS.highlights is absent (feature-detect)', () => {
    // jsdom has no CSS.highlights, so the feature-detect path must
    // warn and no-op — no wrap fallback, no DOM mutation.
    const { doc, win } = setupDomWithOverlay();
    const before = doc.documentElement.outerHTML;
    win.__epubHighlightOverlay!.render(
      [{ id: 'hl-1', color: '#FACC15', pageNumber: 0, cfi: 'epubcfi(/6/1!/1/1,/1:0,/1:10)' }],
      CHAPTER_HREF,
      0,
    );
    expect(doc.querySelectorAll('.epub-hl').length).toBe(0);
    expect(doc.documentElement.outerHTML).toBe(before);
  });
});
