import * as pdfjsLib from "pdfjs-dist";
import type { PDFDocumentProxy, PDFDocumentLoadingTask } from "pdfjs-dist";
import { getFileBytes } from "$lib/shared/api/tauriClient";
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
// 2. PDF Loading (full-file via Tauri IPC)
// ──────────────────────────────────────────

/**
 * Loads a PDF document by reading the entire file via Tauri IPC.
 * For local files this is faster and more reliable than range-request
 * streaming, which pdfjs-dist v5.x no longer supports via PDFDataRangeTransport.
 */
async function loadPdfFromFile(
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

  // Load the full file via Tauri IPC
  const result = await loadPdfFromFile(filePath, options?.onProgress);

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
