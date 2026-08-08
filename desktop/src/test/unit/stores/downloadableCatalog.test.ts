/**
 * Unit tests for the downloadableCatalog store — T-03 (Batch 2 / PR 2).
 *
 * Covers `loadAvailableFromDrive()` (Drive listing with local-library
 * exclusion and unparseable-name filtering) and `downloadBook()` without a
 * pre-existing Drive token (GDriveProvider's refresh fallback handles auth —
 * the store performs no manual `getDriveToken` check).
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGDriveList = vi.hoisted(() => vi.fn());
const mockGDriveDownload = vi.hoisted(() => vi.fn());
const mockListLibraryBooks = vi.hoisted(() => vi.fn());
const mockSaveBookFile = vi.hoisted(() => vi.fn());
const mockCatalogUpsertBook = vi.hoisted(() => vi.fn());
const mockCatalogFindByHash = vi.hoisted(() => vi.fn());

vi.mock('$lib/shared/services/storage/GDriveProvider', () => ({
  GDriveProvider: vi.fn(function () {
    return { list: mockGDriveList, download: mockGDriveDownload };
  }),
}));

vi.mock('$lib/shared/api/tauriClient', () => ({
  listLibraryBooks: mockListLibraryBooks,
  saveBookFile: mockSaveBookFile,
}));

vi.mock('$lib/shared/sync/SupabaseBookCatalogSync', () => ({
  SupabaseBookCatalogSync: vi.fn(function () {
    return { upsertBook: mockCatalogUpsertBook, findByHash: mockCatalogFindByHash };
  }),
}));

// No pre-existing Drive token — the provider's refresh fallback must cover it.
vi.mock('$lib/shared/services/SupabaseAuthService', () => ({
  getDriveToken: vi.fn().mockResolvedValue(null),
  refreshDriveToken: vi.fn().mockResolvedValue('fresh-token'),
}));

// No live user session → downloadBook's catalog upsert must be a no-op.
vi.mock('$lib/stores/authState.svelte.ts', () => ({
  authState: {
    get userId(): string | null {
      return null;
    },
  },
}));

import {
  downloadableCatalog,
  clearDownloadableBooks,
  loadAvailableFromDrive,
  downloadBook,
} from '$lib/stores/downloadableCatalog.svelte';

describe('downloadableCatalog — loadAvailableFromDrive (REQ-01)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearDownloadableBooks();
    downloadableCatalog.clearDownloadError();
    mockGDriveList.mockResolvedValue([]);
    mockListLibraryBooks.mockResolvedValue([]);
  });

  it('lists Drive books absent from the local library (SCN-02)', async () => {
    mockGDriveList.mockResolvedValue(['book-1.epub', 'book-2.pdf', 'book-3.mobi']);
    mockListLibraryBooks.mockResolvedValue([{ id: 'book-2' }]);

    await loadAvailableFromDrive();

    expect(mockGDriveList).toHaveBeenCalledWith('');
    expect(downloadableCatalog.books).toEqual([
      { id: 'book-1', ext: 'epub', remoteName: 'book-1.epub', displayTitle: 'book-1' },
      { id: 'book-3', ext: 'mobi', remoteName: 'book-3.mobi', displayTitle: 'book-3' },
    ]);
  });

  it('drops unparseable names (sync state, no dot, trailing dot)', async () => {
    mockGDriveList.mockResolvedValue([
      'book-1.epub',
      'catalog_state.json',
      'noext',
      'trailing.',
    ]);
    mockListLibraryBooks.mockResolvedValue([]);

    await loadAvailableFromDrive();

    expect(downloadableCatalog.books).toEqual([
      { id: 'book-1', ext: 'epub', remoteName: 'book-1.epub', displayTitle: 'book-1' },
    ]);
  });

  it('surfaces listing failures in the banner without clearing prior books', async () => {
    mockGDriveList.mockRejectedValue(new Error('GDrive List Failed: boom'));

    await loadAvailableFromDrive();

    expect(downloadableCatalog.error).toContain('boom');
    expect(downloadableCatalog.books).toEqual([]);
  });
});

describe('downloadableCatalog — downloadBook without a token (REQ-02, SCN-03)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearDownloadableBooks();
    downloadableCatalog.clearDownloadError();
    mockGDriveList.mockResolvedValue(['Mi.Libro.PDF']);
    mockListLibraryBooks.mockResolvedValue([]);
  });

  it('downloads using the original Drive name and removes the book on success', async () => {
    await loadAvailableFromDrive();
    expect(downloadableCatalog.books).toEqual([
      { id: 'Mi.Libro', ext: 'pdf', remoteName: 'Mi.Libro.PDF', displayTitle: 'Mi.Libro' },
    ]);

    mockGDriveDownload.mockResolvedValue(new Uint8Array([1, 2, 3, 4]));
    mockSaveBookFile.mockResolvedValue(undefined);

    await downloadBook('Mi.Libro');

    // The download closure uses the ORIGINAL filename (non-canonical-safe),
    // not the canonical ref resolved from the synthetic row.
    expect(mockGDriveDownload).toHaveBeenCalledWith('Mi.Libro.PDF');
    expect(mockSaveBookFile).toHaveBeenCalledWith('Mi.Libro', [1, 2, 3, 4], {
      title: 'Mi.Libro',
      author: '',
      format: 'pdf',
    });
    // No live session → markImported is a no-op (no catalog upsert).
    expect(mockCatalogUpsertBook).not.toHaveBeenCalled();
    expect(downloadableCatalog.books).toEqual([]);
  });
});
