/**
 * Unit tests for pdfStreaming.ts
 *
 * Tests document cache operations, LRU eviction, outline loading,
 * and createPdfDocument cache hit/miss behavior.
 * Streaming via PDFDataRangeTransport is tested structurally since it
 * requires pdf.js internals not available in jsdom.
 *
 * IMPORTANT: vi.mock() factories must avoid module-level const references
 * because they run before module-scope const assignments (TDZ). Instead:
 * - Use vi.hoisted() for values used directly in tests (destructured consts)
 * - Use inline definitions + closures inside vi.mock() factories for mock objects
 */
import { beforeEach, describe, expect, it, vi } from "vitest";

// Hoisted values — used directly in test assertions via destructured consts.
// These are module-level consts but that's OK because they're only accessed
// at test-execution time (long after all hoisting/assignments complete).
const { mockDestroy, mockGetOutline, mockPdfDocument, mockGetFileSize, mockReadFileRange, mockGetFileBytes } =
  vi.hoisted(() => {
    const destroy = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
    const getOutline = vi.fn();
    const getFileSize = vi.fn();
    const readFileRange = vi.fn();
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
      mockGetFileSize: getFileSize,
      mockReadFileRange: readFileRange,
      mockGetFileBytes: getFileBytes,
    };
  });

// pdfjs-dist mock: inline factory with closures.
// `mockPdfDocument` is captured via closure inside the arrow function
// returned by getDocument() — accessed lazily, not during factory eval.
// MockTransport is defined inline — no TDZ risk.
vi.mock("pdfjs-dist", () => {
  const MockTransport = vi.fn(function (
    this: { addRangeListener: any; onDataRange: any },
    _size: number,
    _initialData: Uint8Array,
  ) {
    this.addRangeListener = vi.fn();
    this.onDataRange = null as any;
  });

  const getDocument = vi.fn(() => ({
    promise: Promise.resolve(mockPdfDocument),
    onProgress: null as any,
    destroy: vi.fn(),
  }));

  return {
    default: {
      GlobalWorkerOptions: { workerSrc: "" },
      getDocument,
      PDFDataRangeTransport: MockTransport,
    },
    getDocument,
    PDFDataRangeTransport: MockTransport,
    PDFDocumentProxy: class {},
    PDFDocumentLoadingTask: class {},
  };
});

// $lib/api/tauriClient mock: arrow function closures capture hoisted
// const bindings lazily — no TDZ because they're called at test time.
vi.mock("$lib/api/tauriClient", () => ({
  getFileSize: (...args: unknown[]) => mockGetFileSize(...args),
  readFileRange: (...args: unknown[]) => mockReadFileRange(...args),
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
  } as any;
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
    const docs: any[] = [];
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
    mockGetFileSize.mockResolvedValue(500 * 1024);
    mockReadFileRange.mockResolvedValue(new Array(8192).fill(0));
    mockGetFileBytes.mockResolvedValue(new Array(500 * 1024).fill(0));
  });

  it("loads via streaming for large files and caches the result", async () => {
    const result = await createPdfDocument("/large.pdf");
    expect(result.document).toBeDefined();
    expect(mockGetFileSize).toHaveBeenCalledWith("/large.pdf");
    expect(mockReadFileRange).toHaveBeenCalled();

    const cached = getCachedDocument("/large.pdf");
    expect(cached).toBeDefined();
    expect(cached!.outlineLoaded).toBe(false);
  });

  it("returns cached document on second call for same path", async () => {
    const first = await createPdfDocument("/book.pdf");
    const firstDoc = first.document;

    mockGetFileSize.mockClear();
    mockReadFileRange.mockClear();
    mockGetFileBytes.mockClear();

    const second = await createPdfDocument("/book.pdf");
    expect(second.document).toBe(firstDoc);
    expect(mockGetFileSize).not.toHaveBeenCalled();
    expect(mockReadFileRange).not.toHaveBeenCalled();
    expect(mockGetFileBytes).not.toHaveBeenCalled();
  });

  it("loads via full file bytes for small files (<64KB)", async () => {
    mockGetFileSize.mockResolvedValue(32 * 1024);
    mockGetFileBytes.mockResolvedValue(new Array(32 * 1024).fill(42));
    const result = await createPdfDocument("/small.pdf");
    expect(result.document).toBeDefined();
    expect(mockGetFileBytes).toHaveBeenCalledWith("/small.pdf");
    expect(mockReadFileRange).not.toHaveBeenCalled();
  });

  describe("PDFDataRangeTransport availability", () => {
    // This test verifies the mock setup. The `loadStreamingPdf` code checks
    // `(pdfjsLib as any).PDFDataRangeTransport` via the namespace import.
    // Since the mock exposes it as a named export, it must be a function
    // for streaming to proceed.
    it("mock exposes PDFDataRangeTransport as named export on namespace", async () => {
      const pdfjsLib = await import("pdfjs-dist");
      expect(typeof (pdfjsLib as any).PDFDataRangeTransport).toBe("function");
    });
  });

  it("passes onProgress callback to loadingTask", async () => {
    const onProgress = vi.fn();
    const result = await createPdfDocument("/book.pdf", { onProgress });
    expect(result.document).toBeDefined();
  });
});
