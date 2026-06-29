// ──────────────────────────────────────────
// EPUB Blob Cache + Deferred TOC support
// ──────────────────────────────────────────

export interface EpubCacheEntry {
  /** The raw EPUB file as an ArrayBuffer (avoid re-reading from disk) */
  data: ArrayBuffer;
  /** Cached TOC entries (loaded lazily) */
  toc: Array<{ id: string; label: string; href: string }>;
  /** Whether TOC has been loaded */
  tocLoaded: boolean;
}

const epubCache = new Map<string, EpubCacheEntry>();
const MAX_CACHED_BOOKS = 6;

export function getCachedEpub(filePath: string): EpubCacheEntry | undefined {
  return epubCache.get(filePath);
}

export function setCachedEpub(filePath: string, entry: EpubCacheEntry): void {
  if (epubCache.size >= MAX_CACHED_BOOKS) {
    const firstKey = epubCache.keys().next().value;
    if (firstKey) {
      removeCachedEpub(firstKey);
    }
  }
  epubCache.set(filePath, entry);
}

export function removeCachedEpub(filePath: string): void {
  epubCache.delete(filePath);
}

export function clearEpubCache(): void {
  epubCache.clear();
}

/** Returns the cached blob if available, or null */
export function getEpubBlob(filePath: string): ArrayBuffer | null {
  const entry = epubCache.get(filePath);
  return entry?.data ?? null;
}

/** Returns cached TOC or null if not loaded yet */
export function getCachedEpubToc(
  filePath: string,
): Array<{ id: string; label: string; href: string }> | null {
  const entry = epubCache.get(filePath);
  if (entry?.tocLoaded) {
    return entry.toc;
  }
  return null;
}

/** Store TOC in cache after lazy loading */
export function setCachedEpubToc(
  filePath: string,
  toc: Array<{ id: string; label: string; href: string }>,
): void {
  const existing = epubCache.get(filePath);
  if (existing) {
    existing.toc = toc;
    existing.tocLoaded = true;
  } else {
    // Should not happen if blob was cached first, but handle gracefully
    epubCache.set(filePath, {
      data: new ArrayBuffer(0),
      toc,
      tocLoaded: true,
    });
  }
}

export { epubCache };
