import { describe, expect, it, vi, beforeEach } from 'vitest';
import { readerState } from '$lib/shared/stores/ReaderDomainState.svelte';
import type { ReaderBook } from '$lib/shared/types';

// ─── Hoisted mock factories ───

const mockReadFile = vi.hoisted(() =>
  vi.fn<(...args: unknown[]) => Promise<Uint8Array>>().mockResolvedValue(new Uint8Array([1, 2, 3])),
);

const mockGetProgress = vi.hoisted(() => vi.fn().mockResolvedValue(null));
const mockSaveProgress = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));
const mockSaveReadingSession = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));
const mockUpdateBookProgress = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));

// ─── Module mocks ───

vi.mock('@tauri-apps/plugin-fs', () => ({
  readFile: mockReadFile,
  BaseDirectory: { AppData: 'appData', AppConfig: 'appConfig', AppCache: 'appCache' },
}));

vi.mock('$lib/shared/api/tauriClient', () => ({
  getProgress: mockGetProgress,
  saveProgress: mockSaveProgress,
  saveReadingSession: mockSaveReadingSession,
  updateBookProgress: mockUpdateBookProgress,
}));

// Mock pdfStreaming for startReading PDF path
vi.mock('$lib/features/reader/viewer-pdf/pdfStreaming', () => ({
  createPdfDocument: vi.fn().mockResolvedValue({ numPages: 10 }),
}));

// ─── Helpers ───

const makeBook = (overrides: Partial<ReaderBook> = {}): ReaderBook => ({
  id: 'book-1',
  title: 'Test Book',
  author: 'Test Author',
  filePath: '/test/book.epub',
  format: 'epub',
  currentPage: 0,
  totalPages: 100,
  progressPercentage: 0,
  coverPath: null,
  minutesRead: 0,
  updatedAt: '2026-01-01T00:00:00.000Z',
  createdAt: '2026-01-01T00:00:00.000Z',
  collectionIds: [],
  ...overrides,
});

function resetReaderState(): void {
  readerState.activeReadingBookId = null;
  readerState.cfiLocation = '';
  readerState.percentage = 0;
  readerState.preloadedBytes = null;
  readerState.readerError = null;
  readerState.onStatsRefreshNeeded = null;
  readerState.onPageChangeCallback = null;
}

// ─── Tests ───

describe('ReaderDomainState', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetReaderState();
  });

  // ─── Initial state ───

  it('has expected initial state', () => {
    expect(readerState.activeReadingBookId).toBeNull();
    expect(readerState.cfiLocation).toBe('');
    expect(readerState.percentage).toBe(0);
    expect(readerState.preloadedBytes).toBeNull();
    expect(readerState.readerError).toBeNull();
    expect(readerState.onStatsRefreshNeeded).toBeNull();
    expect(readerState.onPageChangeCallback).toBeNull();
  });

  // ─── isValidSessionProgressEvent ───

  it('isValidSessionProgressEvent validates correct events', () => {
    const valid = {
      startedAt: new Date(Date.now() - 60000).toISOString(),
      endedAt: new Date().toISOString(),
      durationSeconds: 60,
      startPercentage: 10,
      endPercentage: 50,
    };
    expect(readerState.isValidSessionProgressEvent(valid)).toBe(true);
  });

  it('isValidSessionProgressEvent rejects missing endedAt', () => {
    expect(
      readerState.isValidSessionProgressEvent({
        startedAt: new Date().toISOString(),
        durationSeconds: 0,
      }),
    ).toBe(false);
  });

  it('isValidSessionProgressEvent rejects zero duration', () => {
    expect(
      readerState.isValidSessionProgressEvent({
        startedAt: new Date().toISOString(),
        endedAt: new Date().toISOString(),
        durationSeconds: 0,
      }),
    ).toBe(false);
  });

  it('isValidSessionProgressEvent rejects endedAt before startedAt', () => {
    expect(
      readerState.isValidSessionProgressEvent({
        startedAt: new Date(Date.now() + 60000).toISOString(),
        endedAt: new Date().toISOString(),
        durationSeconds: 60,
      }),
    ).toBe(false);
  });

  it('isValidSessionProgressEvent rejects invalid percentages', () => {
    expect(
      readerState.isValidSessionProgressEvent({
        startedAt: new Date(Date.now() - 60000).toISOString(),
        endedAt: new Date().toISOString(),
        durationSeconds: 60,
        startPercentage: -1,
        endPercentage: 101,
      }),
    ).toBe(false);
  });

  it('isValidSessionProgressEvent accepts events without percentages', () => {
    expect(
      readerState.isValidSessionProgressEvent({
        startedAt: new Date(Date.now() - 60000).toISOString(),
        endedAt: new Date().toISOString(),
        durationSeconds: 60,
      }),
    ).toBe(true);
  });

  // ─── startReading EPUB ───

  it('startReading with EPUB sets activeReadingBookId', async () => {
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await readerState.startReading(book);
    expect(readerState.activeReadingBookId).toBe('b1');
  });

  it('startReading clears preloadedBytes for EPUB', async () => {
    readerState.preloadedBytes = { filePath: '/old.epub', data: new Uint8Array([9, 9, 9]) };
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await readerState.startReading(book);
    expect(readerState.preloadedBytes).not.toBeNull();
    expect(readerState.preloadedBytes!.filePath).toBe('/test.epub');
  });

  it('startReading fires readFile for EPUB preload', async () => {
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await readerState.startReading(book);
    expect(mockReadFile).toHaveBeenCalledWith('/test.epub');
  });

  it('startReading preloadedBytes populated asynchronously for EPUB', async () => {
    mockReadFile.mockResolvedValueOnce(new Uint8Array([10, 20, 30]));
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await readerState.startReading(book);
    await vi.waitFor(() => {
      expect(readerState.preloadedBytes).not.toBeNull();
    });
    expect(readerState.preloadedBytes!.filePath).toBe('/test.epub');
    expect(readerState.preloadedBytes!.data).toEqual(new Uint8Array([10, 20, 30]));
  });

  it('startReading preload failure does not throw for EPUB', async () => {
    mockReadFile.mockRejectedValueOnce(new Error('EPUB load failed'));
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await expect(readerState.startReading(book)).resolves.toBeUndefined();
    expect(readerState.preloadedBytes).toBeNull();
  });

  it('startReading with EPUB calls getProgress', async () => {
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await readerState.startReading(book);
    expect(mockGetProgress).toHaveBeenCalledWith('b1');
  });

  it('startReading with EPUB restores progress from getProgress', async () => {
    mockGetProgress.mockResolvedValueOnce({
      bookId: 'b1',
      cfiLocation: 'epubcfi(/6/2)',
      percentage: 42,
    });
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await readerState.startReading(book);
    expect(readerState.cfiLocation).toBe('epubcfi(/6/2)');
    expect(readerState.percentage).toBe(42);
  });

  it('startReading with EPUB handles getProgress failure gracefully', async () => {
    mockGetProgress.mockRejectedValueOnce(new Error('DB error'));
    const book = makeBook({ id: 'b1', format: 'epub', filePath: '/test.epub' });
    await readerState.startReading(book);
    expect(readerState.cfiLocation).toBe('');
    expect(readerState.percentage).toBe(0);
  });

  // ─── startReading PDF ───

  it('startReading with PDF sets activeReadingBookId', async () => {
    const book = makeBook({ id: 'b1', format: 'pdf', filePath: '/test.pdf' });
    await readerState.startReading(book);
    expect(readerState.activeReadingBookId).toBe('b1');
  });

  it('startReading fires readFile for PDF preload', async () => {
    const book = makeBook({ id: 'b1', format: 'pdf', filePath: '/test.pdf' });
    await readerState.startReading(book);
    expect(mockReadFile).toHaveBeenCalledWith('/test.pdf');
  });

  it('startReading preloadedBytes populated asynchronously for PDF', async () => {
    mockReadFile.mockResolvedValueOnce(new Uint8Array([99, 98, 97]));
    const book = makeBook({ id: 'b1', format: 'pdf', filePath: '/test.pdf' });
    await readerState.startReading(book);
    await vi.waitFor(() => {
      expect(readerState.preloadedBytes).not.toBeNull();
    });
    expect(readerState.preloadedBytes!.filePath).toBe('/test.pdf');
    expect(readerState.preloadedBytes!.data).toEqual(new Uint8Array([99, 98, 97]));
  });

  it('startReading preload failure does not throw for PDF', async () => {
    mockReadFile.mockRejectedValueOnce(new Error('PDF load failed'));
    const book = makeBook({ id: 'b1', format: 'pdf', filePath: '/test.pdf' });
    await expect(readerState.startReading(book)).resolves.toBeUndefined();
    expect(readerState.preloadedBytes).toBeNull();
  });

  it('startReading with PDF does not call getProgress', async () => {
    const book = makeBook({ id: 'b1', format: 'pdf', filePath: '/test.pdf' });
    await readerState.startReading(book);
    expect(mockGetProgress).not.toHaveBeenCalled();
  });

  // ─── handleEpubLocationChange ───

  it('handleEpubLocationChange updates location and percentage', async () => {
    await readerState.handleEpubLocationChange('b1', 'epubcfi(/6/4)', 45);

    expect(readerState.cfiLocation).toBe('epubcfi(/6/4)');
    expect(readerState.percentage).toBe(45);
    expect(mockSaveProgress).toHaveBeenCalledWith({
      bookId: 'b1',
      cfiLocation: 'epubcfi(/6/4)',
      percentage: 45,
    });
  });

  it('handleEpubLocationChange clamps percentage', async () => {
    await readerState.handleEpubLocationChange('b1', 'epubcfi(/end)', 150);
    expect(readerState.percentage).toBe(100);

    await readerState.handleEpubLocationChange('b1', 'epubcfi(/start)', -10);
    expect(readerState.percentage).toBe(0);
  });

  it('handleEpubLocationChange calls onStatsRefreshNeeded', async () => {
    const refreshFn = vi.fn().mockResolvedValue(undefined);
    readerState.onStatsRefreshNeeded = refreshFn;

    await readerState.handleEpubLocationChange('b1', 'epubcfi(/6/4)', 45);

    expect(refreshFn).toHaveBeenCalledWith('b1');
  });

  it('handleEpubLocationChange does not throw on save failure', async () => {
    mockSaveProgress.mockRejectedValueOnce(new Error('Save failed'));
    await expect(
      readerState.handleEpubLocationChange('b1', 'epubcfi(/6/4)', 45),
    ).resolves.toBeUndefined();
    expect(readerState.cfiLocation).toBe('epubcfi(/6/4)');
  });

  // ─── handlePdfPageChange ───

  it('handlePdfPageChange calls onPageChangeCallback and updateBookProgress', async () => {
    const pageChangeFn = vi.fn();
    readerState.onPageChangeCallback = pageChangeFn;

    await readerState.handlePdfPageChange('b1', 50, 100);

    expect(pageChangeFn).toHaveBeenCalledWith('b1', 50, 100);
    expect(mockUpdateBookProgress).toHaveBeenCalledWith('b1', 50);
  });

  it('handlePdfPageChange calls onStatsRefreshNeeded', async () => {
    const refreshFn = vi.fn().mockResolvedValue(undefined);
    readerState.onStatsRefreshNeeded = refreshFn;

    await readerState.handlePdfPageChange('b1', 50, 100);

    expect(refreshFn).toHaveBeenCalledWith('b1');
  });

  it('handlePdfPageChange does not throw on failure', async () => {
    mockUpdateBookProgress.mockRejectedValueOnce(new Error('Progress save failed'));
    const pageChangeFn = vi.fn();
    readerState.onPageChangeCallback = pageChangeFn;

    await expect(readerState.handlePdfPageChange('b1', 50, 100)).resolves.toBeUndefined();
    expect(pageChangeFn).toHaveBeenCalled();
  });

  // ─── handlePdfSessionProgress ───

  it('handlePdfSessionProgress saves valid session', async () => {
    const refreshFn = vi.fn().mockResolvedValue(undefined);
    readerState.onStatsRefreshNeeded = refreshFn;
    const startedAt = new Date(Date.now() - 120000).toISOString();
    const endedAt = new Date().toISOString();

    await readerState.handlePdfSessionProgress('b1', {
      startedAt,
      endedAt,
      durationSeconds: 120,
      startPercentage: 10,
      endPercentage: 30,
    });

    expect(mockSaveReadingSession).toHaveBeenCalledWith({
      bookId: 'b1',
      startedAt,
      endedAt,
      durationSeconds: 120,
      startPercentage: 10,
      endPercentage: 30,
    });
    expect(refreshFn).toHaveBeenCalledWith('b1');
  });

  it('handlePdfSessionProgress rejects invalid session silently', async () => {
    await readerState.handlePdfSessionProgress('b1', {
      startedAt: new Date().toISOString(),
      endedAt: new Date().toISOString(),
      durationSeconds: 0,
    });

    expect(mockSaveReadingSession).not.toHaveBeenCalled();
  });

  it('handlePdfSessionProgress does not throw on save failure', async () => {
    mockSaveReadingSession.mockRejectedValueOnce(new Error('Session save failed'));
    const startedAt = new Date(Date.now() - 60000).toISOString();
    const endedAt = new Date().toISOString();

    await expect(
      readerState.handlePdfSessionProgress('b1', {
        startedAt,
        endedAt,
        durationSeconds: 60,
      }),
    ).resolves.toBeUndefined();
  });

  // ─── handleReaderLocationContext ───

  it('handleReaderLocationContext does not throw', () => {
    expect(() => readerState.handleReaderLocationContext()).not.toThrow();
  });

  // ─── resetReader ───

  it('resetReader clears all reader state', () => {
    readerState.activeReadingBookId = 'b1';
    readerState.cfiLocation = 'epubcfi(/6/2)';
    readerState.percentage = 42;
    readerState.preloadedBytes = { filePath: '/t.epub', data: new Uint8Array([1, 2, 3]) };
    readerState.readerError = 'Some error';

    readerState.resetReader();

    expect(readerState.activeReadingBookId).toBeNull();
    expect(readerState.cfiLocation).toBe('');
    expect(readerState.percentage).toBe(0);
    expect(readerState.preloadedBytes).toBeNull();
    expect(readerState.readerError).toBeNull();
  });
});
