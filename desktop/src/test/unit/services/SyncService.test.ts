/**
 * Unit tests for SyncService — Task 2.3
 * Tests auth gate, book file sync, and state sync flow.
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';

// ---- Mock control variables (set before each test) ----
let mockIsSignedIn = vi.fn<() => boolean>();
let mockGDriveUpload = vi.fn();
let mockGDriveDownload = vi.fn();
let mockGDriveList = vi.fn();
let mockPushState = vi.fn();
let mockPullState = vi.fn();
let mockListStateFiles = vi.fn();
let mockListBooks = vi.fn();
let mockGetProgress = vi.fn();
let mockUpsertProgress = vi.fn();
let mockUpsertBook = vi.fn();
let mockListHighlights = vi.fn();
let mockListBookmarks = vi.fn();
let mockSaveHighlight = vi.fn();
let mockSaveBookmark = vi.fn();
let mockFileExists = vi.fn();
let mockSaveBookFile = vi.fn();
let mockGetFileBytes = vi.fn();
let mockDeleteHighlight = vi.fn();
let mockDeleteBookmark = vi.fn();

// ---- Mock layers (must be hoisted by vitest) ----

vi.mock('$lib/stores/authState.svelte.ts', () => ({
  authState: {
    get isSignedIn(): boolean {
      return mockIsSignedIn();
    },
  },
}));

vi.mock('$lib/shared/services/storage/GDriveProvider', () => {
  return {
    GDriveProvider: vi.fn(function () {
      return {
        upload: mockGDriveUpload,
        download: mockGDriveDownload,
        list: mockGDriveList,
      };
    }),
  };
});

vi.mock('$lib/shared/services/GoogleDriveStateSync', () => ({
  GoogleDriveStateSync: {
    pushState: (...args: unknown[]) => mockPushState(...args),
    pullState: (...args: unknown[]) => mockPullState(...args),
    listStateFiles: () => mockListStateFiles(),
  },
}));

vi.mock('$lib/shared/api/tauriClient', () => ({
  listBooks: () => mockListBooks(),
  getProgress: (bookId: string) => mockGetProgress(bookId),
  upsertProgress: (p: unknown) => mockUpsertProgress(p),
  upsertBook: (b: unknown) => mockUpsertBook(b),
  listHighlights: (bookId?: string) => mockListHighlights(bookId),
  listBookmarks: (bookId?: string) => mockListBookmarks(bookId),
  saveHighlight: (h: unknown) => mockSaveHighlight(h),
  saveBookmark: (b: unknown) => mockSaveBookmark(b),
  deleteHighlight: (id: string) => mockDeleteHighlight(id),
  deleteBookmark: (id: string) => mockDeleteBookmark(id),
  fileExists: (path: string) => mockFileExists(path),
  saveBookFile: (id: string, data: number[]) => mockSaveBookFile(id, data),
  getFileBytes: (path: string) => mockGetFileBytes(path),
}));

// Dynamic import
let SyncService: typeof import('$lib/shared/services/SyncService').SyncService;

beforeAll(async () => {
  const mod = await import('$lib/shared/services/SyncService');
  SyncService = mod.SyncService;
});

beforeEach(() => {
  vi.clearAllMocks();
  // Defaults
  mockIsSignedIn.mockReturnValue(false);
  mockListBooks.mockResolvedValue([]);
  mockGetProgress.mockResolvedValue(null);
  mockListHighlights.mockResolvedValue([]);
  mockListBookmarks.mockResolvedValue([]);
  mockGDriveList.mockResolvedValue([]);
  mockGDriveDownload.mockRejectedValue(new Error('not found'));
  mockGDriveUpload.mockResolvedValue('file-id');
  mockPushState.mockResolvedValue(undefined);
  mockPullState.mockResolvedValue({ progress: null, highlights: [], bookmarks: [] });
  mockListStateFiles.mockResolvedValue([]);
  mockFileExists.mockResolvedValue(true);
  mockUpsertProgress.mockResolvedValue(undefined);
  mockUpsertBook.mockResolvedValue(undefined);
  mockSaveHighlight.mockResolvedValue(undefined);
  mockSaveBookmark.mockResolvedValue(undefined);
  mockDeleteHighlight.mockResolvedValue(undefined);
  mockDeleteBookmark.mockResolvedValue(undefined);
  mockSaveBookFile.mockResolvedValue(undefined);
  mockGetFileBytes.mockResolvedValue([1, 2, 3]);
});

function makeLocalBook(id: string, title: string, filePath: string) {
  return {
    id,
    title,
    author: 'Test Author',
    filePath,
    format: 'epub',
    syncStatus: 'local',
    currentPage: 0,
    totalPages: 0,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-06-01T00:00:00Z',
  };
}

describe('SyncService — auth gate', () => {
  it('syncMetadata does nothing when not signed in', async () => {
    mockIsSignedIn.mockReturnValue(false);

    await SyncService.syncMetadata();

    expect(mockListBooks).not.toHaveBeenCalled();
    expect(mockGDriveList).not.toHaveBeenCalled();
  });

  it('syncMetadata proceeds when signed in', async () => {
    mockIsSignedIn.mockReturnValue(true);

    await SyncService.syncMetadata();

    expect(mockListBooks).toHaveBeenCalled();
  });

  it('shares one in-flight metadata sync across concurrent callers', async () => {
    mockIsSignedIn.mockReturnValue(true);
    let releaseDriveList!: () => void;
    mockGDriveList.mockImplementation(
      () => new Promise<string[]>((resolve) => { releaseDriveList = () => resolve([]); }),
    );

    const first = SyncService.syncMetadata();
    const second = SyncService.syncMetadata();

    expect(mockGDriveList).toHaveBeenCalledTimes(1);
    releaseDriveList();
    await Promise.all([first, second]);
  });
});

describe('SyncService — book file sync', () => {
  it('downloads missing book files from Drive', async () => {
    mockIsSignedIn.mockReturnValue(true);

    const localBook = makeLocalBook('book-1', 'Test Book', '/tmp/book-1.epub');
    mockListBooks.mockResolvedValue([localBook]);
    mockGDriveList.mockResolvedValue(['book-1.epub']); // remote has book file
    mockFileExists.mockResolvedValue(false); // NOT locally

    // State sync shouldn't interfere — no remote state
    // pullState will download 'book-1_state.json' which fails (default reject)
    // so it returns local state. But mockGDriveDownload is shared.
    // Since syncState calls GoogleDriveStateSync (mocked), the mockPullState
    // intercepts and prevents actual download. Only syncBooks calls gdrive.download.

    // Need to make the shared mockGDriveDownload work for syncBooks but not break state sync.
    // Since state sync uses mock GoogleDriveStateSync (not real gdrive), this is fine.
    // The mockGoogleDriveStateSync's pullState doesn't call gdrive.download at all!

    // Set up download for the book file (syncBooks path)
    const bookBytes = new Uint8Array([1, 2, 3]);

    // Reset mockGDriveDownload before syncBooks runs
    let downloadCallCount = 0;
    mockGDriveDownload.mockImplementation(async (_name: string) => {
      downloadCallCount++;
      return bookBytes;
    });

    await SyncService.syncMetadata();

    // syncBooks should have called download for the book file
    expect(downloadCallCount).toBeGreaterThanOrEqual(1);
    // saveBookFile should have been called
    expect(mockSaveBookFile).toHaveBeenCalled();
  });

  it('skips download when file exists locally', async () => {
    mockIsSignedIn.mockReturnValue(true);

    const localBook = makeLocalBook('book-2', 'Existing Book', '/tmp/book-2.epub');
    mockListBooks.mockResolvedValue([localBook]);
    mockGDriveList.mockResolvedValue(['book-2.epub']);
    mockFileExists.mockResolvedValue(true);

    let downloadBookCallCount = 0;
    mockGDriveDownload.mockImplementation(async (name: string) => {
      // Only count book file downloads, not state.json downloads
      if (!name.endsWith('_state.json')) {
        downloadBookCallCount++;
      }
      return new Uint8Array();
    });

    await SyncService.syncMetadata();

    // No book file should be downloaded since it exists locally
    expect(downloadBookCallCount).toBe(0);
    expect(mockSaveBookFile).not.toHaveBeenCalled();
  });
});

describe('SyncService — state sync', () => {
  it('pushes local state then pulls remote state for each book', async () => {
    mockIsSignedIn.mockReturnValue(true);

    const localBook = makeLocalBook('book-3', 'State Book', '/tmp/book-3.epub');
    mockListBooks.mockResolvedValue([localBook]);

    mockGetProgress.mockResolvedValue({
      id: 'prog-1',
      bookId: 'book-3',
      cfiLocation: '/6/4',
      percentage: 42,
      updatedAt: '2025-06-01T00:00:00Z',
    });

    mockPullState.mockResolvedValue({
      progress: {
        id: 'prog-1',
        book_id: 'book-3',
        cfi_location: '/6/10',
        percentage: 75,
        updated_at: 2000000,
      },
      highlights: [],
      bookmarks: [],
    });

    // Suppress book sync noise
    mockGDriveList.mockResolvedValue([]);

    await SyncService.syncMetadata();

    expect(mockPushState).toHaveBeenCalled();
    expect(mockPullState).toHaveBeenCalled();
    expect(mockUpsertProgress).toHaveBeenCalled();
  });

  it('syncs highlights and bookmarks via state.json', async () => {
    mockIsSignedIn.mockReturnValue(true);

    const localBook = makeLocalBook('book-4', 'Rich Book', '/tmp/book-4.epub');
    mockListBooks.mockResolvedValue([localBook]);

    mockListHighlights.mockResolvedValue([
      {
        id: 'h1',
        bookId: 'book-4',
        text: 'Great quote',
        color: '#ffff00',
        pageNumber: 10,
        note: null,
        createdAt: '2025-01-01T00:00:00Z',
      },
    ]);

    mockListBookmarks.mockResolvedValue([
      {
        id: 'b1',
        bookId: 'book-4',
        pageNumber: 15,
        title: 'Chapter 2',
        createdAt: '2025-01-01T00:00:00Z',
      },
    ]);

    mockGetProgress.mockResolvedValue(null);

    mockPullState.mockResolvedValue({
      progress: null,
      highlights: [
        {
          id: 'h2',
          book_id: 'book-4',
          cfi_range: '/6/20',
          text_content: 'Remote quote',
          note: 'my note',
          color: '#00ff00',
          updated_at: 3000000,
          deleted_at: null,
          recordId: 'h2',
          updatedAtEpochMillis: 3000000,
          deletedAtEpochMillis: null,
        },
      ],
      bookmarks: [],
    });

    mockGDriveList.mockResolvedValue([]);

    await SyncService.syncMetadata();

    expect(mockPushState).toHaveBeenCalled();
    expect(mockSaveHighlight).toHaveBeenCalled();
  });
});
