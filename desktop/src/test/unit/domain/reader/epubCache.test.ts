/**
 * Unit tests for epubCache.ts
 *
 * Tests blob cache operations, LRU eviction, deferred TOC caching,
 * and edge cases (empty data, missing entries, etc.).
 * Pure cache logic — no external dependencies needed.
 */
import { beforeEach, describe, expect, it } from "vitest";

import {
  getCachedEpub,
  setCachedEpub,
  removeCachedEpub,
  clearEpubCache,
  getEpubBlob,
  getCachedEpubToc,
  setCachedEpubToc,
  epubCache,
} from "$lib/features/reader/epub/epubCache";

function resetCache() {
  epubCache.clear();
}

function makeEntry(overrides: Partial<{
  data: ArrayBuffer;
  toc: Array<{ id: string; label: string; href: string }>;
  tocLoaded: boolean;
}> = {}) {
  return {
    data: new ArrayBuffer(1024),
    toc: [],
    tocLoaded: false,
    ...overrides,
  };
}

describe("epubCache — Blob Cache", () => {
  beforeEach(() => {
    resetCache();
  });

  it("getCachedEpub returns undefined for unset key", () => {
    expect(getCachedEpub("/unknown.epub")).toBeUndefined();
  });

  it("setCachedEpub and getCachedEpub round-trip", () => {
    const entry = makeEntry();
    setCachedEpub("/book.epub", entry);
    expect(getCachedEpub("/book.epub")).toBe(entry);
  });

  it("setCachedEpub overwrites existing entry for same path", () => {
    const entry1 = makeEntry({ data: new ArrayBuffer(512) });
    const entry2 = makeEntry({ data: new ArrayBuffer(2048), tocLoaded: true });
    setCachedEpub("/book.epub", entry1);
    setCachedEpub("/book.epub", entry2);
    const cached = getCachedEpub("/book.epub");
    expect(cached!.data.byteLength).toBe(2048);
    expect(cached!.tocLoaded).toBe(true);
  });

  it("removeCachedEpub removes entry", () => {
    setCachedEpub("/book.epub", makeEntry());
    removeCachedEpub("/book.epub");
    expect(getCachedEpub("/book.epub")).toBeUndefined();
  });

  it("removeCachedEpub is no-op for missing key", () => {
    expect(() => removeCachedEpub("/nonexistent.epub")).not.toThrow();
  });

  it("clearEpubCache removes all entries", () => {
    setCachedEpub("/a.epub", makeEntry());
    setCachedEpub("/b.epub", makeEntry());
    clearEpubCache();
    expect(epubCache.size).toBe(0);
  });

  it("getEpubBlob returns blob data or null", () => {
    const data = new ArrayBuffer(2048);
    setCachedEpub("/book.epub", makeEntry({ data }));
    expect(getEpubBlob("/book.epub")?.byteLength).toBe(2048);
    expect(getEpubBlob("/unknown.epub")).toBeNull();
  });

  it("maintains independent entries for different file paths", () => {
    const dataA = new ArrayBuffer(100);
    const dataB = new ArrayBuffer(200);
    setCachedEpub("/a.epub", makeEntry({ data: dataA }));
    setCachedEpub("/b.epub", makeEntry({ data: dataB }));
    expect(getEpubBlob("/a.epub")?.byteLength).toBe(100);
    expect(getEpubBlob("/b.epub")?.byteLength).toBe(200);
  });
});

describe("epubCache — LRU Eviction", () => {
  beforeEach(() => {
    resetCache();
  });

  it("evicts oldest entry when cache exceeds max (6)", () => {
    for (let i = 0; i < 8; i++) {
      setCachedEpub(`/book-${i}.epub`, makeEntry());
    }

    // Cache should have at most 6 entries
    expect(epubCache.size).toBeLessThanOrEqual(6);

    // The first 2 entries should have been evicted
    expect(getCachedEpub("/book-0.epub")).toBeUndefined();
    expect(getCachedEpub("/book-1.epub")).toBeUndefined();

    // The most recent 6 should still be present
    expect(getCachedEpub("/book-7.epub")).toBeDefined();
    expect(getCachedEpub("/book-6.epub")).toBeDefined();
    expect(getCachedEpub("/book-5.epub")).toBeDefined();
  });

  it("re-setting same key does not trigger eviction", () => {
    for (let i = 0; i < 6; i++) {
      setCachedEpub(`/book-${i}.epub`, makeEntry());
    }
    // Re-set an existing key — should not evict
    setCachedEpub("/book-0.epub", makeEntry({ data: new ArrayBuffer(999) }));
    expect(epubCache.size).toBe(6);
    expect(getCachedEpub("/book-0.epub")!.data.byteLength).toBe(999);
  });
});

describe("epubCache — Deferred TOC", () => {
  beforeEach(() => {
    resetCache();
  });

  it("getCachedEpubToc returns null when TOC not loaded", () => {
    setCachedEpub("/book.epub", makeEntry({ tocLoaded: false }));
    expect(getCachedEpubToc("/book.epub")).toBeNull();
  });

  it("getCachedEpubToc returns null for missing entry", () => {
    expect(getCachedEpubToc("/nonexistent.epub")).toBeNull();
  });

  it("setCachedEpubToc updates existing entry with TOC", () => {
    const toc = [
      { id: "ch1", label: "Chapter 1", href: "chap1.xhtml" },
      { id: "ch2", label: "Chapter 2", href: "chap2.xhtml" },
    ];
    setCachedEpub("/book.epub", makeEntry());
    setCachedEpubToc("/book.epub", toc);

    const cached = getCachedEpub("/book.epub");
    expect(cached!.toc).toEqual(toc);
    expect(cached!.tocLoaded).toBe(true);
  });

  it("setCachedEpubToc returns cached TOC after loading", () => {
    const toc = [
      { id: "ch1", label: "Chapter 1", href: "chap1.xhtml" },
    ];
    setCachedEpub("/book.epub", makeEntry());
    setCachedEpubToc("/book.epub", toc);

    const result = getCachedEpubToc("/book.epub");
    expect(result).toEqual(toc);
  });

  it("setCachedEpubToc creates fallback entry if blob not cached", () => {
    const toc = [{ id: "ch1", label: "Chapter 1", href: "chap1.xhtml" }];
    // Call setCachedEpubToc WITHOUT having called setCachedEpub first
    setCachedEpubToc("/book.epub", toc);

    // Should have created a fallback entry with empty ArrayBuffer
    const entry = getCachedEpub("/book.epub");
    expect(entry).toBeDefined();
    expect(entry!.data.byteLength).toBe(0);
    expect(entry!.toc).toEqual(toc);
    expect(entry!.tocLoaded).toBe(true);
    expect(getCachedEpubToc("/book.epub")).toEqual(toc);
  });

  it("getCachedEpubToc returns null after tocLoaded is reset", () => {
    const toc = [{ id: "ch1", label: "Chapter 1", href: "chap1.xhtml" }];
    setCachedEpub("/book.epub", makeEntry());
    setCachedEpubToc("/book.epub", toc);
    expect(getCachedEpubToc("/book.epub")).toEqual(toc);

    // Simulate a new entry without TOC (e.g., re-opening with fresh cache)
    setCachedEpub("/book.epub", makeEntry({ tocLoaded: false }));
    expect(getCachedEpubToc("/book.epub")).toBeNull();
  });
});

describe("epubCache — Edge Cases", () => {
  beforeEach(() => {
    resetCache();
  });

  it("handles empty ArrayBuffer", () => {
    setCachedEpub("/empty.epub", makeEntry({ data: new ArrayBuffer(0) }));
    expect(getEpubBlob("/empty.epub")?.byteLength).toBe(0);
    expect(getCachedEpub("/empty.epub")).toBeDefined();
  });

  it("handles null filePath gracefully", () => {
    expect(getCachedEpub(null as unknown as string)).toBeUndefined();
    expect(() => setCachedEpub(null as unknown as string, makeEntry())).not.toThrow();
    expect(() => removeCachedEpub(null as unknown as string)).not.toThrow();
  });

  it("handles undefined filePath gracefully", () => {
    expect(getCachedEpub(undefined as unknown as string)).toBeUndefined();
    expect(() => setCachedEpub(undefined as unknown as string, makeEntry())).not.toThrow();
    expect(() => removeCachedEpub(undefined as unknown as string)).not.toThrow();
  });
});
