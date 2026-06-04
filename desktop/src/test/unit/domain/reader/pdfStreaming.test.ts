/**
 * Unit tests for pdfStreaming.ts
 *
 * Tests document cache operations, LRU eviction, outline loading,
 * and createPdfDocument cache hit/miss behavior.
 *
 * IMPORTANT: vi.mock() factories must avoid module-level const references
 * because they run before module-scope const assignments (TDZ). Instead:
 * - Use vi.hoisted() for values used directly in tests (destructured consts)
 * - Use inline definitions + closures inside vi.mock() factories for mock objects
 */
import { beforeEach, describe, expect, it, vi } from "vitest";

// Hoisted values — used directly in test assertions via destructured consts.
const { mockDestroy, mockGetOutline, mockPdfDocument, mockGetFileBytes } =
  vi.hoisted(() => {
    const destroy = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const getOutline = vi.fn();
    const getFileBytes = vi.fn();

    const pdfDoc = {
      numPages: 10,
      destroy,
      getOutline,
      getPage: vi.fn(),
      getDestination: vi.fn(),
      getPageIndex: vi.fn(),
    };

    return {
      mockDestroy: destroy,
      mockGetOutline: getOutline,
      mockPdfDocument: pdfDoc,
      mockGetFileBytes: getFileBytes,
    };
  });

// pdfjs-dist mock
vi.mock("pdfjs-dist", () => {
  const getDocument = vi.fn(() => ({
    promise: Promise.resolve(mockPdfDocument),
    onProgress: null as unknown as ((progress: { loaded: number; total: number }) => void) | null,
    destroy: vi.fn(),
  }));

  return {
    default: {
      GlobalWorkerOptions: { workerSrc: "" },
      getDocument,
    },
    getDocument,
    PDFDocumentProxy: class {},
    PDFDocumentLoadingTask: class {},
  };
});

// $lib/api/tauriClient mock
vi.mock("$lib/api/tauriClient", () => ({
  getFileBytes: (...args: unknown[]) => mockGetFileBytes(...args),
}));

import {
  getCachedDocument,
  setCachedDocument,
  removeCachedDocument,
  clearDocumentCache,
  loadPdfOutline,
  createPdfDocument,
  documentCache,
} from "$lib/features/reader/pdf/pdfStreaming";

function makeMockDocument() {
  return {
    numPages: 10,
    destroy: mockDestroy,
    getOutline: mockGetOutline,
    getPage: vi.fn(),
    getDestination: vi.fn(),
    getPageIndex: vi.fn(),
  } as unknown as Record<string, unknown>;
}

function resetAllMocks() {
  vi.clearAllMocks();
  documentCache.clear();
  mockDestroy.mockResolvedValue(undefined);
  mockGetOutline.mockResolvedValue([]);
}

describe("pdfStreaming — Document Cache", () => {
  beforeEach(() => {
    resetAllMocks();
  });

  it("getCachedDocument returns undefined for unset key", () => {
    expect(getCachedDocument("/unknown.pdf")).toBeUndefined();
  });

  it("setCachedDocument and getCachedDocument round-trip", () => {
    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });
    const entry = getCachedDocument("/book.pdf");
    expect(entry).toBeDefined();
    expect(entry!.document).toBe(doc);
    expect(entry!.outlineLoaded).toBe(false);
  });

  it("setCachedDocument overwrites existing entry for same path", () => {
    const doc1 = makeMockDocument();
    const doc2 = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc1, outline: [], outlineLoaded: false });
    setCachedDocument("/book.pdf", { document: doc2, outline: [], outlineLoaded: true });
    const entry = getCachedDocument("/book.pdf");
    expect(entry!.document).toBe(doc2);
    expect(entry!.outlineLoaded).toBe(true);
    expect(mockDestroy).not.toHaveBeenCalled();
  });

  it("removeCachedDocument destroys document and removes entry", () => {
    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });
    removeCachedDocument("/book.pdf");
    expect(mockDestroy).toHaveBeenCalledTimes(1);
    expect(getCachedDocument("/book.pdf")).toBeUndefined();
  });

  it("removeCachedDocument is no-op for missing key", () => {
    removeCachedDocument("/nonexistent.pdf");
    expect(mockDestroy).not.toHaveBeenCalled();
  });

  it("clearDocumentCache destroys all cached documents and clears", () => {
    setCachedDocument("/a.pdf", { document: makeMockDocument(), outline: [], outlineLoaded: false });
    setCachedDocument("/b.pdf", { document: makeMockDocument(), outline: [], outlineLoaded: false });
    setCachedDocument("/c.pdf", { document: makeMockDocument(), outline: [], outlineLoaded: false });
    clearDocumentCache();
    expect(mockDestroy).toHaveBeenCalledTimes(3);
    expect(documentCache.size).toBe(0);
  });

  it("documentCache.destroy errors are swallowed", async () => {
    mockDestroy.mockRejectedValueOnce(new Error("destroy failed"));
    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });
    await expect(() => removeCachedDocument("/book.pdf")).not.toThrow();
    expect(getCachedDocument("/book.pdf")).toBeUndefined();
  });
});

describe("pdfStreaming — LRU Eviction", () => {
  beforeEach(() => {
    resetAllMocks();
  });

  it("evicts oldest entry when cache exceeds max (8)", () => {
    const docs: Array<Record<string, unknown>> = [];
    for (let i = 0; i < 10; i++) {
      const doc = makeMockDocument();
      docs.push(doc);
      setCachedDocument(`/book-${i}.pdf`, { document: doc, outline: [], outlineLoaded: false });
    }

    expect(documentCache.size).toBeLessThanOrEqual(8);

    expect(getCachedDocument("/book-0.pdf")).toBeUndefined();
    expect(getCachedDocument("/book-1.pdf")).toBeUndefined();

    expect(getCachedDocument("/book-9.pdf")).toBeDefined();
    expect(getCachedDocument("/book-8.pdf")).toBeDefined();

    expect(mockDestroy).toHaveBeenCalled();
  });

  it("re-setting same key does not trigger eviction", () => {
    for (let i = 0; i < 8; i++) {
      setCachedDocument(`/book-${i}.pdf`, { document: makeMockDocument(), outline: [], outlineLoaded: false });
    }
    setCachedDocument("/book-0.pdf", { document: makeMockDocument(), outline: [], outlineLoaded: true });
    expect(documentCache.size).toBe(8);
  });
});

describe("pdfStreaming — loadPdfOutline", () => {
  beforeEach(() => {
    resetAllMocks();
  });

  it("calls document.getOutline() and caches the result", async () => {
    const rawOutline = [
      { title: "Chapter 1", dest: "page=1", items: [] },
      { title: "Chapter 2", dest: "page=5", items: [
        { title: "Section 2.1", dest: "page=5", items: [] },
      ]},
    ];
    mockGetOutline.mockResolvedValueOnce(rawOutline);

    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });

    const outline = await loadPdfOutline(doc, "/book.pdf");

    expect(mockGetOutline).toHaveBeenCalledTimes(1);
    expect(outline).toHaveLength(2);
    expect(outline[0].title).toBe("Chapter 1");
    expect(outline[1].items).toHaveLength(1);
    expect(outline[1].items[0].title).toBe("Section 2.1");

    expect(getCachedDocument("/book.pdf")!.outlineLoaded).toBe(true);
    expect(getCachedDocument("/book.pdf")!.outline).toHaveLength(2);
  });

  it("returns cached outline on second call without re-fetching", async () => {
    mockGetOutline.mockResolvedValueOnce([{ title: "Ch1", dest: null, items: [] }]);

    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });

    const first = await loadPdfOutline(doc, "/book.pdf");
    expect(first).toHaveLength(1);

    mockGetOutline.mockClear();
    const second = await loadPdfOutline(doc, "/book.pdf");
    expect(second).toHaveLength(1);
    expect(mockGetOutline).not.toHaveBeenCalled();
  });

  it("normalizes outline items with null/undefined titles", async () => {
    mockGetOutline.mockResolvedValueOnce([
      { title: null, dest: null, items: [] },
      { title: "   ", dest: null, items: [] },
      { title: "Real Title", dest: null, items: [] },
    ]);

    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });

    const outline = await loadPdfOutline(doc, "/book.pdf");
    expect(outline[0].title).toBe("Untitled");
    expect(outline[1].title).toBe("Untitled");
    expect(outline[2].title).toBe("Real Title");
  });

  it("handles null getOutline response", async () => {
    mockGetOutline.mockResolvedValueOnce(null);

    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });

    const outline = await loadPdfOutline(doc, "/book.pdf");
    expect(outline).toEqual([]);
    expect(getCachedDocument("/book.pdf")!.outlineLoaded).toBe(true);
  });
});

describe("pdfStreaming — createPdfDocument", () => {
  beforeEach(() => {
    resetAllMocks();
    mockGetFileBytes.mockResolvedValue(new Array(500 * 1024).fill(0));
  });

  it("loads via getFileBytes and caches the result", async () => {
    const result = await createPdfDocument("/large.pdf");
    expect(result.document).toBeDefined();
    expect(mockGetFileBytes).toHaveBeenCalledWith("/large.pdf");

    const cached = getCachedDocument("/large.pdf");
    expect(cached).toBeDefined();
    expect(cached!.outlineLoaded).toBe(false);
  });

  it("returns cached document on second call for same path", async () => {
    const first = await createPdfDocument("/book.pdf");
    const firstDoc = first.document;

    mockGetFileBytes.mockClear();

    const second = await createPdfDocument("/book.pdf");
    expect(second.document).toBe(firstDoc);
    expect(mockGetFileBytes).not.toHaveBeenCalled();
  });

  it("passes onProgress callback to loadingTask", async () => {
    const onProgress = vi.fn();
    const result = await createPdfDocument("/book.pdf", { onProgress });
    expect(result.document).toBeDefined();
  });
});
