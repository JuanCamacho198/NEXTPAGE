import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

function readViewerSource(): string {
  return readFileSync(
    join(process.cwd(), 'src/lib/features/reader/viewer-epub/EpubNativeViewer.svelte'),
    'utf8',
  );
}
function readHelpersSource(): string {
  return readFileSync(
    join(process.cwd(), 'src/lib/features/reader/viewer-epub/epubViewerHelpers.ts'),
    'utf8',
  );
}
function readSpineSource(): string {
  return readFileSync(
    join(process.cwd(), 'src/lib/features/reader/viewer-epub/useEpubSpine.svelte.ts'),
    'utf8',
  );
}
function readBridgeSource(): string {
  return readFileSync(
    join(process.cwd(), 'src/lib/features/reader/viewer-epub/useEpubBridge.svelte.ts'),
    'utf8',
  );
}
function readNavSource(): string {
  return readFileSync(
    join(process.cwd(), 'src/lib/features/reader/viewer-epub/useEpubNavigation.svelte.ts'),
    'utf8',
  );
}
function readRenderSource(): string {
  return readFileSync(
    join(process.cwd(), 'src/lib/features/reader/viewer-epub/useEpubRender.svelte.ts'),
    'utf8',
  );
}

describe('EpubNativeViewer Phase 3 — viewer wiring', () => {
  describe('3.1 spineHrefs wiring (spine authority)', () => {
    it('consumes spineHrefs for setSpine/renderChapter/LocatorCodec (not chapters)', () => {
      const src = readViewerSource();
      expect(src).toContain('spineHrefs');
      expect(src).not.toContain('metadata.chapters.map((c) => normalizeHref(c.href))');
      expect(src).toMatch(/currentSpineIndex/);
    });

    it('keeps TOC from toc only (not chapters)', () => {
      const src = readViewerSource();
      expect(src).toContain('getToc');
      expect(src).toContain('toc');
    });

    it('viewer wires createEpubSpine', () => {
      const src = readViewerSource();
      expect(src).toContain('createEpubSpine');
    });
  });

  describe('3.2 mapping helpers offset-2', () => {
    it('spineIndexForToc and tocIndexForSpine handle Historia offset-2 via index → normalize → filename', async () => {
      const helpersSrc = readHelpersSource();
      const spineSrc = readSpineSource();
      expect(helpersSrc).toContain("split('/').pop()");
      expect(spineSrc).toContain('spineIndexForToc');
      expect(spineSrc).toContain('tocIndexForSpine');
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
      expect(tocIndexForSpine(toc as any, 99, 'OEBPS/Text/HM-colombia-1.html')).toBe(0);
      expect(tocIndexForSpine(toc as any, 99, 'Text/HM-colombia-2.html')).toBe(1);
      expect(tocIndexForSpine(toc as any, 99, 'OEBPS/Text/HM-colombia-3.html')).toBe(2);
      expect(tocIndexForSpine(toc as any, 99, 'OEBPS/Text/HM-colombia-3.html#_idParaDest-5')).toBe(2);
    });
  });

  describe('3.3 fragment scroll', () => {
    it('preserves #frag and provides scrollToFragment with getElementById + 3×rAF', () => {
      const bridgeSrc = readBridgeSource();
      const helpersSrc = readHelpersSource();
      expect(bridgeSrc).toContain('scrollToFragment');
      expect(bridgeSrc).toContain('getElementById');
      const rafMatches = bridgeSrc.match(/requestAnimationFrame/g) ?? [];
      expect(rafMatches.length).toBeGreaterThanOrEqual(3);
      expect(helpersSrc).toMatch(/extractFragment|stripFragment|#frag|fragment/);
    });

    it('pending fragment triggers after chapter load', () => {
      const navSrc = readNavSource();
      const viewerSrc = readViewerSource();
      expect(navSrc).toContain('pendingFragment');
      expect(viewerSrc).toContain('pendingFragment');
    });
  });

  describe('3.4 sanitize before rewrite', () => {
    it('buildChapterSrcdoc sanitizes before rewrite and guarantees zero chrome-extension://', async () => {
      const helpersSrc = readHelpersSource();
      const renderSrc = readRenderSource();
      expect(helpersSrc).toContain('sanitizeEpubHtml');
      expect(renderSrc).toContain('sanitizeEpubHtml');
      const sanitizeIdx = renderSrc.indexOf('sanitizeEpubHtml');
      const rewriteIdx = renderSrc.indexOf('resolveResourcePath');
      // sanitize should appear before rewrite in render
      expect(sanitizeIdx).toBeGreaterThan(-1);
      expect(rewriteIdx).toBeGreaterThan(-1);
      // If rewrite not found, at least helpers guarantees sanitize
      if (rewriteIdx !== -1) expect(sanitizeIdx).toBeLessThan(rewriteIdx);

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
