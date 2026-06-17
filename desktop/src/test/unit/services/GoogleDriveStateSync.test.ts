/**
 * Unit tests for GoogleDriveStateSync — Task 2.2
 * Tests serialization, LWW conflict resolution, and GDrive integration.
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';

// Shared mock functions that both the GDriveProvider mock AND tests can reference
const mockUpload = vi.fn();
const mockDownload = vi.fn();
const mockList = vi.fn();

// Mock GDriveProvider BEFORE importing GoogleDriveStateSync
vi.mock('$lib/shared/services/storage/GDriveProvider', () => {
  return {
    GDriveProvider: vi.fn(function () {
      return {
        upload: mockUpload,
        download: mockDownload,
        list: mockList,
      };
    }),
  };
});

// Dynamic import — GoogleDriveStateSync module will use the mocked GDriveProvider
let GoogleDriveStateSync: typeof import('$lib/shared/services/GoogleDriveStateSync').GoogleDriveStateSync;

beforeAll(async () => {
  const mod = await import('$lib/shared/services/GoogleDriveStateSync');
  GoogleDriveStateSync = mod.GoogleDriveStateSync;
});

beforeEach(() => {
  vi.clearAllMocks();
});

// Types matching GoogleDriveStateSync exports
interface ProgressStateJson {
  id: string;
  book_id: string;
  cfi_location: string;
  percentage: number;
  current_page?: number;
  updated_at: number;
}

interface HighlightStateJson {
  id: string;
  book_id: string;
  cfi_range: string;
  text_content: string;
  note: string | null;
  color: string;
  updated_at: number;
  deleted_at: number | null;
  recordId: string;
  updatedAtEpochMillis: number;
  deletedAtEpochMillis: number | null;
}

interface BookmarkStateJson {
  id: string;
  book_id: string;
  cfi_location: string;
  title_or_snippet: string;
  updated_at: number;
  deleted_at: number | null;
  recordId: string;
  updatedAtEpochMillis: number;
  deletedAtEpochMillis: number | null;
}

interface BookStateJson {
  progress: ProgressStateJson | null;
  highlights: HighlightStateJson[];
  bookmarks: BookmarkStateJson[];
}

// Helper factories
function makeProgress(
  bookId: string,
  updatedAt: number,
  overrides: Partial<ProgressStateJson> = {},
): ProgressStateJson {
  return {
    id: `prog-${bookId}`,
    book_id: bookId,
    cfi_location: '/6/4[chap01]!/4/1:0',
    percentage: 42.5,
    updated_at: updatedAt,
    ...overrides,
  };
}

function makeHighlight(
  id: string,
  bookId: string,
  updatedAt: number,
  deletedAt: number | null = null,
): HighlightStateJson {
  return {
    id,
    book_id: bookId,
    cfi_range: `/6/4[chap01]!/4/1:0,/6/4[chap01]!/4/1:100`,
    text_content: `Highlight text for ${id}`,
    note: null,
    color: '#ffff00',
    updated_at: updatedAt,
    deleted_at: deletedAt,
    recordId: id,
    updatedAtEpochMillis: updatedAt,
    deletedAtEpochMillis: deletedAt,
  };
}

function makeBookmark(
  id: string,
  bookId: string,
  updatedAt: number,
  deletedAt: number | null = null,
): BookmarkStateJson {
  return {
    id,
    book_id: bookId,
    cfi_location: '/6/4[chap01]!/4/1:0',
    title_or_snippet: `Bookmark ${id}`,
    updated_at: updatedAt,
    deleted_at: deletedAt,
    recordId: id,
    updatedAtEpochMillis: updatedAt,
    deletedAtEpochMillis: deletedAt,
  };
}

// ============================================================
// Tests
// ============================================================

describe('GoogleDriveStateSync — LWW resolution logic', () => {
  it('pullState returns local state when no remote state exists (first sync)', async () => {
    const localProgress = makeProgress('book-1', 1000);
    const localHighlights = [makeHighlight('h1', 'book-1', 1000)];
    const localBookmarks = [makeBookmark('b1', 'book-1', 1000)];

    // Mock download to throw (no remote file)
    mockDownload.mockRejectedValue(new Error('File not found'));

    const result = await GoogleDriveStateSync.pullState(
      'book-1',
      localProgress,
      localHighlights,
      localBookmarks,
    );

    expect(result).toBeDefined();
    expect(result.progress).not.toBeNull();
    expect(result.progress?.book_id).toBe('book-1');
    expect(result.highlights).toHaveLength(1);
    expect(result.bookmarks).toHaveLength(1);
  });

  it('pullState resolves progress via LWW — remote newer wins', async () => {
    const localProgress = makeProgress('book-2', 1000);
    const remoteProgress = makeProgress('book-2', 2000, {
      cfi_location: '/6/10!remote',
      percentage: 75,
    });

    const remoteState: BookStateJson = {
      progress: remoteProgress,
      highlights: [],
      bookmarks: [],
    };
    const jsonBytes = new TextEncoder().encode(JSON.stringify(remoteState));
    mockDownload.mockResolvedValue(jsonBytes);

    const result = await GoogleDriveStateSync.pullState('book-2', localProgress, [], []);

    expect(result.progress).not.toBeNull();
    if (result.progress) {
      expect(result.progress.percentage).toBe(75);
      expect(result.progress.cfi_location).toBe('/6/10!remote');
      expect(result.progress.updated_at).toBe(2000);
    }
  });

  it('pullState resolves progress via LWW — local newer wins', async () => {
    const localProgress = makeProgress('book-3', 3000, { percentage: 90 });
    const remoteProgress = makeProgress('book-3', 2000, { percentage: 45 });

    const remoteState: BookStateJson = {
      progress: remoteProgress,
      highlights: [],
      bookmarks: [],
    };
    mockDownload.mockResolvedValue(new TextEncoder().encode(JSON.stringify(remoteState)));

    const result = await GoogleDriveStateSync.pullState('book-3', localProgress, [], []);

    expect(result.progress).not.toBeNull();
    if (result.progress) {
      expect(result.progress.percentage).toBe(90);
      expect(result.progress.updated_at).toBe(3000);
    }
  });

  it('pullState resolves highlights with LWW via ConflictResolver', async () => {
    const localHighlights = [makeHighlight('h1', 'book-4', 1500)];
    const remoteHighlights = [makeHighlight('h1', 'book-4', 2500)];

    const remoteState: BookStateJson = {
      progress: null,
      highlights: remoteHighlights,
      bookmarks: [],
    };
    mockDownload.mockResolvedValue(new TextEncoder().encode(JSON.stringify(remoteState)));

    const result = await GoogleDriveStateSync.pullState('book-4', null, localHighlights, []);

    expect(result.highlights).toHaveLength(1);
    expect(result.highlights[0].updatedAtEpochMillis).toBe(2500);
  });

  it('pullState resolves bookmarks — merge local+remote with distinct IDs', async () => {
    const localBookmarks = [makeBookmark('b1', 'book-5', 1000)];
    const remoteBookmarks = [makeBookmark('b2', 'book-5', 2000)];

    const remoteState: BookStateJson = {
      progress: null,
      highlights: [],
      bookmarks: remoteBookmarks,
    };
    mockDownload.mockResolvedValue(new TextEncoder().encode(JSON.stringify(remoteState)));

    const result = await GoogleDriveStateSync.pullState('book-5', null, [], localBookmarks);

    expect(result.bookmarks).toHaveLength(2);
    const ids = result.bookmarks.map((b) => b.id).sort();
    expect(ids).toEqual(['b1', 'b2']);
  });

  it('pullState handles tombstoned records (deleted_at not null)', async () => {
    const localHighlight = makeHighlight('h-del', 'book-6', 1000);
    const remoteHighlight = makeHighlight('h-del', 'book-6', 3000, 3000);

    const remoteState: BookStateJson = {
      progress: null,
      highlights: [remoteHighlight],
      bookmarks: [],
    };
    mockDownload.mockResolvedValue(new TextEncoder().encode(JSON.stringify(remoteState)));

    const result = await GoogleDriveStateSync.pullState('book-6', null, [localHighlight], []);

    expect(result.highlights).toHaveLength(1);
    expect(result.highlights[0].deletedAtEpochMillis).toBe(3000);
  });

  it('pushState serializes state and uploads with correct filename', async () => {
    mockUpload.mockResolvedValue('file-id-123');

    const progress = makeProgress('book-7', 5000);
    const highlights = [makeHighlight('h1', 'book-7', 5000)];
    const bookmarks: BookmarkStateJson[] = [];

    await GoogleDriveStateSync.pushState('book-7', progress, highlights, bookmarks);

    expect(mockUpload).toHaveBeenCalled();
    const uploadCall = mockUpload.mock.calls[0];
    expect(uploadCall[0]).toBe('book-7_state.json');

    // Verify uploaded bytes are valid BookStateJson
    const uploadedBytes = uploadCall[1] as Uint8Array;
    const uploadedJson = JSON.parse(new TextDecoder().decode(uploadedBytes)) as BookStateJson;
    expect(uploadedJson.progress?.book_id).toBe('book-7');
    expect(uploadedJson.highlights).toHaveLength(1);
  });

  it('listStateFiles filters only *_state.json files', async () => {
    mockList.mockResolvedValue([
      'book-1.epub',
      'book-1_state.json',
      'book-2.pdf',
      'book-2_state.json',
      'cover.jpg',
    ]);

    const files = await GoogleDriveStateSync.listStateFiles();
    expect(files).toHaveLength(2);
    expect(files).toContain('book-1_state.json');
    expect(files).toContain('book-2_state.json');
  });
});
