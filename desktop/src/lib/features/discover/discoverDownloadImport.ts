/**
 * Discover download-to-import bridge (WU2).
 *
 * Fetches the open detail book's catalog URL with progress/cancel support,
 * then reuses the verified `importRecoveredBook` pipeline via a synthetic
 * entry: no `contentHash` (hash check skipped) with EPUB metadata fallback,
 * mirroring the shelf download flow. The entry is marked imported only when
 * a live user session exists; otherwise the mark step is a no-op.
 */
import { authState } from '$lib/shared/stores/AuthState.svelte';
import type { CatalogBook } from '$lib/shared/services/catalog';
import type { LibraryPort } from '$lib/shared/ports/LibraryPort';
import { TauriLibraryAdapter } from '$lib/shared/ports/adapters/tauri/TauriLibraryAdapter';
import { importRecoveredBook, type ImportDeps } from '$lib/shared/recovery/desktopRecoveryImport';
import type { SupabaseUserBookRow } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { SupabaseBookCatalogSync } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { extractEpubMetadataFromBytes } from '$lib/shared/services/epubImportMetadata';

export type DiscoverProgressFn = (doneBytes: number, totalBytes: number | null) => void;

export interface DiscoverDownloadPorts {
  fetchFn?: typeof fetch;
  importFn?: typeof importRecoveredBook;
  persist?: ImportDeps['persist'];
  markImported?: ImportDeps['markImported'];
}

let discoverLibraryPort: LibraryPort = new TauriLibraryAdapter();

/** Test seam: swap the persistence port without touching the app wiring. */
export function setDiscoverLibraryPort(port: LibraryPort): void {
  discoverLibraryPort = port;
}

/** Entries that never carried real metadata keep the id as their title. */
const UUID_LIKE_TITLE_RE = /^[0-9a-f-]{36}$/i;

function isFallbackTitle(title: string, id: string): boolean {
  return title === id || UUID_LIKE_TITLE_RE.test(title);
}

function totalOrNull(header: string | null): number | null {
  if (header === null || header.trim() === '') return null;
  const parsed = Number(header);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

/**
 * Fetch a catalog URL into memory, reporting byte progress. Rejects with an
 * `AbortError` when `signal` aborts; rejects with the HTTP status line on
 * non-2xx responses (redacted at the call site to a code-only message).
 */
export async function fetchBytesWithProgress(
  url: string,
  signal: AbortSignal,
  onProgress: DiscoverProgressFn,
  fetchFn: typeof fetch = fetch,
): Promise<Uint8Array> {
  const response = await fetchFn(url, { signal });
  if (!response.ok) throw new Error(`DOWNLOAD_HTTP_${response.status}`);
  const total = totalOrNull(response.headers.get('content-length'));
  const reader = response.body?.getReader();
  if (!reader) {
    const fallback = new Uint8Array(await response.arrayBuffer());
    onProgress(fallback.length, total);
    return fallback;
  }
  const chunks: Uint8Array[] = [];
  let done = 0;
  for (;;) {
    const { done: finished, value } = await reader.read();
    if (finished) break;
    if (value) {
      chunks.push(value);
      done += value.length;
      onProgress(done, total);
    }
  }
  const merged = new Uint8Array(done);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  onProgress(done, total);
  return merged;
}

/** Synthetic entry so the verified import pipeline can be reused as-is. */
export function buildDiscoverImportRow(book: CatalogBook, nowIso?: string): SupabaseUserBookRow {
  const now = nowIso ?? new Date().toISOString();
  return {
    id: book.id,
    userId: authState.userId ?? '',
    title: book.title,
    author: book.authors[0] ?? '',
    format: 'epub',
    filePath: null,
    coverUrl: null,
    description: null,
    totalPages: null,
    sourceDevice: null,
    importedAt: now,
    updatedAt: now,
    lifecycle: 'available',
    catalogVersion: 1,
  };
}

async function defaultPersist(
  bookId: string,
  bytes: Uint8Array,
  meta: { title: string; author: string; format: string },
): Promise<void> {
  let { title, author } = meta;
  if (meta.format === 'epub' && isFallbackTitle(title, bookId)) {
    try {
      const real = await extractEpubMetadataFromBytes(bytes);
      if (real.title) title = real.title;
      if (real.author) author = real.author;
    } catch {
      // keep catalog title
    }
  }
  await discoverLibraryPort.saveBookFile(bookId, Array.from(bytes), {
    title,
    author,
    format: meta.format,
  });
}

export interface DiscoverImportResult {
  ok: boolean;
  error: string | null;
}

/**
 * Import already-fetched catalog bytes into the library. Returns
 * `{ ok: true }` for both fresh and idempotent (`already_imported`)
 * outcomes; anything else maps to `{ ok: false, error }` for retry UI.
 * The `download` closure serves the fetched bytes so no second request
 * is issued — a cancelled fetch therefore never reaches persistence.
 */
export async function importDiscoverBytes(
  book: CatalogBook,
  bytes: Uint8Array,
  ports: DiscoverDownloadPorts = {},
): Promise<DiscoverImportResult> {
  const row = buildDiscoverImportRow(book);
  const importFn = ports.importFn ?? importRecoveredBook;
  const result = await importFn(row, {
    download: async () => bytes,
    persist: ports.persist ?? defaultPersist,
    markImported:
      ports.markImported ??
      (async (id, version) => {
        const userId = authState.userId;
        if (!userId) return;
        await new SupabaseBookCatalogSync(userId).upsertBook({
          ...row,
          id,
          lifecycle: 'imported',
          catalogVersion: version,
          updatedAt: new Date().toISOString(),
        });
      }),
  });
  if (result.outcome === 'imported' || result.outcome === 'already_imported') {
    return { ok: true, error: null };
  }
  return { ok: false, error: result.error?.message ?? result.outcome };
}
