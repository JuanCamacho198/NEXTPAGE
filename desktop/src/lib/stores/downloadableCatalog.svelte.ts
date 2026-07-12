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
import { GDriveProvider } from '$lib/shared/services/storage/GDriveProvider';
import { getDriveToken } from '$lib/shared/services/SupabaseAuthService';
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
 * Flow:
 * 1. Check Drive auth state
 * 2. Download file from Drive via GDriveProvider (with retry + backoff)
 * 3. Save locally via tauri.saveBookFile()
 * 4. Upsert local BookEntity via tauri.upsertBook()
 * 5. Remove from downloadable list on success
 * 6. On retry exhaustion: set error state, keep book in list for later retry
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
    // 1. Check Drive auth
    const driveToken = await getDriveToken();
    if (!driveToken) {
      throw new Error(
        'Google Drive authentication required. Please sign in with Google again.',
      );
    }

    // 2. Download with retry (up to 3 attempts, exponential backoff)
    const gdrive = new GDriveProvider();
    const fileName = `${book.id}.${book.format}`;
    let bytes: Uint8Array;
    let lastError: Error | null = null;

    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        if (attempt > 0) {
          // Exponential backoff: 1s, 2s
          await new Promise((r) => setTimeout(r, 1000 * Math.pow(2, attempt - 1)));
        }
        bytes = await gdrive.download(fileName);
        lastError = null;
        break;
      } catch (e) {
        lastError = e instanceof Error ? e : new Error(String(e));

        // If 401/Unauthorized, try triggering a session refresh
        if (
          lastError.message.includes('401') ||
          lastError.message.includes('Unauthorized')
        ) {
          const refreshed = await refreshDriveToken();
          if (!refreshed) {
            throw new Error(
              'Your Google Drive session has expired. Please sign in with Google again to download books from other devices.',
            );
          }
          // Token refreshed, retry will use new token
        }
      }
    }

    if (lastError !== null) {
      throw lastError;
    }

    // 3. Save locally
    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
    await tauri.saveBookFile(bookId, Array.from(bytes!));

    // 4. Upsert BookEntity
    await tauri.upsertBook({
      id: bookId,
      title: book.title,
      author: book.author ?? '',
      format: book.format,
    });

    // 5. Remove from downloadable list
    removeDownloadableBook(bookId);
  } catch (e) {
    const msg = e instanceof Error ? e.message : 'Download failed unexpectedly';
    downloadErrorMsg = msg;
    // Keep book in downloadable list for later retry (per spec S5)
    throw e;
  } finally {
    isDownloadingSet.delete(bookId);
  }
}

/**
 * Attempt to refresh the Supabase session, which may yield a new
 * provider_token for Drive access.
 */
async function refreshDriveToken(): Promise<boolean> {
  try {
    const { getSessionClient } = await import('$lib/services/supabase');
    const supabase = getSessionClient();
    const { data, error } = await supabase.auth.refreshSession();
    if (error || !data.session?.provider_token) return false;
    // Update auth state with refreshed session
    const { authState } = await import('$lib/stores/authState.svelte');
    authState.setSupabaseSession({
      accessToken: data.session.access_token,
      refreshToken: data.session.refresh_token,
      expiresAt: data.session.expires_at ? data.session.expires_at * 1000 : null,
      userId: data.session.user.id,
      email: data.session.user.email ?? null,
      displayName: data.session.user.user_metadata?.full_name ?? null,
      photoUrl: data.session.user.user_metadata?.avatar_url ?? null,
      providerToken: data.session.provider_token ?? null,
    });
    return true;
  } catch {
    return false;
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
