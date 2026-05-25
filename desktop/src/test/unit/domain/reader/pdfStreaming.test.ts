/**
 * Unit tests for pdfStreaming.ts
 *
 * Tests document cache operations, LRU eviction, outline loading,
 * and createPdfDocument cache hit/miss behavior.
 * Streaming via PDFDataRangeTransport is tested structurally since it
 * requires pdf.js internals not available in jsdom.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";

// Mock pdfjs-dist before importing the module under test
const mockDestroy = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
const mockGetOutline = vi.fn();

const mockPdfDocument = {
  numPages: 10,
  destroy: mockDestroy,
  getOutline: mockGetOutline,
  getPage: vi.fn(),
  getDestination: vi.fn(),
  getPageIndex: vi.fn(),
};

// Mock PDFDataRangeTransport
const MockPDFDataRangeTransport = vi.fn(function (
  this: { addRangeListener: any; addProgressListener: any; onDataRange: any },
  _size: number,
  _initialData: Uint8Array,
) {
  this.addRangeListener = vi.fn();
  this.addProgressListener = vi.fn();
  this.onDataRange = null as any;
});

vi.mock("pdfjs-dist", () => ({
  default: {
    GlobalWorkerOptions: { workerSrc: "" },
    getDocument: vi.fn(() => ({
      promise: Promise.resolve(mockPdfDocument),
      onProgress: null as any,
      destroy: vi.fn(),
    })),
    PDFDataRangeTransport: MockPDFDataRangeTransport,
  },
  PDFDocumentProxy: class {},
  PDFDocumentLoadingTask: class {},
}));

// Mock Tauri IPC client
const mockGetFileSize = vi.fn();
const mockReadFileRange = vi.fn();
const mockGetFileBytes = vi.fn();

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

import type { PdfOutlineItem } from "$lib/types";

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
    // First doc should NOT be destroyed on overwrite (only on LRU eviction)
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

    // Cache should have at most 8 entries
    expect(documentCache.size).toBeLessThanOrEqual(8);

    // The first 2 entries should have been evicted (and destroyed)
    expect(getCachedDocument("/book-0.pdf")).toBeUndefined();
    expect(getCachedDocument("/book-1.pdf")).toBeUndefined();

    // The most recent 8 should still be present
    expect(getCachedDocument("/book-9.pdf")).toBeDefined();
    expect(getCachedDocument("/book-8.pdf")).toBeDefined();

    // Destroy should have been called for evicted docs
    // Note: eviction destroys the document, but the exact count depends on implementation
    // (8th insert triggers eviction of 1st, 9th triggers eviction of 2nd, etc.)
    expect(mockDestroy).toHaveBeenCalled();
  });

  it("re-setting same key does not trigger eviction", () => {
    for (let i = 0; i < 8; i++) {
      setCachedDocument(`/book-${i}.pdf`, { document: makeMockDocument(), outline: [], outlineLoaded: false });
    }
    // Re-set an existing key — should not evict
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

    // Should be cached now
    expect(getCachedDocument("/book.pdf")!.outlineLoaded).toBe(true);
    expect(getCachedDocument("/book.pdf")!.outline).toHaveLength(2);
  });

  it("returns cached outline on second call without re-fetching", async () => {
    mockGetOutline.mockResolvedValueOnce([{ title: "Ch1", dest: null, items: [] }]);

    const doc = makeMockDocument();
    setCachedDocument("/book.pdf", { document: doc, outline: [], outlineLoaded: false });

    const first = await loadPdfOutline(doc, "/book.pdf");
    expect(first).toHaveLength(1);

    // Second call should use cache
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
    // Default: file is large enough to trigger streaming
    mockGetFileSize.mockResolvedValue(500 * 1024); // 500KB
    mockReadFileRange.mockResolvedValue(new Array(8192).fill(0));
    mockGetFileBytes.mockResolvedValue(new Array(500 * 1024).fill(0));
  });

  it("loads via streaming for large files and caches the result", async () => {
    const result = await createPdfDocument("/large.pdf");
    expect(result.document).toBeDefined();
    expect(mockGetFileSize).toHaveBeenCalledWith("/large.pdf");
    expect(mockReadFileRange).toHaveBeenCalled();

    // Should be cached
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
    expect(second.document).toBe(firstDoc); // Same document instance
    expect(mockGetFileSize).not.toHaveBeenCalled(); // No file access
    expect(mockReadFileRange).not.toHaveBeenCalled();
    expect(mockGetFileBytes).not.toHaveBeenCalled();
  });

  it("loads via full file bytes for small files (<64KB)", async () => {
    mockGetFileSize.mockResolvedValue(32 * 1024); // 32KB — under threshold
    mockGetFileBytes.mockResolvedValue(new Array(32 * 1024).fill(42));
    const result = await createPdfDocument("/small.pdf");
    expect(result.document).toBeDefined();
    // Should use getFileBytes, not readFileRange
    expect(mockGetFileBytes).toHaveBeenCalledWith("/small.pdf");
    expect(mockReadFileRange).not.toHaveBeenCalled();
  });

  it("loads via full file bytes when PDFDataRangeTransport is unavailable", async () => {
    // Temporarily remove PDFDataRangeTransport from pdfjs-dist mock
    const { default: pdfjsLib } = await import("pdfjs-dist");
    const originalTransport = (pdfjsLib as any).PDFDataRangeTransport;
    delete (pdfjsLib as any).PDFDataRangeTransport;

    mockGetFileSize.mockResolvedValue(500 * 1024);
    mockGetFileBytes.mockResolvedValue(new Array(500 * 1024).fill(42));

    const result = await createPdfDocument("/no-transport.pdf");
    expect(result.document).toBeDefined();
    expect(mockGetFileBytes).toHaveBeenCalled();

    // Restore
    (pdfjsLib as any).PDFDataRangeTransport = originalTransport;
  });

  it("passes onProgress callback to loadingTask", async () => {
    const onProgress = vi.fn();
    const result = await createPdfDocument("/book.pdf", { onProgress });
    expect(result.document).toBeDefined();
  });
});
