import { render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import EpubNativeViewer from '$lib/features/reader/components/EpubNativeViewer.svelte';

// Mock Tauri invoke
const mockInvoke = vi.fn();
vi.mock('@tauri-apps/api/core', () => ({
  invoke: (...args: unknown[]) => mockInvoke(...args),
  convertFileSrc: vi.fn((path: string) => `tauri://asset.localhost/${String(path).replace(/\\/g, '/')}`),
}));

const t = (key: string) => key;

describe('EpubNativeViewer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Make parse_epub return minimal metadata quickly for loading state testing
    mockInvoke.mockImplementation((cmd: string) => {
      if (cmd === 'parse_epub') {
        return Promise.resolve({
          title: 'Test Book',
          author: 'Test Author',
          language: 'en',
          publisher: null,
          chapters: [{ index: 0, id: '0', label: 'Chapter 1', href: 'chap1.xhtml' }],
          totalChapters: 1,
          resourcesPath: '/tmp/resources',
        });
      }
      if (cmd === 'get_epub_chapter') {
        return Promise.resolve({
          index: 0,
          html: `<!DOCTYPE html><html><head>
            <link rel="stylesheet" href="../css/book.css">
            <style>p.title { font-weight: 700; }</style>
          </head><body><p class="title">Hello</p><p>World</p></body></html>`,
          mime: 'application/xhtml+xml',
          chapterBasePath: 'OEBPS/Text',
          chapterPath: 'OEBPS/Text/chapter1.xhtml',
        });
      }
      return Promise.reject(new Error(`Unknown command: ${cmd}`));
    });
  });

  // ─── Task 1.3: Loading progress indicator ────────────────
  describe('Loading Progress (1.3)', () => {
    it('shows animated progress bar during loading state', async () => {
      // Delay invoke to keep loading state active
      mockInvoke.mockImplementation(
        () =>
          new Promise(() => {
            /* never resolves - keeps loading */
          }),
      );

      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
      });

      // Should show the progress bar container div while loading
      const progressContainer = document.querySelector('.h-1.w-full');
      expect(progressContainer).toBeTruthy();
      expect(progressContainer?.classList.contains('bg-(--color-border)/20')).toBe(true);
      expect(progressContainer?.classList.contains('overflow-hidden')).toBe(true);
    });

    it('replaces progress bar with iframe after loading completes', async () => {
      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
      });

      // After loading, the iframe should appear
      const iframe = await screen.findByTitle('chapter');
      expect(iframe).toBeInTheDocument();
      // Progress bar should be gone
      const progressBar = document.querySelector('.h-1.w-full');
      expect(progressBar).toBeFalsy();
    });
  });

  // ─── Task 1.2: Fullscreen props ──────────────────────────
  describe('Fullscreen Support (1.2)', () => {
    it('renders container with fullscreen classes when isFullscreen is true', async () => {
      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        isFullscreen: true,
        t,
      });

      // Wait for loading to complete
      await screen.findByTitle('chapter');

      // The iframe container div should exist
      const chapterDiv = document.querySelector('.flex-1');
      expect(chapterDiv).toBeTruthy();
    });
  });

  // ─── Task 1.1: Text Selection via postMessage ────────────
  describe('Text Selection (1.1)', () => {
    it('fires onselection when epub-selection postMessage arrives', async () => {
      const onselection = vi.fn();

      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
        onselection,
      });

      // Wait for component to mount
      await screen.findByTitle('chapter');

      // Simulate postMessage from iframe
      const selectionEvent = {
        type: 'epub-selection' as const,
        text: 'selected text',
        bounds: { left: 10, top: 20, right: 100, bottom: 40 },
        container: { left: 0, top: 0, width: 800, height: 600 },
        rects: [{ left: 10, top: 20, width: 90, height: 20 }],
        pageNumber: 0,
      };

      window.dispatchEvent(
        new MessageEvent('message', {
          data: selectionEvent,
          origin: window.origin,
        }),
      );

      expect(onselection).toHaveBeenCalledTimes(1);
      expect(onselection).toHaveBeenCalledWith({
        text: 'selected text',
        bounds: { left: 10, top: 20, right: 100, bottom: 40 },
        container: { left: 0, top: 0, width: 800, height: 600 },
        placement: 'epub-chapter',
        rects: [{ left: 10, top: 20, width: 90, height: 20 }],
        pageNumber: 0,
      });
    });

    it('ignores non-selection postMessage events', async () => {
      const onselection = vi.fn();

      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
        onselection,
      });

      await screen.findByTitle('chapter');

      // Dispatch a non-selection message
      window.dispatchEvent(
        new MessageEvent('message', {
          data: { type: 'some-other-event', payload: 'irrelevant' },
          origin: window.origin,
        }),
      );

      expect(onselection).not.toHaveBeenCalled();
    });

    it('ignores messages with empty text selection', async () => {
      const onselection = vi.fn();

      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
        onselection,
      });

      await screen.findByTitle('chapter');

      // Dispatch selection message with empty text
      window.dispatchEvent(
        new MessageEvent('message', {
          data: { type: 'epub-selection', text: '', bounds: {} },
          origin: window.origin,
        }),
      );

      expect(onselection).not.toHaveBeenCalled();
    });
  });

  // ─── Selection script injection ──────────────────────────
  describe('Selection Script Injection (1.1)', () => {
    it('injects selection detection script into srcdoc', async () => {
      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
      });

      await screen.findByTitle('chapter');

      // The iframe should have srcdoc with the selection script
      const iframe = document.querySelector('iframe');
      expect(iframe).toBeTruthy();

      const srcdoc = iframe?.getAttribute('srcdoc') || '';
      expect(srcdoc).toContain('mouseup');
      expect(srcdoc).toContain('window.parent.postMessage');
      expect(srcdoc).toContain('epub-selection');
      expect(srcdoc).toContain('epub-resize');
      expect(srcdoc).toContain('nextpage-reader-overrides');
      expect(srcdoc).toContain('OEBPS/css/book.css');
      expect(srcdoc).toContain('font-weight: 700');
    });
  });

  // ─── Task 5.2: Floating pill removed, EpubControls present ─────
  describe('EpubControls Integration (5.2)', () => {
    it('does NOT render floating pill overlay div', async () => {
      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
      });

      await screen.findByTitle('chapter');

      // The old floating pill was an overlay div at absolute bottom-4
      const pillContainer = document.querySelector('.absolute.bottom-4');
      expect(pillContainer).toBeFalsy();
    });

    it('renders EpubControls with TOC button in the viewer', async () => {
      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
      });

      await screen.findByTitle('chapter');

      // EpubControls renders a TOC button with data-testid="epub-toc"
      const tocBtn = await screen.findByTestId('epub-toc');
      expect(tocBtn).toBeInTheDocument();
    });

    it('renders EpubControls with prev/next/font-size controls', async () => {
      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
      });

      await screen.findByTitle('chapter');

      expect(screen.getByTestId('epub-prev')).toBeInTheDocument();
      expect(screen.getByTestId('epub-next')).toBeInTheDocument();
      expect(screen.getByTestId('epub-font-decrease')).toBeInTheDocument();
      expect(screen.getByTestId('epub-font-increase')).toBeInTheDocument();
    });

    it('renders EpubControls with fullscreen toggle', async () => {
      render(EpubNativeViewer, {
        filePath: '/test/book.epub',
        bookId: 'test-book',
        t,
        isFullscreen: false,
        onToggleFullscreen: vi.fn(),
      });

      await screen.findByTitle('chapter');

      const fsBtn = screen.getByTestId('epub-fullscreen');
      expect(fsBtn).toBeInTheDocument();
    });
  });
});
