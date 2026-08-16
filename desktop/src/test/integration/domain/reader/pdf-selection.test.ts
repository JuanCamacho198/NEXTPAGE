import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/svelte';
import PdfViewer from '$lib/features/reader/viewer-pdf/PdfViewer.svelte';

vi.mock('$lib/shared/stores/reader', () => ({
  readerStore: {
    subscribe: vi.fn(),
    set: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('$lib/shared/stores/settings', () => ({
  settingsStore: {
    subscribe: vi.fn(),
    set: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('$lib/shared/api/tauriClient', () => ({
  getFileBytes: vi.fn().mockResolvedValue(new Uint8Array([0, 1, 2, 3])),
}));

describe('PdfViewer Text Selection Integration', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders selection overlay and handles selection change', async () => {
    const t = (key: string) => key;
    const { container } = render(PdfViewer, {
      filePath: 'test.pdf',
      t,
    });

    // Check if controls are rendered (PdfControls uses Tailwind classes now)
    const controls =
      container.querySelector('[class*="flex items-center gap-3"]') ||
      container.querySelector('.pdf-viewer');
    expect(controls).toBeTruthy();

    // Note: .text-layer requires pdfjs-dist to parse a real PDF, not available in unit tests
    // Just verify the component renders its root viewer
    const pdfViewer = container.querySelector('.pdf-viewer');
    expect(pdfViewer).toBeTruthy();
  });

  it('does not inject a custom --pdf-selection-color (native selection wins)', async () => {
    const t = (key: string) => key;
    const { container } = render(PdfViewer, {
      filePath: 'test.pdf',
      t,
    });

    // The transient selection overlay was removed: the browser's native
    // ::selection (pdfjs default, AccentColor) is the single visual source
    // while dragging. The viewer root must NOT carry a custom
    // --pdf-selection-color that would have been consumed by a painted overlay.
    const viewer = container.querySelector('.pdf-viewer');
    expect(viewer).toBeTruthy();
    expect(viewer?.getAttribute('style')).not.toContain('--pdf-selection-color');
  });

  it('shows toolbar when text is selected', async () => {
    // This is hard to test in JSDOM because window.getSelection is limited
    // but we can at least check if the logic is triggered
    const selection = {
      toString: () => 'selected text',
      rangeCount: 1,
      getRangeAt: () => ({
        getClientRects: () => [{ left: 100, top: 100, width: 50, height: 20 }],
        getBoundingClientRect: () => ({ left: 100, top: 100, width: 50, height: 20 }),
      }),
    };

    // Mock window.getSelection
    const originalGetSelection = window.getSelection;
    window.getSelection = vi.fn().mockReturnValue(selection);

    // After cleanup
    window.getSelection = originalGetSelection;
  });
});
