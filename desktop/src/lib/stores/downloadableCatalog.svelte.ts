/**
 * Reactive store for books available for download from other devices.
 *
 * Populated by SyncService.syncBookCatalog() on app start and periodic sync.
 * Consumed by the library UI to show "Available from other devices" section.
 *
 * Provides download orchestration with Drive auth check, retry with backoff,
 * and error state management.
 */
import type { SupabaseUserBookRow } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { SupabaseBookCatalogSync } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { GDriveProvider } from '$lib/shared/services/storage/GDriveProvider';
import { getDriveToken } from '$lib/shared/services/SupabaseAuthService';
import { importRecoveredBook } from '$lib/shared/recovery/desktopRecoveryImport';
import * as tauri from '$lib/shared/api/tauriClient';

// ─── Reactive State ───────────────────────────────────────────────────

let downloadableBooks: SupabaseUserBookRow[] = $state([]);
let isDownloadingSet: Set<string> = $state(new Set());
let downloadErrorMsg: string | null = $state(null);

// ─── Public Setters ───────────────────────────────────────────────────

export function setDownloadableBooks(books: SupabaseUserBookRow[]): void {
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

// ─── Download Orchestration (Task 4.3 + 4.6) ─────────────────────────

/**
 * Download a remote book from another device.
 *
 * Flow: auth check → resolve stable remote ref (fileId first, canonical
 * name fallback) → download temp bytes → verify SHA-256 BEFORE persistence →
 * atomic persist (Rust saveBookFile creates row + temp/rename) → mark the
 * remote row imported (version bump) → remove from downloadable on success.
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
    const driveToken = await getDriveToken();
    if (!driveToken) {
      throw new Error(
        'Google Drive authentication required. Please sign in with Google again.',
      );
    }

    const gdrive = new GDriveProvider();
    const catalogSync = new SupabaseBookCatalogSync(book.userId);
    const result = await importRecoveredBook(book, {
      download: (ref) => gdrive.download(ref),
      persist: (id, bytes, meta) =>
        tauri.saveBookFile(id, Array.from(bytes), meta),
      markImported: (id, version) =>
        catalogSync.upsertBook({
          ...book, id, lifecycle: 'imported',
          catalogVersion: version, updatedAt: new Date().toISOString(),
        }),
      findByHash: (hash) => catalogSync.findByHash(hash),
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
  get books(): SupabaseUserBookRow[] {
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
  setDownloadError,
  clearDownloadError,
};
