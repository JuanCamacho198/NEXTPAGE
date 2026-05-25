import * as pdfjsLib from "pdfjs-dist";
import type { PDFDocumentProxy, PDFDocumentLoadingTask } from "pdfjs-dist";
import { getFileSize, readFileRange, getFileBytes } from "$lib/api/tauriClient";
import type { PdfOutlineItem } from "$lib/types";

// ──────────────────────────────────────────
// 1. Document Cache
// ──────────────────────────────────────────

export interface PdfCacheEntry {
  document: PDFDocumentProxy;
  outline: PdfOutlineItem[];
  outlineLoaded: boolean;
}

const documentCache = new Map<string, PdfCacheEntry>();
const MAX_CACHED_DOCUMENTS = 8;

export function getCachedDocument(filePath: string): PdfCacheEntry | undefined {
  return documentCache.get(filePath);
}

export function setCachedDocument(filePath: string, entry: PdfCacheEntry): void {
  if (documentCache.size >= MAX_CACHED_DOCUMENTS) {
    const firstKey = documentCache.keys().next().value;
    if (firstKey) {
      removeCachedDocument(firstKey);
    }
  }
  documentCache.set(filePath, entry);
}

export function removeCachedDocument(filePath: string): void {
  const entry = documentCache.get(filePath);
  if (entry) {
    entry.document.destroy().catch(() => {
      // swallow teardown errors
    });
    documentCache.delete(filePath);
  }
}

export function clearDocumentCache(): void {
  for (const [key] of documentCache) {
    removeCachedDocument(key);
  }
}

// ──────────────────────────────────────────
// 2. PDF Range Transport (streaming via IPC)
// ──────────────────────────────────────────

type RangeRequestCallback = (begin: number, end: number) => void;

/**
 * Creates a streaming PDF document using IPC range requests.
 * Falls back to loading the entire file if PDFDataRangeTransport is unavailable
 * or if the file is too small (under 64KB, where streaming overhead isn't worth it).
 */
async function loadStreamingPdf(
  filePath: string,
  onProgress?: (loaded: number, total: number) => void,
): Promise<{ loadingTask: PDFDocumentLoadingTask; document: PDFDocumentProxy }> {
  const size = await getFileSize(filePath);

  // For small files, just load the whole thing — range overhead isn't worth it
  const SMALL_FILE_THRESHOLD = 64 * 1024;
  if (size <= SMALL_FILE_THRESHOLD) {
    return loadFullPdf(filePath, onProgress);
  }

  // Fetch initial header (first 8KB) for PDF.js to parse the cross-reference table
  const HEADER_SIZE = 8192;
  const initialChunk: number[] =
    size > 0 ? await readFileRange(filePath, 0, Math.min(HEADER_SIZE, size)) : [];

  // Try to use PDFDataRangeTransport for true streaming
  const PdfRangeTransport = (pdfjsLib as any).PDFDataRangeTransport;

  if (typeof PdfRangeTransport !== "function") {
    // PDFDataRangeTransport not available in this version — fall back to full load
    return loadFullPdf(filePath, onProgress);
  }

  const transport = new PdfRangeTransport(size, new Uint8Array(initialChunk));

  transport.addRangeListener((begin: number, end: number) => {
    const fetchRange = async () => {
      try {
        const chunk = await readFileRange(filePath, begin, end - begin);
        if (transport.onDataRange) {
          transport.onDataRange(begin, new Uint8Array(chunk));
        }
      } catch (err) {
        console.error("[PdfStreaming] Range fetch failed:", err);
      }
    };
    fetchRange();
  });

  if (onProgress) {
    transport.addProgressListener((loaded: number, total: number) => {
      onProgress(loaded, total);
    });
  }

  const loadingTask = pdfjsLib.getDocument(transport);

  if (onProgress) {
    loadingTask.onProgress = (progress: { loaded: number; total: number }) => {
      onProgress(progress.loaded, progress.total);
    };
  }

  const document = await loadingTask.promise;
  return { loadingTask, document };
}

async function loadFullPdf(
  filePath: string,
  onProgress?: (loaded: number, total: number) => void,
): Promise<{ loadingTask: PDFDocumentLoadingTask; document: PDFDocumentProxy }> {
  const fileData = await getFileBytes(filePath);

  const loadingTask = pdfjsLib.getDocument({ data: new Uint8Array(fileData) });

  if (onProgress) {
    loadingTask.onProgress = (progress: { loaded: number; total: number }) => {
      onProgress(progress.loaded, progress.total);
    };
  }

  const document = await loadingTask.promise;
  return { loadingTask, document };
}

// ──────────────────────────────────────────
// 3. Lazy outline loading
// ──────────────────────────────────────────

/**
 * Load the PDF outline/TOC. Call this lazily (e.g., when user opens the TOC panel)
 * rather than eagerly during document load.
 */
export async function loadPdfOutline(
  document: PDFDocumentProxy,
  filePath: string,
): Promise<PdfOutlineItem[]> {
  const cached = documentCache.get(filePath);
  if (cached?.outlineLoaded) {
    return cached.outline;
  }

  const rawOutline = await document.getOutline();
  const outline = normalizeOutlineItems(rawOutline ?? []);

  if (cached) {
    cached.outline = outline;
    cached.outlineLoaded = true;
  }

  return outline;
}

// ──────────────────────────────────────────
// 4. Public API
// ──────────────────────────────────────────

export async function createPdfDocument(
  filePath: string,
  options?: {
    onProgress?: (loaded: number, total: number) => void;
  },
): Promise<{ document: PDFDocumentProxy; loadingTask: PDFDocumentLoadingTask }> {
  // Check cache first
  const cached = documentCache.get(filePath);
  if (cached) {
    return { document: cached.document, loadingTask: null as unknown as PDFDocumentLoadingTask };
  }

  // Load streaming
  const result = await loadStreamingPdf(filePath, options?.onProgress);

  // Cache the document (outline loaded lazily by loadPdfOutline)
  documentCache.set(filePath, {
    document: result.document,
    outline: [],
    outlineLoaded: false,
  });

  return result;
}

export { documentCache };

// ──────────────────────────────────────────
// 5. Internal helpers
// ──────────────────────────────────────────

interface RawOutlineItem {
  title?: unknown;
  dest?: unknown;
  items?: unknown[];
}

function normalizeOutlineItems(
  rawItems: unknown[],
  parentId = "outline",
): PdfOutlineItem[] {
  const normalized: PdfOutlineItem[] = [];

  rawItems.forEach((rawItem, index) => {
    if (!rawItem || typeof rawItem !== "object") {
      return;
    }

    const item = rawItem as RawOutlineItem;
    const children = Array.isArray(item.items)
      ? normalizeOutlineItems(item.items, `${parentId}-${index}`)
      : [];

    const destination =
      typeof item.dest === "string" || Array.isArray(item.dest) ? item.dest : null;

    normalized.push({
      id: `${parentId}-${index}`,
      title: toOutlineTitle(item.title),
      dest: destination,
      items: children,
    });
  });

  return normalized;
}

function toOutlineTitle(title: unknown): string {
  if (typeof title !== "string") {
    return "Untitled";
  }
  const normalized = title.trim();
  return normalized.length > 0 ? normalized : "Untitled";
}
