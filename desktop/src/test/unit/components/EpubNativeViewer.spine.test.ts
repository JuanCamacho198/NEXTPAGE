import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

function readViewerSource(): string {
  return readFileSync(
    join(process.cwd(), 'src/lib/features/reader/viewer-epub/EpubNativeViewer.svelte'),
    'utf8',
  );
}

describe('EpubNativeViewer Phase 3 — viewer wiring', () => {
  describe('3.1 spineHrefs wiring (spine authority)', () => {
    it('consumes spineHrefs for setSpine/renderChapter/LocatorCodec (not chapters)', () => {
      const src = readViewerSource();
      // Must use spineHrefs (or getSpineHrefs) for spine authority, not chapters.map
      expect(src).toContain('spineHrefs');
      // Must not use old conflated pattern for spineHrefs generation
      expect(src).not.toContain('metadata.chapters.map((c) => normalizeHref(c.href))');
      // Must derive currentSpineIndex for CFI/location
      expect(src).toMatch(/currentSpineIndex/);
    });

    it('keeps TOC from toc only (not chapters)', () => {
      const src = readViewerSource();
      // toc is the nav source; chapters alias should be fallback only
      expect(src).toContain('getToc');
      expect(src).toContain('toc');
    });
  });

  describe('3.2 mapping helpers offset-2', () => {
    it('spineIndexForToc and tocIndexForSpine handle Historia offset-2 via index → normalize → filename', async () => {
      const src = readViewerSource();
      const helpersSrc = readFileSync(
        join(process.cwd(), 'src/lib/features/reader/viewer-epub/epubViewerHelpers.ts'),
        'utf8',
      );
      expect(src).toContain('function spineIndexForToc');
      expect(src).toContain('function tocIndexForSpine');
      // Must handle filename fallback (OEBPS/Text prefix variance) — in helpers
      expect(helpersSrc).toContain("split('/').pop()");
      // Also verify via pure helpers (triangulation)
      const { spineIndexForToc, tocIndexForSpine } = await import(
        '$lib/features/reader/viewer-epub/epubViewerHelpers'
      );
      const toc = [
        { index: 2, id: 'chapter-0', label: 'HM 1', href: 'OEBPS/Text/HM-colombia-1.html', depth: 0 },
        { index: 3, id: 'chapter-1', label: 'HM 2', href: 'OEBPS/Text/HM-colombia-2.html', depth: 0 },
        { index: 4, id: 'chapter-2', label: 'HM 3', href: 'OEBPS/Text/HM-colombia-3.html#_idParaDest-5', depth: 0 },
      ];
      expect(spineIndexForToc(toc as any, 0)).toBe(2);
      expect(tocIndexForSpine(toc as any, 2)).toBe(0);
      expect(tocIndexForSpine(toc as any, 0)).toBeNull();
      // filename fallback before not-found
      expect(tocIndexForSpine(toc as any, 99, 'OEBPS/Text/HM-colombia-1.html')).toBe(0);
      expect(tocIndexForSpine(toc as any, 99, 'Text/HM-colombia-2.html')).toBe(1);
      // fragment preserved but lookup strips fragment
      expect(tocIndexForSpine(toc as any, 99, 'OEBPS/Text/HM-colombia-3.html')).toBe(2);
      expect(tocIndexForSpine(toc as any, 99, 'OEBPS/Text/HM-colombia-3.html#_idParaDest-5')).toBe(2);
    });
  });

  describe('3.3 fragment scroll', () => {
    it('preserves #frag and provides scrollToFragment with getElementById + 3×rAF', () => {
      const src = readViewerSource();
      expect(src).toContain('scrollToFragment');
      expect(src).toContain('getElementById');
      // 3× requestAnimationFrame chain
      const rafMatches = src.match(/requestAnimationFrame/g) ?? [];
      expect(rafMatches.length).toBeGreaterThanOrEqual(3);
      // Must extract fragment from href
      expect(src).toMatch(/extractFragment|stripFragment|#frag|fragment/);
    });

    it('pending fragment triggers after chapter load', () => {
      const src = readViewerSource();
      expect(src).toContain('pendingFragment');
      expect(src).toMatch(/scrollToFragment\(.*fragment/);
    });
  });

  describe('3.4 sanitize before rewrite', () => {
    it('buildChapterSrcdoc sanitizes before rewrite and guarantees zero chrome-extension://', async () => {
      const src = readViewerSource();
      expect(src).toContain('sanitizeEpubHtml');
      // Must be called before rewrite / srcdoc cache
      const sanitizeIdx = src.indexOf('sanitizeEpubHtml');
      const rewriteIdx = src.indexOf('resolveResourcePath');
      expect(sanitizeIdx).toBeGreaterThan(-1);
      expect(rewriteIdx).toBeGreaterThan(-1);
      expect(sanitizeIdx).toBeLessThan(rewriteIdx);

      const { sanitizeEpubHtml } = await import(
        '$lib/features/reader/viewer-epub/epubViewerHelpers'
      );
      const polluted =
        '<div><p>Hello</p><img id="floatBarImgId" src="chrome-extension://dbkmjjclgbiooljcegcddagnddjedmed/img.png"><p>World <img src="chrome-extension://abc/def.png"></p></div>';
      const sanitized = sanitizeEpubHtml(polluted);
      expect(sanitized).not.toContain('chrome-extension://');
      expect(sanitized).not.toContain('floatBarImgId');
      expect(sanitized).toContain('Hello');
      expect(sanitized).toContain('World');
    });

    it('sanitize preserves normal content and handles empty src cleanup', async () => {
      const { sanitizeEpubHtml } = await import(
        '$lib/features/reader/viewer-epub/epubViewerHelpers'
      );
      const normal = '<p>Keep <img src="images/cover.jpg"> and <a href="chapter2.xhtml">link</a></p>';
      const sanitized = sanitizeEpubHtml(normal);
      expect(sanitized).toContain('images/cover.jpg');
      expect(sanitized).toContain('chapter2.xhtml');
      expect(sanitized).not.toContain('chrome-extension://');

      const emptySrc = '<p><img src="chrome-extension://evil"></p>';
      expect(sanitizeEpubHtml(emptySrc)).not.toContain('chrome-extension://');
      expect(sanitizeEpubHtml(emptySrc)).not.toContain('evil');
    });
  });
});
