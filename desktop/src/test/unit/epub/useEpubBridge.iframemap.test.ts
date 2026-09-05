/**
 * PR1 signal — iframe error forward mapping (tasks 1.1–1.2). Covers
 * `truncateIframeMsg` / `basename` + `mapIframeMessageToError` (the thin
 * `handleIframeMessage` branch delegates to it), and asserts the injected
 * chapter script captures `onerror` AND `unhandledrejection` with `kind`.
 */
import { describe, expect, it, vi } from 'vitest';

vi.mock('@tauri-apps/api/core', () => ({
  invoke: vi.fn(),
  convertFileSrc: vi.fn((path: string) => `asset://localhost/${String(path).replace(/\\/g, '/')}`),
}));

import {
  truncateIframeMsg,
  basename,
  mapIframeMessageToError,
} from '$lib/features/reader/viewer-epub/useEpubBridge.svelte';
import { buildChapterSrcdoc } from '$lib/features/reader/viewer-epub/useEpubRender.svelte';

describe('truncateIframeMsg / basename', () => {
  it('passes short messages through, truncates 500-char to 200', () => {
    expect(truncateIframeMsg('boom', 200)).toBe('boom');
    expect(truncateIframeMsg('x'.repeat(500), 200)).toHaveLength(200);
  });

  it('strips POSIX/Windows dirs, passes bare names', () => {
    expect(basename('/Users/juan/Library/OEBPS/Text/ch1.xhtml')).toBe('ch1.xhtml');
    expect(basename('C:\\Books\\lib\\ch2.xhtml')).toBe('ch2.xhtml');
    expect(basename('ch3.xhtml')).toBe('ch3.xhtml');
  });
});

describe('mapIframeMessageToError', () => {
  it('maps a JS error to ReaderError epub/iframe_error + book context', () => {
    const err = mapIframeMessageToError(
      { msg: 'Uncaught TypeError: x is null', url: 'OEBPS/Text/ch1.xhtml', line: 12, col: 3, kind: 'js' },
      'book-1',
      4,
    );
    expect(err.code).toBe('READER_IFRAME_ERROR');
    expect(err.context).toMatchObject({
      format: 'epub', action: 'iframe_error', kind: 'js', iframeSource: 'ch1.xhtml',
      line: 12, col: 3, bookId: 'book-1', chapterIndex: 4,
    });
  });

  it('maps rejections, truncates, basenames, defaults missing fields (fixed keys)', () => {
    const err = mapIframeMessageToError(
      { msg: 'y'.repeat(500), url: '/data/user/0/books/OEBPS/Text/ch9.xhtml', kind: 'rejection' },
      'book-3',
      0,
    );
    expect(err.message.length).toBeLessThanOrEqual(200);
    expect(err.context).toMatchObject({ kind: 'rejection', iframeSource: 'ch9.xhtml', line: 0, col: 0 });
    expect(Object.keys(err.context).sort()).toEqual(
      ['action', 'bookId', 'chapterIndex', 'col', 'format', 'iframeSource', 'kind', 'line'].sort(),
    );
  });
});

describe('injected errorScript (task 1.1)', () => {
  it('captures onerror AND unhandledrejection with kind discriminator', () => {
    const srcdoc = buildChapterSrcdoc(
      {
        index: 0,
        html: '<html><head></head><body><p>hi</p></body></html>',
        mime: 'application/xhtml+xml',
        chapterBasePath: '',
        chapterPath: 'OEBPS/Text/ch1.xhtml',
      },
      '/tmp/resources',
      ['OEBPS/Text/ch1.xhtml'],
      'OEBPS/Text/ch1.xhtml',
      0,
    );
    expect(srcdoc).toContain('unhandledrejection');
    expect(srcdoc).toContain("kind:'js'");
    expect(srcdoc).toContain("kind:'rejection'");
  });
});
