/**
 * Reactive store for books available for download from other devices.
 *
 * The shelf "Available from other devices" section sources its list directly
 * from Drive `NextPage/Books/` (REQ-01) via `loadAvailableFromDrive()`, filtered
 * against the local library — instead of the Supabase user_books catalog.
 * Populated on shelf mount by the screen; downloaded files land in the local
 * library and are removed from the section on success.
 *
 * Provides download orchestration with Drive auth fallback (GDriveProvider
 * refreshes the token when absent), retry with backoff, and error state
 * management.
 */
import type { SupabaseUserBookRow } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { SupabaseBookCatalogSync } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { GDriveProvider } from '$lib/shared/services/storage/GDriveProvider';
import { parseCanonicalBookName } from '$lib/shared/protocol/DriveCatalogContract';
import { reportAuthError } from '$lib/shared/stores/syncAlert.svelte';
import { authState } from '$lib/stores/authState.svelte';
import { importRecoveredBook } from '$lib/shared/recovery/desktopRecoveryImport';
import * as tauri from '$lib/shared/api/tauriClient';

// ─── Types ─────────────────────────────────────────────────────────────

/** A book listed from Drive `NextPage/Books/` and absent from the local library. */
export interface AvailableDriveBook {
  id: string;
  ext: string;
  remoteName: string;
  displayTitle: string;
}

// ─── Reactive State ───────────────────────────────────────────────────

let downloadableBooks: AvailableDriveBook[] = $state([]);
let isDownloadingSet: Set<string> = $state(new Set());
let downloadErrorMsg: string | null = $state(null);

// ─── Public Setters ───────────────────────────────────────────────────

export function setDownloadableBooks(books: AvailableDriveBook[]): void {
  downloadableBooks = books;
}

export function clearDownloadableBooks(): void {
  downloadableBooks = [];
}

export function removeDownloadableBook(bookId: string): void {
  downloadableBooks = downloadableBooks.filter((b) => b.id !== bookId);
}

export function setDownloadError(msg: string | null): void {
  downloadErrorMsg = msg;
}

export function clearDownloadError(): void {
  downloadErrorMsg = null;
}

// ─── Drive Listing (REQ-01) ───────────────────────────────────────────

/** Module-level in-flight guard: concurrent mounts/refreshes share one listing. */
let availableLoadPromise: Promise<void> | null = null;

/**
 * List `NextPage/Books/` via Drive and expose the books absent from the local
 * library (SCN-01/SCN-02). Unparseable filenames (`_state.json`, no dot,
 * trailing dot) are dropped; `displayTitle` is the filename minus its
 * extension (no catalog lookup — spec-optional). Auth-class failures surface
 * on the global banner via `reportAuthError` and in `error` for the inline
 * banner. Never clears a previously loaded list on failure.
 */
export async function loadAvailableFromDrive(): Promise<void> {
  if (availableLoadPromise) return availableLoadPromise;

  const promise = (async () => {
    try {
      const gdrive = new GDriveProvider();
      const [remoteNames, localBooks] = await Promise.all([
        gdrive.list(''),
        tauri.listLibraryBooks(),
      ]);
      const localIds = new Set(localBooks.map((b) => b.id));
      const available: AvailableDriveBook[] = [];
      for (const name of remoteNames) {
        const parsed = parseCanonicalBookName(name);
        if (!parsed) continue; // sync-state / malformed names are not books
        if (localIds.has(parsed.bookId)) continue; // already local (SCN-02)
        available.push({
          id: parsed.bookId,
          ext: parsed.ext,
          remoteName: name,
          displayTitle: name.slice(0, name.lastIndexOf('.')),
        });
      }
      downloadableBooks = available;
    } catch (e) {
      reportAuthError(e);
      downloadErrorMsg = e instanceof Error ? e.message : 'Failed to load books from Drive';
    }
  })().finally(() => {
    if (availableLoadPromise === promise) availableLoadPromise = null;
  });

  availableLoadPromise = promise;
  return promise;
}

// ─── Download Orchestration (REQ-02) ─────────────────────────────────

/**
 * Download a Drive book from the shelf section.
 *
 * Flow: download temp bytes via GDriveProvider (it resolves the token with a
 * refresh fallback — no manual `getDriveToken` check) → atomic persist (Rust
 * saveBookFile creates row + temp/rename) → mark the catalog row imported
 * (version bump) only when a live user session exists → remove from
 * downloadable on success.
 */
export async function downloadBook(bookId: string): Promise<void> {
  if (isDownloadingSet.has(bookId)) return;

  const book = downloadableBooks.find((b) => b.id === bookId);
  if (!book) {
    downloadErrorMsg = 'Book not found in downloadable list';
    return;
  }

  isDownloadingSet.add(bookId);
  downloadErrorMsg = null;

  try {
    const gdrive = new GDriveProvider();
    // Synthetic catalog row so the verified import pipeline can be reused;
    // the resolved remote ref is ignored by the download closure below, which
    // uses the ORIGINAL Drive filename (safe for non-canonical names).
    const row: SupabaseUserBookRow = {
      id: book.id,
      userId: authState.userId ?? '',
      title: book.displayTitle,
      author: null,
      format: book.ext,
      filePath: null,
      coverUrl: null,
      description: null,
      totalPages: null,
      sourceDevice: null,
      importedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      lifecycle: 'available',
      catalogVersion: 1,
      remoteName: book.remoteName,
    };
    const catalogSync = new SupabaseBookCatalogSync(row.userId);
    const result = await importRecoveredBook(row, {
      // book.remoteName is a non-null string (AvailableDriveBook) — the
      // synthetic row's optional remoteName would widen to `string | null`.
      download: () => gdrive.download(book.remoteName),
      persist: (id, bytes, meta) => tauri.saveBookFile(id, Array.from(bytes), meta),
      // Catalog upsert only when a live user session exists (no auth → no-op).
      markImported: row.userId
        ? (id, version) =>
            catalogSync.upsertBook({
              ...row, id, lifecycle: 'imported',
              catalogVersion: version, updatedAt: new Date().toISOString(),
            })
        : async () => {},
      findByHash: row.userId ? (hash) => catalogSync.findByHash(hash) : undefined,
    });

    if (result.outcome === 'imported' || result.outcome === 'already_imported') {
      removeDownloadableBook(bookId);
    } else if (result.error) {
      downloadErrorMsg = result.error.message;
    } else {
      downloadErrorMsg = 'Download failed unexpectedly';
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : 'Download failed unexpectedly';
    downloadErrorMsg = msg;
    // Keep book in downloadable list for later retry (per spec S5)
    throw e;
  } finally {
    isDownloadingSet.delete(bookId);
  }
}

// ─── Public API ───────────────────────────────────────────────────────

export const downloadableCatalog = {
  get books(): AvailableDriveBook[] {
    return downloadableBooks;
  },
  get count(): number {
    return downloadableBooks.length;
  },
  get isDownloading(): Set<string> {
    return isDownloadingSet;
  },
  get error(): string | null {
    return downloadErrorMsg;
  },
  setDownloadableBooks,
  clearDownloadableBooks,
  removeDownloadableBook,
  downloadBook,
  loadAvailableFromDrive,
  setDownloadError,
  clearDownloadError,
};
