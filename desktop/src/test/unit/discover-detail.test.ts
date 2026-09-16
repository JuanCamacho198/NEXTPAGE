import { fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { describe, expect, it, vi } from 'vitest';
import DiscoverDetail from '$lib/features/discover/DiscoverDetail.svelte';
import {
  DiscoverDomainState,
  type DiscoverDownloadState,
} from '$lib/features/discover/DiscoverDomainState.svelte';
import {
  discoverDescription,
  discoverExternalLink,
  discoverFormatLabels,
} from '$lib/features/discover/discoverDetailFormat';
import type { MessageKey } from '$lib/shared/i18n/messages.en';
import type { CatalogBook, CatalogProvider } from '$lib/shared/services/catalog/CatalogProvider';
import { catalogError } from '$lib/shared/services/catalog/errors';
import { importRecoveredBook } from '$lib/shared/recovery/desktopRecoveryImport';

const t = (key: MessageKey): string => key;

function fakeBook(overrides: Partial<CatalogBook> = {}): CatalogBook {
  return {
    id: 'gutendex:1342',
    provider: 'builtin:gutendex',
    title: 'Pride and Prejudice',
    authors: ['Jane Austen'],
    coverUrl: 'https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg',
    languages: ['en'],
    subjects: ['Fiction'],
    downloadUrl: 'https://www.gutenberg.org/ebooks/1342.epub3.images',
    description: 'A classic novel.',
    formats: {
      'application/epub+zip': 'https://www.gutenberg.org/ebooks/1342.epub3.images',
      'application/pdf': 'https://www.gutenberg.org/files/1342/1342-pdf.pdf',
      'unknown-xyz': 'https://example.com/unknown',
    },
    ...overrides,
  };
}

function fakeProvider(books: Record<string, CatalogBook>): CatalogProvider {
  return {
    async search() {
      return { results: [], nextPage: null, totalCount: 0 };
    },
    async getDetails(id: string) {
      const book = books[id];
      if (!book) throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
      return book;
    },
    resolveDownloadUrl() {
      throw catalogError('UNAVAILABLE_DOWNLOAD', 'no usable url');
    },
    listSources() {
      return [];
    },
    async featured() {
      return { results: [], nextPage: null, totalCount: 0 };
    },
    supportsFeatured() {
      return false;
    },
    async searchSource() {
      return { results: [], nextPage: null, totalCount: 0 };
    },
  };
}

function okFetch(bytes: number[] = [1, 2, 3]): typeof fetch {
  return (async () =>
    new Response(new Uint8Array(bytes), {
      status: 200,
      headers: { 'content-length': String(bytes.length) },
    })) as typeof fetch;
}

function hangingFetch(): typeof fetch {
  return ((_: unknown, init?: RequestInit) => {
    if (init?.signal?.aborted) return Promise.reject(new DOMException('Aborted', 'AbortError'));
    return new Promise<Response>((_, reject) => {
      init?.signal?.addEventListener('abort', () => {
        reject(new DOMException('Aborted', 'AbortError'));
      });
    });
  }) as typeof fetch;
}

function importedStub(): typeof importRecoveredBook {
  return (async () => ({
    bookId: 'gutendex:1342',
    outcome: 'imported' as const,
  })) as typeof importRecoveredBook;
}

describe('discoverDetailFormat pure helpers', () => {
  it('maps known formats in stable order and skips unknown keys', () => {
    expect(
      discoverFormatLabels({
        'unknown-xyz': 'https://example.com/u',
        'application/pdf': 'https://example.com/p.pdf',
        'application/epub+zip': 'https://example.com/b.epub',
      }),
    ).toEqual(['EPUB', 'PDF']);
  });

  it('dedupes TXT variants and maps MOBI/HTML', () => {
    expect(
      discoverFormatLabels({
        'text/plain': 'https://example.com/b.txt',
        'text/plain; charset=utf-8': 'https://example.com/b2.txt',
        'application/x-mobipocket-ebook': 'https://example.com/b.mobi',
        'text/html': 'https://example.com/b.html',
      }),
    ).toEqual(['MOBI', 'TXT', 'HTML']);
  });

  it('empty or undefined formats hide the row', () => {
    expect(discoverFormatLabels({})).toEqual([]);
    expect(discoverFormatLabels(undefined)).toEqual([]);
  });

  it('gates external links to Gutenberg/Open Library ids', () => {
    expect(discoverExternalLink(fakeBook())).toEqual({
      url: 'https://www.gutenberg.org/ebooks/1342',
      kind: 'gutenberg',
    });
    expect(
      discoverExternalLink(
        fakeBook({ id: 'openlibrary:/works/OL1W', provider: 'builtin:openlibrary' }),
      ),
    ).toEqual({ url: 'https://openlibrary.org/works/OL1W', kind: 'openlibrary' });
    expect(discoverExternalLink(fakeBook({ id: 'gutendex:abc' }))).toBeNull();
    expect(
      discoverExternalLink(fakeBook({ id: 'openlibrary:OL1W', provider: 'builtin:openlibrary' })),
    ).toBeNull();
    expect(
      discoverExternalLink(fakeBook({ id: 'googlebooks:xyz', provider: 'builtin:googlebooks' })),
    ).toBeNull();
    expect(discoverExternalLink(fakeBook({ id: 'curated:x', provider: 'curated' }))).toBeNull();
  });

  it('blank description hides the block', () => {
    expect(discoverDescription(fakeBook({ description: undefined }))).toBeUndefined();
    expect(discoverDescription(fakeBook({ description: '   ' }))).toBeUndefined();
    expect(discoverDescription(fakeBook({ description: 'A novel.' }))).toBe('A novel.');
  });
});

describe('DiscoverDomainState download-to-import (WU2)', () => {
  it('successful download imports and reports progress', async () => {
    const book = fakeBook();
    const fetchFn = vi.fn(okFetch());
    const importFn = vi.fn(importedStub());
    const state = new DiscoverDomainState(fakeProvider({ [book.id]: book }), { fetchFn, importFn });
    await state.openDetail(book.id);
    await state.startDownload();
    expect(fetchFn).toHaveBeenCalledWith(book.downloadUrl, expect.anything());
    expect(state.downloadState).toBe('imported');
    expect(state.downloadError).toBeNull();
    expect(state.progressBytes).toBe(3);
    expect(state.progressTotal).toBe(3);
    expect(importFn).toHaveBeenCalledOnce();
  });

  it('cancel aborts the fetch and never reaches persistence', async () => {
    const book = fakeBook();
    const importFn = vi.fn(importedStub());
    const state = new DiscoverDomainState(fakeProvider({ [book.id]: book }), {
      fetchFn: hangingFetch(),
      importFn,
    });
    await state.openDetail(book.id);
    const pending = state.startDownload();
    await tick();
    await tick();
    expect(state.downloadState).toBe('downloading');
    state.cancelDownload();
    await pending;
    expect(state.downloadState).toBe('cancelled');
    expect(importFn).not.toHaveBeenCalled();
  });

  it('failed download offers retry that recovers', async () => {
    const book = fakeBook();
    let failing = true;
    const fetchFn = vi.fn((async () => {
      if (failing) throw new Error('boom');
      return new Response(new Uint8Array([1, 2, 3]), {
        status: 200,
        headers: { 'content-length': '3' },
      });
    }) as typeof fetch);
    const state = new DiscoverDomainState(fakeProvider({ [book.id]: book }), {
      fetchFn,
      importFn: importedStub(),
    });
    await state.openDetail(book.id);
    await state.startDownload();
    expect(state.downloadState).toBe('error');
    expect(state.downloadError).toContain('boom');
    failing = false;
    await state.retryDownload();
    expect(state.downloadState).toBe('imported');
  });

  it('missing download URL fails closed without I/O', async () => {
    const book = fakeBook({ downloadUrl: null });
    const fetchFn = vi.fn(okFetch());
    const state = new DiscoverDomainState(fakeProvider({ [book.id]: book }), {
      fetchFn,
      importFn: importedStub(),
    });
    await state.openDetail(book.id);
    await state.startDownload();
    expect(state.downloadState).toBe('error');
    expect(state.downloadError).toBe('UNAVAILABLE_DOWNLOAD');
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it('openDetail and dismissDetail reset the transfer state', async () => {
    const first = fakeBook();
    const second = fakeBook({ id: 'gutendex:11', title: 'Second' });
    const state = new DiscoverDomainState(
      fakeProvider({ [first.id]: first, [second.id]: second }),
      { fetchFn: okFetch(), importFn: importedStub() },
    );
    await state.openDetail(first.id);
    await state.startDownload();
    expect(state.downloadState).toBe('imported');
    await state.openDetail(second.id);
    expect(state.downloadState).toBe('idle');
    expect(state.progressBytes).toBe(0);
    expect(state.progressTotal).toBeNull();
    await state.startDownload();
    state.dismissDetail();
    expect(state.detailStatus).toBe('closed');
    expect(state.downloadState).toBe('idle');
  });
});

describe('DiscoverDetail modal (WU2)', () => {
  it('renders cover badge, description, format chips, and external link', async () => {
    const onDismiss = vi.fn();
    render(DiscoverDetail, { props: { detail: fakeBook(), detailStatus: 'loaded', t, onDismiss } });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    // Title renders twice: modal header + detail heading.
    expect(screen.getAllByText('Pride and Prejudice')).toHaveLength(2);
    expect(screen.getByText('Gutenberg')).toBeInTheDocument();
    expect(screen.getByText('A classic novel.')).toBeInTheDocument();
    expect(screen.getByText('EPUB')).toBeInTheDocument();
    expect(screen.getByText('PDF')).toBeInTheDocument();
    expect(screen.queryByText('unknown-xyz')).not.toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      'https://www.gutenberg.org/ebooks/1342',
    );
    expect(screen.getByText('discover.download')).toBeInTheDocument();
  });

  it('hides formats row, external link, download CTA, and description when absent', async () => {
    const onDismiss = vi.fn();
    render(DiscoverDetail, {
      props: {
        detail: fakeBook({
          id: 'googlebooks:xyz',
          provider: 'builtin:googlebooks',
          description: '  ',
          formats: {},
          downloadUrl: null,
        }),
        detailStatus: 'loaded',
        t,
        onDismiss,
      },
    });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.queryByText('discover.formats')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByText('discover.download')).not.toBeInTheDocument();
  });

  it('renders downloading progress with cancel and terminal states', async () => {
    const onCancelDownload = vi.fn();
    const onRetryDownload = vi.fn();
    const base = { detail: fakeBook(), detailStatus: 'loaded' as const, t, onDismiss: vi.fn() };
    const { unmount } = render(DiscoverDetail, {
      props: {
        ...base,
        downloadState: 'downloading' as DiscoverDownloadState,
        progressBytes: 50,
        progressTotal: 100,
        onCancelDownload,
      },
    });
    expect(await screen.findByText('discover.downloading')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50');
    await fireEvent.click(screen.getByText('discover.downloadCancel'));
    expect(onCancelDownload).toHaveBeenCalledOnce();
    unmount();

    render(DiscoverDetail, { props: { ...base, downloadState: 'imported' } });
    expect(await screen.findByText('discover.imported')).toBeInTheDocument();
  });

  it('Escape closes through onDismiss (reset-on-close)', async () => {
    const onDismiss = vi.fn();
    render(DiscoverDetail, { props: { detail: fakeBook(), detailStatus: 'loaded', t, onDismiss } });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await fireEvent.keyDown(window, { key: 'Escape' });
    await tick();
    expect(onDismiss).toHaveBeenCalled();
  });
});
