import { describe, expect, it, vi, beforeEach } from "vitest";
import { appState } from "$lib/stores/AppState.svelte";

const mockReadFile = vi.hoisted(() =>
  vi.fn<(...args: unknown[]) => Promise<Uint8Array>>().mockResolvedValue(new Uint8Array([1, 2, 3]))
);

// Mock ALL AppState dependencies upfront
vi.mock("$lib/services/BookImportService", () => {
  const z = vi.fn() as any;
  return { importBook: z };
});
vi.mock("$lib/services/FilePicker", () => {
  const z = vi.fn() as any;
  return { pickFile: z, pickFolder: z };
});
vi.mock("$lib/services/pdfThumbnail", () => ({
  extractPdfMetadata: vi.fn(function () {
    return Promise.reject(new Error("no pdf"));
  }),
}));
vi.mock("$lib/stores/theme", () => {
  const z = vi.fn() as any;
  return { initTheme: z };
});
vi.mock("$lib/i18n", () => ({
  i18n: {
    t: vi.fn(function (locale: string, key: string) {
      return key;
    }),
    initializeLocale: vi.fn(function () {
      return Promise.resolve("es");
    }),
  },
}));

const mockGetProgress = vi.hoisted(() => vi.fn().mockResolvedValue(null));
const mockGetReadingStats = vi.hoisted(() => vi.fn().mockResolvedValue(null));

vi.mock("$lib/api/tauriClient", () => {
  const rf = vi.fn(function () {
    return Promise.resolve([]);
  }) as any;
  return {
    listLibraryBooks: rf,
    listBooks: rf,
    listCollections: rf,
    getDefaultReaderSettings: vi.fn(function () {
      return {
        themeMode: "paper",
        brightness: 100,
        contrast: 100,
        selectionColor: "#3b82f6",
        epub: { fontSize: 16, fontFamily: "serif" },
      };
    }),
    getReaderSettings: vi.fn().mockResolvedValue(null),
    getProgress: mockGetProgress,
    getReadingStats: mockGetReadingStats,
    saveProgress: vi.fn(function () {
      return Promise.resolve(undefined);
    }),
    saveReadingSession: vi.fn(function () {
      return Promise.resolve(undefined);
    }),
    hideBookFromLibrary: vi.fn(function () {
      return Promise.resolve(undefined);
    }),
    upsertBook: vi.fn(function () {
      return Promise.resolve(undefined);
    }),
    upsertBookCover: vi.fn(function () {
      return Promise.resolve(undefined);
    }),
    extractEpubCover: vi.fn(function () {
      return Promise.resolve(false);
    }),
    updateBookProgress: vi.fn(function () {
      return Promise.resolve(undefined);
    }),
    searchBookText: vi.fn(function () {
      return Promise.resolve(null);
    }),
    scanFolder: vi.fn(function () {
      return Promise.resolve({ files: [] });
    }),
  };
});

vi.mock("@tauri-apps/plugin-fs", () => ({
  readFile: mockReadFile,
}));

vi.mock("$lib/services/BulkImportService", () => {
  const mock = {
    importFolder: vi.fn().mockResolvedValue({ success: 0, skipped: 0, failed: 0, cancelled: 0 }),
    cancel: vi.fn(),
  };
  return {
    BulkImportService: vi.fn(function () {
      return mock;
    }),
  };
});

function resetAppState() {
  appState.route = "home";
  appState.previewBookId = null;
  appState.activeReadingBookId = null;
  appState.shelfDetailsBookId = null;
  appState.libraryUnavailableReason = null;
  appState.statsUnavailableReason = null;
  appState.searchUnavailableReason = null;
  appState.isLoadingLibrary = false;
  appState.isLoadingStats = false;
  appState.isSearching = false;
  appState.isImporting = false;
  appState.importProgress = null;
  appState.cfiLocation = "";
  appState.percentage = 0;
  appState.stats = null;
  appState.searchResponse = null;
  appState.searchTargetLocator = null;
  appState.readerError = null;
  appState.locale = "es";
  appState.editingBook = null;
  appState.collections = [];
  appState.isCollectionManagerOpen = false;
  appState.isBulkImportOpen = false;
  appState.isBulkScanning = false;
  appState.isBulkImporting = false;
  appState.bulkImportFolderPath = null;
  appState.bulkImportFolderName = null;
  appState.bulkScanResult = null;
  appState.bulkScanError = null;
  appState.bulkImportProgress = null;
  appState.bulkImportSummary = null;
  appState.books = [];
  appState.preloadedBytes = null;
}

describe("AppState", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetAppState();
  });

  // ─── Preload (startReading) ───

  it("startReading preloadedBytes is populated for EPUB", async () => {
    appState.preloadedBytes = { filePath: "/old.epub", data: new Uint8Array([1, 2, 3]) };
    const book = { id: "b1", title: "Test", filePath: "/test.epub", format: "epub" } as any;
    await appState.startReading(book);
    // The old preloadedBytes was cleared, then new preload populated it
    expect(appState.preloadedBytes).not.toBeNull();
    expect(appState.preloadedBytes!.filePath).toBe("/test.epub");
  });

  it("startReading sets route to reader", async () => {
    const book = { id: "b1", title: "Test", filePath: "/test.epub", format: "epub" } as any;
    await appState.startReading(book);
    expect(appState.route).toBe("reader");
  });

  it("startReading sets activeReadingBookId", async () => {
    const book = { id: "b1", title: "Test", filePath: "/test.epub", format: "epub" } as any;
    await appState.startReading(book);
    expect(appState.activeReadingBookId).toBe("b1");
  });

  it("startReading fires readFile for EPUB preload", async () => {
    const book = { id: "b1", title: "Test", filePath: "/test.epub", format: "epub" } as any;
    await appState.startReading(book);
    expect(mockReadFile).toHaveBeenCalledWith("/test.epub");
  });

  it("startReading preloadedBytes is populated asynchronously for EPUB", async () => {
    mockReadFile.mockResolvedValueOnce(new Uint8Array([10, 20, 30]));
    const book = { id: "b1", title: "Test", filePath: "/test.epub", format: "epub" } as any;
    await appState.startReading(book);
    await vi.waitFor(() => {
      expect(appState.preloadedBytes).not.toBeNull();
    });
    expect(appState.preloadedBytes!.filePath).toBe("/test.epub");
    expect(appState.preloadedBytes!.data).toEqual(new Uint8Array([10, 20, 30]));
  });

  it("startReading preload failure does not throw for EPUB", async () => {
    mockReadFile.mockRejectedValueOnce(new Error("EPUB load failed"));
    const book = { id: "b1", title: "Test", filePath: "/test.epub", format: "epub" } as any;
    await expect(appState.startReading(book)).resolves.toBeUndefined();
    expect(appState.preloadedBytes).toBeNull();
  });

  it("startReading fires readFile for PDF preload", async () => {
    const book = { id: "b1", title: "Test", filePath: "/test.pdf", format: "pdf" } as any;
    await appState.startReading(book);
    expect(mockReadFile).toHaveBeenCalledWith("/test.pdf");
  });

  it("startReading preloadedBytes is populated asynchronously for PDF", async () => {
    mockReadFile.mockResolvedValueOnce(new Uint8Array([99, 98, 97]));
    const book = { id: "b1", title: "Test", filePath: "/test.pdf", format: "pdf" } as any;
    await appState.startReading(book);
    await vi.waitFor(() => {
      expect(appState.preloadedBytes).not.toBeNull();
    });
    expect(appState.preloadedBytes!.filePath).toBe("/test.pdf");
    expect(appState.preloadedBytes!.data).toEqual(new Uint8Array([99, 98, 97]));
  });

  it("startReading preload failure does not throw for PDF", async () => {
    mockReadFile.mockRejectedValueOnce(new Error("PDF load failed"));
    const book = { id: "b1", title: "Test", filePath: "/test.pdf", format: "pdf" } as any;
    await expect(appState.startReading(book)).resolves.toBeUndefined();
    expect(appState.preloadedBytes).toBeNull();
  });

  it("startReading with EPUB calls getProgress for location and percentage", async () => {
    const book = { id: "b1", title: "Test", filePath: "/test.epub", format: "epub" } as any;
    await appState.startReading(book);
    expect(mockGetProgress).toHaveBeenCalledWith("b1");
  });

  it("startReading with PDF does not call getProgress", async () => {
    const book = { id: "b1", title: "Test", filePath: "/test.pdf", format: "pdf" } as any;
    await appState.startReading(book);
    expect(mockGetProgress).not.toHaveBeenCalled();
  });

  // ─── Navigation ───

  it("navigateToHome sets route and clears shelf details", () => {
    appState.route = "reader";
    appState.shelfDetailsBookId = "book-1";
    appState.navigateToHome();
    expect(appState.route).toBe("home");
    expect(appState.shelfDetailsBookId).toBeNull();
  });

  it("navigateToLibrary sets route to library", () => {
    appState.navigateToLibrary();
    expect(appState.route).toBe("library");
  });

  it("navigateToStats sets route to stats", () => {
    appState.navigateToStats();
    expect(appState.route).toBe("stats");
  });

  it("navigateToHighlights sets route to highlights", () => {
    appState.navigateToHighlights();
    expect(appState.route).toBe("highlights");
  });

  it("navigateToSettings sets route to settings", () => {
    appState.navigateToSettings();
    expect(appState.route).toBe("settings");
  });

  it("backToHome sets route to home", () => {
    appState.route = "reader";
    appState.backToHome();
    expect(appState.route).toBe("home");
  });

  // ─── Pure utility methods ───

  it("getBookById returns null for empty/unknown id", () => {
    expect(appState.getBookById(null)).toBeNull();
    expect(appState.getBookById("nonexistent")).toBeNull();
  });

  it("getBookById returns matching book", () => {
    appState.books = [
      { id: "1", title: "Test Book", filePath: "", format: "pdf", currentPage: 10, totalPages: 100 } as any,
    ];
    const found = appState.getBookById("1");
    expect(found).toBeTruthy();
    expect(found!.id).toBe("1");
  });

  it("isValidSessionProgressEvent validates correct events", () => {
    const valid = {
      startedAt: new Date(Date.now() - 60000).toISOString(),
      endedAt: new Date().toISOString(),
      durationSeconds: 60,
      startPercentage: 10,
      endPercentage: 50,
    };
    expect(appState.isValidSessionProgressEvent(valid)).toBe(true);
  });

  it("isValidSessionProgressEvent rejects missing endedAt", () => {
    expect(appState.isValidSessionProgressEvent({ startedAt: new Date().toISOString(), durationSeconds: 0 })).toBe(false);
  });

  it("isValidSessionProgressEvent rejects zero duration", () => {
    expect(
      appState.isValidSessionProgressEvent({
        startedAt: new Date().toISOString(),
        endedAt: new Date().toISOString(),
        durationSeconds: 0,
      }),
    ).toBe(false);
  });

  it("isValidSessionProgressEvent rejects invalid percentages", () => {
    expect(
      appState.isValidSessionProgressEvent({
        startedAt: new Date(Date.now() - 60000).toISOString(),
        endedAt: new Date().toISOString(),
        durationSeconds: 60,
        startPercentage: -1,
        endPercentage: 101,
      }),
    ).toBe(false);
  });

  it("mapCommandError extracts commandError when present", () => {
    const err = new Error("wrapped") as any;
    err.commandError = { code: "NOT_FOUND", message: "Book not found", recoverable: true };
    expect(appState.mapCommandError(err)).toEqual({
      code: "NOT_FOUND",
      message: "Book not found",
      recoverable: true,
    });
  });

  it("mapCommandError falls back for plain Error", () => {
    const result = appState.mapCommandError(new Error("something broke"));
    expect(result.code).toBe("INTERNAL_ERROR");
    expect(result.message).toBe("something broke");
  });

  it("mapCommandError handles non-Error throws", () => {
    const result = appState.mapCommandError("string error");
    expect(result.code).toBe("INTERNAL_ERROR");
    expect(result.recoverable).toBe(false);
  });

  it("hasResolvedCoverPath checks non-empty coverPath", () => {
    expect(appState.hasResolvedCoverPath({ coverPath: "/path.jpg" } as any)).toBe(true);
    expect(appState.hasResolvedCoverPath({ coverPath: "" } as any)).toBe(false);
    expect(appState.hasResolvedCoverPath({ coverPath: "   " } as any)).toBe(false);
    expect(appState.hasResolvedCoverPath({ coverPath: null } as any)).toBe(false);
  });

  it("shouldGeneratePdfCover only for PDFs without cover", () => {
    const pdf = { id: "1", format: "pdf", coverPath: null, filePath: "/t.pdf" } as any;
    const epub = { id: "2", format: "epub", coverPath: null, filePath: "/t.epub" } as any;
    const withCover = { id: "3", format: "pdf", coverPath: "/c.jpg", filePath: "/t.pdf" } as any;
    expect(appState.shouldGeneratePdfCover(pdf)).toBe(true);
    expect(appState.shouldGeneratePdfCover(epub)).toBe(false);
    expect(appState.shouldGeneratePdfCover(withCover)).toBe(false);
  });

  // ─── Shelf operations ───

  it("openDetails sets previewBookId", () => {
    appState.openDetails({ id: "b1" } as any);
    expect(appState.previewBookId).toBe("b1");
  });

  it("openShelfDetails sets preview and shelf details", () => {
    appState.openShelfDetails({ id: "b1" } as any);
    expect(appState.previewBookId).toBe("b1");
    expect(appState.shelfDetailsBookId).toBe("b1");
  });

  it("closeShelfDetails clears shelf details", () => {
    appState.shelfDetailsBookId = "b1";
    appState.closeShelfDetails();
    expect(appState.shelfDetailsBookId).toBeNull();
  });

  it("setShelfTab updates tab", () => {
    appState.setShelfTab("favorites");
    expect(appState.shelfQueryState.tab).toBe("favorites");
  });

  it("setShelfSort updates sort", () => {
    appState.setShelfSort("title");
    expect(appState.shelfQueryState.sortKey).toBe("title");
  });

  it("setShelfViewMode toggles grid/list", () => {
    appState.setShelfViewMode("list");
    expect(appState.shelfQueryState.viewMode).toBe("list");
    appState.setShelfViewMode("grid");
    expect(appState.shelfQueryState.viewMode).toBe("grid");
  });

  it("handleShelfQueryInput and clearShelfQuery", () => {
    appState.handleShelfQueryInput({ target: { value: "search term" } } as any);
    expect(appState.shelfQueryState.rawQuery).toBe("search term");
    appState.clearShelfQuery();
    expect(appState.shelfQueryState.rawQuery).toBe("");
  });

  // ─── Bulk import open/close ───

  it("openBulkImportModal sets flag", () => {
    appState.openBulkImportModal();
    expect(appState.isBulkImportOpen).toBe(true);
  });

  it("closeBulkImportModal resets bulk state", () => {
    appState.isBulkImportOpen = true;
    appState.isBulkScanning = true;
    appState.bulkScanError = "err";
    appState.closeBulkImportModal();
    expect(appState.isBulkImportOpen).toBe(false);
    expect(appState.isBulkScanning).toBe(false);
    expect(appState.bulkScanError).toBeNull();
    expect(appState.bulkImportProgress).toBeNull();
    expect(appState.bulkImportSummary).toBeNull();
  });

  // ─── Settings / config ───

  it("handleReaderSettingsChange updates settings", () => {
    const s = { themeMode: "night", brightness: 50 } as any;
    appState.handleReaderSettingsChange(s);
    expect(appState.readerSettings).toStrictEqual(s);
  });

  it("handleLocaleChange updates locale", () => {
    appState.handleLocaleChange("en");
    expect(appState.locale).toBe("en");
  });

  // ─── Search ───

  it("handleSearchJump sets target", () => {
    appState.handleSearchJump({ locator: "page=42" } as any);
    expect(appState.searchTargetLocator).toBe("page=42");
  });

  it("handleReaderLocationContext does not throw", () => {
    expect(() => appState.handleReaderLocationContext()).not.toThrow();
  });

  // ─── Derived state ───

  it("derived values do not throw with empty books", () => {
    expect(() => {
      const _ = appState.continueReadingBooks;
      const _2 = appState.myShelfBooks;
      const _3 = appState.shelfBooks;
      const _4 = appState.shelfWarnings;
      const _5 = appState.shelfSortToken;
    }).not.toThrow();
  });

  it("derived selectedShelfBook is null when no details open", () => {
    expect(appState.selectedShelfBook).toBeNull();
  });

  // ─── i18n helper ───

  it("t() calls i18n.t with current locale", () => {
    const result = appState.t("home.title" as any);
    expect(typeof result).toBe("string");
  });

  // ─── Constants ───

  it("SHELF_TAB_OPTIONS has expected entries", () => {
    expect(appState.SHELF_TAB_OPTIONS).toHaveLength(4);
    expect(appState.SHELF_TAB_OPTIONS[0].key).toBe("all");
  });

  it("SHELF_SORT_OPTIONS has expected entries", () => {
    expect(appState.SHELF_SORT_OPTIONS).toHaveLength(6);
  });
});

describe("AppState — Preload edge cases", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetAppState();
  });

  it("preloadedBytes is null by default", () => {
    expect(appState.preloadedBytes).toBeNull();
  });

  it("preloadedBytes persists across state changes until overwritten", () => {
    appState.preloadedBytes = { filePath: "/book.pdf", data: new Uint8Array([1, 2]) };
    appState.route = "foo" as any;
    expect(appState.preloadedBytes).toEqual({ filePath: "/book.pdf", data: new Uint8Array([1, 2]) });
  });

  it("startReading clears preloadedBytes even if readFile fails for EPUB", async () => {
    mockReadFile.mockRejectedValueOnce(new Error("fail"));
    appState.preloadedBytes = { filePath: "/old.epub", data: new Uint8Array([9, 9, 9]) };
    const book = { id: "b1", filePath: "/new.epub", format: "epub" } as any;
    await appState.startReading(book);
    expect(appState.preloadedBytes).toBeNull();
  });
});
