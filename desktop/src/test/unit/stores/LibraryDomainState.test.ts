import { describe, expect, it, vi, beforeEach } from 'vitest';
import { libraryState } from '$lib/shared/stores/LibraryDomainState.svelte';

// ─── Hoisted mock factories ───

const mockListLibraryBooks = vi.hoisted(() => vi.fn());
const mockListBooks = vi.hoisted(() => vi.fn());
const mockListCollections = vi.hoisted(() => vi.fn());
const mockUpsertBook = vi.hoisted(() => vi.fn());
const mockUpsertBookCover = vi.hoisted(() => vi.fn());
const mockHideBookFromLibrary = vi.hoisted(() => vi.fn());
const mockUpdateBookProgress = vi.hoisted(() => vi.fn());
const mockSaveProgress = vi.hoisted(() => vi.fn());
const mockExtractEpubCover = vi.hoisted(() => vi.fn());
const mockExtractPdfMetadata = vi.hoisted(() => vi.fn());
const mockRecordMetric = vi.hoisted(() => vi.fn());

// ─── Module mocks ───

vi.mock('$lib/shared/api/tauriClient', () => ({
  listLibraryBooks: mockListLibraryBooks,
  listBooks: mockListBooks,
  listCollections: mockListCollections,
  upsertBook: mockUpsertBook,
  upsertBookCover: mockUpsertBookCover,
  hideBookFromLibrary: mockHideBookFromLibrary,
  updateBookProgress: mockUpdateBookProgress,
  saveProgress: mockSaveProgress,
  extractEpubCover: mockExtractEpubCover,
}));

vi.mock('$lib/shared/services/pdfThumbnail', () => ({
  extractPdfMetadata: mockExtractPdfMetadata,
}));

vi.mock('$lib/shared/logger/MetricsStore', () => ({
  recordMetric: mockRecordMetric,
}));

vi.mock('$lib/shared/logger/metricTypes', () => ({
  METRIC_NAMES: { READER_OPEN: 'reader_open' },
}));

// ─── Helpers ───

type BookLike = {
  id: string;
  title: string;
  author: string;
  filePath: string;
  format: string;
  currentPage: number;
  totalPages: number;
  progressPercentage: number;
  coverPath: string | null;
  minutesRead: number;
  updatedAt: string;
  collectionIds: number[];
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
};

const makeBook = (overrides: Partial<BookLike> = {}): BookLike => ({
  id: 'book-1',
  title: 'Test Book',
  author: 'Test Author',
  filePath: '/test/book.pdf',
  format: 'pdf',
  currentPage: 0,
  totalPages: 100,
  progressPercentage: 0,
  coverPath: null,
  minutesRead: 0,
  updatedAt: '2026-01-01T00:00:00.000Z',
  collectionIds: [],
  isFavorite: false,
  toRead: false,
  completed: false,
  ...overrides,
});

const makeLibraryRow = (overrides: Partial<BookLike> = {}) => {
  const b = makeBook(overrides);
  return {
    id: b.id,
    title: b.title,
    author: b.author,
    format: b.format,
    currentPage: b.currentPage,
    totalPages: b.totalPages,
    progressPercentage: b.progressPercentage,
    coverPath: b.coverPath,
    minutesRead: b.minutesRead,
    updatedAt: b.updatedAt,
    collectionIds: b.collectionIds,
  };
};

const makeSourceRow = (overrides: Partial<BookLike> = {}) => {
  const b = makeBook(overrides);
  return {
    id: b.id,
    filePath: b.filePath,
    title: b.title,
    author: b.author,
    format: b.format,
    syncStatus: 'local',
    currentPage: b.currentPage,
    totalPages: b.totalPages,
  };
};

function resetLibraryState(): void {
  libraryState.books = [];
  libraryState.collections = [];
  libraryState.isLoadingLibrary = false;
  libraryState.readerError = null;
  libraryState.editingBook = null;
  libraryState.isCollectionManagerOpen = false;
  libraryState.setShelfTab('all');
  libraryState.setShelfSort('date');
  libraryState.setShelfViewMode('grid');
  libraryState._booksJustChanged = false;
  libraryState._lastRecoverableError = null;
  libraryState.thumbnailGenerationInFlight.clear();
  libraryState.thumbnailGenerationAttempted.clear();
}

function setupDefaultMocks(): void {
  mockListLibraryBooks.mockResolvedValue([]);
  mockListBooks.mockResolvedValue([]);
  mockListCollections.mockResolvedValue([]);
  mockUpsertBook.mockResolvedValue(undefined);
  mockUpsertBookCover.mockResolvedValue(undefined);
  mockHideBookFromLibrary.mockResolvedValue(undefined);
  mockUpdateBookProgress.mockResolvedValue(undefined);
  mockSaveProgress.mockResolvedValue(undefined);
  mockExtractEpubCover.mockResolvedValue(false);
  mockExtractPdfMetadata.mockResolvedValue({
    author: null,
    title: null,
    totalPages: null,
    thumbnailBytes: null,
  });
  mockRecordMetric.mockReturnValue(undefined);
}

// ─── Tests ───

describe('LibraryDomainState', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupDefaultMocks();
    resetLibraryState();
  });

  // ─── Initial state ───

  it('has expected initial state', () => {
    expect(libraryState.books).toEqual([]);
    expect(libraryState.collections).toEqual([]);
    expect(libraryState.isLoadingLibrary).toBe(false);
    expect(libraryState.readerError).toBeNull();
    expect(libraryState.editingBook).toBeNull();
    expect(libraryState.isCollectionManagerOpen).toBe(false);
    expect(libraryState.thumbnailGenerationInFlight.size).toBe(0);
    expect(libraryState.thumbnailGenerationAttempted.size).toBe(0);
  });

  // ─── getBookById ───

  it('getBookById returns null for null/empty id', () => {
    expect(libraryState.getBookById(null)).toBeNull();
    expect(libraryState.getBookById('nonexistent')).toBeNull();
  });

  it('getBookById returns matching book', () => {
    const book = makeBook({ id: 'b1', title: 'Found' });
    libraryState.books = [book as any];
    expect(libraryState.getBookById('b1')?.id).toBe('b1');
    expect(libraryState.getBookById('b1')?.title).toBe('Found');
  });

  // ─── hasResolvedCoverPath ───

  it('hasResolvedCoverPath checks non-empty coverPath', () => {
    expect(libraryState.hasResolvedCoverPath({ coverPath: '/path.jpg' })).toBe(true);
    expect(libraryState.hasResolvedCoverPath({ coverPath: '' })).toBe(false);
    expect(libraryState.hasResolvedCoverPath({ coverPath: '   ' })).toBe(false);
    expect(libraryState.hasResolvedCoverPath({ coverPath: null })).toBe(false);
  });

  // ─── shouldGeneratePdfCover ───

  it('shouldGeneratePdfCover only for PDFs without cover and with filePath', () => {
    expect(
      libraryState.shouldGeneratePdfCover(makeBook({ format: 'pdf', filePath: '/t.pdf' }) as any),
    ).toBe(true);
    expect(libraryState.shouldGeneratePdfCover(makeBook({ format: 'epub' }) as any)).toBe(false);
    expect(
      libraryState.shouldGeneratePdfCover(makeBook({ format: 'pdf', coverPath: '/c.jpg' }) as any),
    ).toBe(false);
    expect(
      libraryState.shouldGeneratePdfCover(makeBook({ format: 'pdf', filePath: '' }) as any),
    ).toBe(false);
  });

  // ─── shouldGenerateEpubCover ───

  it('shouldGenerateEpubCover only for EPUBs without cover and with filePath', () => {
    expect(
      libraryState.shouldGenerateEpubCover(
        makeBook({ format: 'epub', filePath: '/t.epub' }) as any,
      ),
    ).toBe(true);
    expect(libraryState.shouldGenerateEpubCover(makeBook({ format: 'pdf' }) as any)).toBe(false);
    expect(
      libraryState.shouldGenerateEpubCover(
        makeBook({ format: 'epub', coverPath: '/c.jpg' }) as any,
      ),
    ).toBe(false);
    expect(
      libraryState.shouldGenerateEpubCover(makeBook({ format: 'epub', filePath: '' }) as any),
    ).toBe(false);
  });

  // ─── loadLibrary ───

  it('loadLibrary fetches books and collections', async () => {
    const rows = [makeLibraryRow({ id: 'b1', title: 'Book 1', format: 'pdf', collectionIds: [1] })];
    const sourceRows = [makeSourceRow({ id: 'b1', filePath: '/b1.pdf' })];
    const collections = [
      {
        id: 1,
        name: 'Favorites',
        color: 'red',
        isSystem: false,
        createdAt: '2026-01-01T00:00:00.000Z',
      },
    ];

    mockListLibraryBooks.mockResolvedValue(rows);
    mockListBooks.mockResolvedValue(sourceRows);
    mockListCollections.mockResolvedValue(collections);

    await libraryState.loadLibrary();

    expect(mockListLibraryBooks).toHaveBeenCalledWith(1);
    expect(mockListBooks).toHaveBeenCalled();
    expect(mockListCollections).toHaveBeenCalled();
    expect(libraryState.books).toHaveLength(1);
    expect(libraryState.books[0].id).toBe('b1');
    expect(libraryState.books[0].filePath).toBe('/b1.pdf');
    expect(libraryState.collections).toEqual(collections);
    expect(libraryState.isLoadingLibrary).toBe(false);
    expect(libraryState.readerError).toBeNull();
  });

  it('loadLibrary sets readerError on unrecoverable error', async () => {
    const commandError = {
      code: 'DB_ERROR',
      message: 'Database connection failed',
      recoverable: false,
    };
    mockListLibraryBooks.mockRejectedValue({ commandError, message: 'Database connection failed' });

    await libraryState.loadLibrary();

    expect(libraryState.readerError).toBe('Database connection failed');
    expect(libraryState.isLoadingLibrary).toBe(false);
    expect(libraryState._lastRecoverableError).toBeNull();
    // Other mocks still resolve, but Promise.all fails fast
  });

  it('loadLibrary stores recoverable error separately', async () => {
    const commandError = { code: 'RATE_LIMIT', message: 'Too many requests', recoverable: true };
    mockListLibraryBooks.mockRejectedValue({ commandError, message: 'Too many requests' });

    await libraryState.loadLibrary();

    expect(libraryState.readerError).toBeNull();
    expect(libraryState._lastRecoverableError).toEqual({
      code: 'RATE_LIMIT',
      message: 'Too many requests',
    });
  });

  it('loadLibrary handles plain Error without commandError', async () => {
    mockListLibraryBooks.mockRejectedValue(new Error('Network error'));

    await libraryState.loadLibrary();

    expect(libraryState.readerError).toBe('Network error');
  });

  it('loadLibrary sets booksJustChanged flag', async () => {
    const rows = [makeLibraryRow({ id: 'b1', format: 'epub' })];
    const sourceRows = [makeSourceRow({ id: 'b1', filePath: '/b1.epub' })];
    mockListLibraryBooks.mockResolvedValue(rows);
    mockListBooks.mockResolvedValue(sourceRows);

    await libraryState.loadLibrary();

    expect(libraryState.consumeBooksJustChanged()).toBe(true);
    expect(libraryState.consumeBooksJustChanged()).toBe(false);
  });

  it('loadLibrary handles non-Error non-commandError unknown throws', async () => {
    mockListLibraryBooks.mockRejectedValue('some string error');

    await libraryState.loadLibrary();

    expect(libraryState.readerError).toBe('Unknown error');
  });

  // ─── handleHideBook ───

  it('handleHideBook hides book and reloads', async () => {
    const rows = [makeLibraryRow({ id: 'b1', format: 'epub' })];
    mockListLibraryBooks.mockResolvedValue(rows);
    mockListBooks.mockResolvedValue([makeSourceRow({ id: 'b1' })]);

    await libraryState.handleHideBook(makeBook({ id: 'b1' }) as any);

    expect(mockHideBookFromLibrary).toHaveBeenCalledWith('b1');
    expect(mockListLibraryBooks).toHaveBeenCalled();
  });

  it('handleHideBook sets error on failure', async () => {
    mockHideBookFromLibrary.mockRejectedValue(new Error('Cannot hide'));

    await libraryState.handleHideBook(makeBook({ id: 'b1' }) as any);

    expect(libraryState.readerError).toBe('Cannot hide');
  });

  // ─── handleToggleFavorite ───

  it('handleToggleFavorite optimistically updates and persists', async () => {
    const book = makeBook({ id: 'b1', isFavorite: false });
    libraryState.books = [book as any];

    await libraryState.handleToggleFavorite(book as any);

    expect(libraryState.books[0].isFavorite).toBe(true);
    expect(mockUpsertBook).toHaveBeenCalledWith(expect.objectContaining({ id: 'b1' }));
  });

  it('handleToggleFavorite rolls back on failure', async () => {
    const book = makeBook({ id: 'b1', isFavorite: false });
    libraryState.books = [book as any];
    mockUpsertBook.mockRejectedValue(new Error('Save failed'));

    await libraryState.handleToggleFavorite(book as any);

    expect(libraryState.books[0].isFavorite).toBe(false);
    expect(libraryState.readerError).toBe('Save failed');
  });

  // ─── handleMarkCompleted ───

  it('handleMarkCompleted marks PDF book as completed', async () => {
    const book = makeBook({ id: 'b1', format: 'pdf', currentPage: 50, totalPages: 100 });
    libraryState.books = [book as any];
    // loadLibrary runs inside handleMarkCompleted; mock returns progressPercentage: 100
    const row = makeLibraryRow({
      id: 'b1',
      format: 'pdf',
      currentPage: 100,
      totalPages: 100,
      progressPercentage: 100,
    });
    mockListLibraryBooks.mockResolvedValue([row]);
    mockListBooks.mockResolvedValue([makeSourceRow({ id: 'b1', format: 'pdf' })]);

    await libraryState.handleMarkCompleted(book as any);

    expect(mockUpdateBookProgress).toHaveBeenCalledWith('b1', 100);
    // Verify the save was sent AND loadLibrary confirmed progressPercentage from backend
    expect(libraryState.books[0].progressPercentage).toBe(100);
  });

  it('handleMarkCompleted marks EPUB book as completed via saveProgress', async () => {
    const book = makeBook({ id: 'b1', format: 'epub' });
    libraryState.books = [book as any];
    const row = makeLibraryRow({ id: 'b1', format: 'epub', progressPercentage: 100 });
    mockListLibraryBooks.mockResolvedValue([row]);
    mockListBooks.mockResolvedValue([makeSourceRow({ id: 'b1', format: 'epub' })]);

    await libraryState.handleMarkCompleted(book as any);

    expect(mockSaveProgress).toHaveBeenCalledWith({
      bookId: 'b1',
      cfiLocation: '',
      percentage: 100,
    });
    // loadLibrary persists progressPercentage; completed field is DTO-only
    expect(libraryState.books[0].progressPercentage).toBe(100);
  });

  // ─── handleEditBook / handleSaveEditedBook ───

  it('handleEditBook sets editingBook', () => {
    const book = makeBook({ id: 'b1' });
    libraryState.handleEditBook(book as any);
    expect(libraryState.editingBook?.id).toBe('b1');
  });

  it('handleSaveEditedBook updates book and clears editingBook', async () => {
    const book = makeBook({ id: 'b1', title: 'Old Title', author: 'Old Author' });
    libraryState.books = [book as any];

    await libraryState.handleSaveEditedBook({
      id: 'b1',
      title: 'New Title',
      author: 'New Author',
    } as any);

    expect(mockUpsertBook).toHaveBeenCalled();
    expect(libraryState.books[0].title).toBe('New Title');
    expect(libraryState.books[0].author).toBe('New Author');
    expect(libraryState.editingBook).toBeNull();
  });

  // ─── Shelf operations ───

  it('setShelfTab updates tab', () => {
    libraryState.setShelfTab('favorites');
    expect(libraryState.shelfQueryState.tab).toBe('favorites');
  });

  it('setShelfSort updates sort', () => {
    libraryState.setShelfSort('title');
    expect(libraryState.shelfQueryState.sortKey).toBe('title');
  });

  it('setShelfViewMode toggles grid/list', () => {
    libraryState.setShelfViewMode('list');
    expect(libraryState.shelfQueryState.viewMode).toBe('list');
    libraryState.setShelfViewMode('grid');
    expect(libraryState.shelfQueryState.viewMode).toBe('grid');
  });

  it('handleShelfQueryInput updates raw query', () => {
    libraryState.handleShelfQueryInput({ target: { value: 'search text' } } as unknown as Event);
    expect(libraryState.shelfQueryState.rawQuery).toBe('search text');
  });

  it('clearShelfQuery clears raw query', () => {
    libraryState.handleShelfQueryInput({ target: { value: 'search' } } as unknown as Event);
    libraryState.clearShelfQuery();
    expect(libraryState.shelfQueryState.rawQuery).toBe('');
  });

  // ─── Reading helpers ───

  it('promoteBookForReading delegates to homeState', () => {
    const book = makeBook({ id: 'b1', progressPercentage: 0 });
    libraryState.books = [book as any];
    libraryState.promoteBookForReading('b1');
    expect(libraryState.books[0].progressPercentage).toBe(1);
  });

  it('updateBookPage updates book page and total pages', () => {
    const book = makeBook({ id: 'b1', currentPage: 0, totalPages: 100 });
    libraryState.books = [book as any];
    libraryState.updateBookPage('b1', 50, 100);
    expect(libraryState.books[0].currentPage).toBe(50);
    expect(libraryState.books[0].totalPages).toBe(100);
  });

  it('recordReaderOpenMetric records metric', () => {
    libraryState.recordReaderOpenMetric('epub');
    expect(mockRecordMetric).toHaveBeenCalledWith('reader_open', { feature: 'epub' });
  });

  // ─── consumeBooksJustChanged / consumeLastRecoverableError ───

  it('consumeBooksJustChanged returns and clears flag', () => {
    libraryState._booksJustChanged = true;
    expect(libraryState.consumeBooksJustChanged()).toBe(true);
    expect(libraryState.consumeBooksJustChanged()).toBe(false);
  });

  it('consumeLastRecoverableError returns and clears', () => {
    libraryState._lastRecoverableError = { code: 'RATE_LIMIT', message: 'Too fast' };
    expect(libraryState.consumeLastRecoverableError()).toEqual({
      code: 'RATE_LIMIT',
      message: 'Too fast',
    });
    expect(libraryState.consumeLastRecoverableError()).toBeNull();
  });

  // ─── Derived state ───

  it('derived values do not throw with empty books', () => {
    expect(() => {
      void libraryState.continueReadingBooks;
      void libraryState.myShelfBooks;
      void libraryState.shelfBooks;
      void libraryState.shelfWarnings;
      void libraryState.shelfSortToken;
    }).not.toThrow();
  });

  it('derived shelfSortToken returns null when no sort token exists', () => {
    expect(libraryState.shelfSortToken).toBeNull();
  });

  it('derived shelfSortToken extracts sort value from query', () => {
    libraryState.handleShelfQueryInput({
      target: { value: 'sort:title hello' },
    } as unknown as Event);
    expect(libraryState.shelfSortToken).toBe('title');
  });

  // ─── Constants ───

  it('SHELF_TAB_OPTIONS has expected entries', () => {
    expect(libraryState.SHELF_TAB_OPTIONS).toHaveLength(4);
    expect(libraryState.SHELF_TAB_OPTIONS[0].key).toBe('all');
  });

  it('SHELF_SORT_OPTIONS has expected entries', () => {
    expect(libraryState.SHELF_SORT_OPTIONS).toHaveLength(6);
  });
});

describe('LibraryDomainState — Thumbnail generation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupDefaultMocks();
    resetLibraryState();
  });

  it('ensureEpubCover calls extractEpubCover and reloads on success', async () => {
    mockExtractEpubCover.mockResolvedValue(true);
    const rows = [makeLibraryRow({ id: 'b1', format: 'epub' })];
    mockListLibraryBooks.mockResolvedValue(rows);
    mockListBooks.mockResolvedValue([makeSourceRow({ id: 'b1', format: 'epub' })]);

    await libraryState.ensureEpubCover(makeBook({ id: 'b1', format: 'epub' }) as any);

    expect(mockExtractEpubCover).toHaveBeenCalledWith('b1', '/test/book.pdf');
    expect(mockListLibraryBooks).toHaveBeenCalled();
  });

  it('ensureEpubCover skips if already in flight', async () => {
    libraryState.thumbnailGenerationInFlight.add('b1');
    await libraryState.ensureEpubCover(makeBook({ id: 'b1', format: 'epub' }) as any);
    expect(mockExtractEpubCover).not.toHaveBeenCalled();
  });

  it('ensurePdfCover extracts metadata and updates book', async () => {
    mockExtractPdfMetadata.mockResolvedValue({
      author: 'New Author',
      title: null,
      totalPages: 200,
      thumbnailBytes: new Uint8Array([1, 2, 3]),
    });
    const book = makeBook({
      id: 'b1',
      format: 'pdf',
      filePath: '/t.pdf',
      author: '',
      totalPages: 0,
    });
    libraryState.books = [book as any];
    const rows = [
      makeLibraryRow({ id: 'b1', format: 'pdf', author: 'New Author', totalPages: 200 }),
    ];
    mockListLibraryBooks.mockResolvedValue(rows);
    mockListBooks.mockResolvedValue([makeSourceRow({ id: 'b1', filePath: '/t.pdf' })]);

    await libraryState.ensurePdfCover(book as any);

    expect(mockExtractPdfMetadata).toHaveBeenCalledWith('/t.pdf');
    expect(mockUpsertBookCover).toHaveBeenCalledWith({
      bookId: 'b1',
      data: [1, 2, 3],
      mimeType: 'image/png',
    });
    expect(mockUpsertBook).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'b1', author: 'New Author', totalPages: 200 }),
    );
    expect(mockListLibraryBooks).toHaveBeenCalled();
  });

  it('ensurePdfCover skips if already in flight', async () => {
    libraryState.thumbnailGenerationInFlight.add('b1');
    await libraryState.ensurePdfCover(makeBook({ id: 'b1', format: 'pdf' }) as any);
    expect(mockExtractPdfMetadata).not.toHaveBeenCalled();
  });
});

describe('LibraryDomainState — loadLibrary thumbnail triggering', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupDefaultMocks();
    resetLibraryState();
  });

  it('loadLibrary with pending PDF thumbnails attempts generation', async () => {
    const rows = [makeLibraryRow({ id: 'b1', format: 'pdf' })];
    mockListLibraryBooks.mockResolvedValue(rows);
    mockListBooks.mockResolvedValue([makeSourceRow({ id: 'b1', filePath: '/b1.pdf' })]);
    // Make extractPdfMetadata return nulls so ensurePdfCover doesn't recursively load
    mockExtractPdfMetadata.mockResolvedValue({
      author: null,
      title: null,
      totalPages: null,
      thumbnailBytes: null,
    });

    await libraryState.loadLibrary();

    expect(libraryState.thumbnailGenerationAttempted.has('b1')).toBe(true);
    expect(mockExtractPdfMetadata).toHaveBeenCalledWith('/b1.pdf');
  });

  it('loadLibrary with pending EPUB covers attempts generation', async () => {
    const rows = [makeLibraryRow({ id: 'b1', format: 'epub' })];
    mockListLibraryBooks.mockResolvedValue(rows);
    mockListBooks.mockResolvedValue([makeSourceRow({ id: 'b1', filePath: '/b1.epub' })]);

    await libraryState.loadLibrary();

    expect(libraryState.thumbnailGenerationAttempted.has('b1')).toBe(true);
    expect(mockExtractEpubCover).toHaveBeenCalledWith('b1', '/b1.epub');
  });
});
