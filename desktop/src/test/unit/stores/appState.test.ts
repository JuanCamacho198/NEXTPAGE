import { describe, expect, it, vi, beforeEach } from "vitest";
import { appState } from "$lib/stores/AppState.svelte";

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

// Tauri client methods used by AppState
vi.mock("$lib/api/tauriClient", () => {
  const rf = vi.fn(function () {
    return Promise.resolve([]);
  }) as any;
  const rv = vi.fn(function () {
    return Promise.resolve(null);
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
    getReaderSettings: rv,
    getProgress: rv,
    getReadingStats: rv,
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
}

describe("AppState", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetAppState();
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
    const result = appState.t("home.title");
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
