/**
 * SyncService — syncs local SQLite state with Google Drive.
 * Replaces Supabase table sync with Drive JSON state files (GoogleDriveStateSync)
 * and Drive file sync (GDriveProvider). LWW conflict resolution via ConflictResolver.
 */
import { authState } from '$lib/stores/authState.svelte';
import { GDriveProvider } from './storage/GDriveProvider';
import { GoogleDriveStateSync } from './GoogleDriveStateSync';
import type {
  ProgressStateJson,
  HighlightStateJson,
  BookmarkStateJson,
} from './GoogleDriveStateSync';
import * as tauri from '$lib/shared/api/tauriClient';

export class SyncService {
  private static gdrive = new GDriveProvider();

  static async syncMetadata(): Promise<void> {
    if (!authState.isSignedIn) return;

    await Promise.all([this.syncBooks(), this.syncState()]);
  }

  /**
   * Sync book files with Drive — download missing files, upload local-only files.
   * Book metadata (title, author) stays local-only (no table sync).
   */
  private static async syncBooks(): Promise<void> {
    // 1. List remote book files from Drive
    const remoteFiles = await this.gdrive.list('');
    const remoteBookFiles = remoteFiles.filter((f) => !f.endsWith('_state.json'));

    // 2. Get local books from SQLite
    const localBooks = await tauri.listBooks();

    // 3. Download book files that are on Drive but missing locally
    for (const remoteFile of remoteBookFiles) {
      const localBook = localBooks.find((b) => {
        const ext = b.format || 'epub';
        return remoteFile === `${b.id}.${ext}` || remoteFile.startsWith(b.id);
      });

      if (localBook) {
        const existsLocally = await tauri.fileExists(localBook.filePath);
        if (!existsLocally) {
          try {
            console.log(`Syncing missing file for book: ${localBook.id}`);
            const fileData = await this.gdrive.download(remoteFile);
            await tauri.saveBookFile(localBook.id, Array.from(fileData));
          } catch (e) {
            console.error(`Failed to sync book file for ${localBook.id}:`, e);
          }
        }
      }
    }

    // 4. Upload local-only books to Drive (file only, not metadata)
    const remoteFileIds = new Set(remoteBookFiles);
    for (const localBook of localBooks) {
      const ext = localBook.format || 'epub';
      const expectedName = `${localBook.id}.${ext}`;
      if (!remoteFileIds.has(expectedName)) {
        try {
          const existsLocally = await tauri.fileExists(localBook.filePath);
          if (existsLocally) {
            const fileBytes = await tauri.getFileBytes(localBook.filePath);
            await this.gdrive.upload(expectedName, new Uint8Array(fileBytes), expectedName);
          }
        } catch (e) {
          console.error(`Failed to upload book file for ${localBook.id}:`, e);
        }
      }
    }
  }

  /**
   * Sync reading state (progress, highlights, bookmarks) via state.json in Drive.
   * Uses GoogleDriveStateSync for push/pull with LWW conflict resolution.
   */
  private static async syncState(): Promise<void> {
    const localBooks = await tauri.listBooks();

    for (const book of localBooks) {
      try {
        // ---- Gather local state ----
        const localProgressDto = await tauri.getProgress(book.id);
        const localHighlightsDto = await tauri.listHighlights(book.id);
        const localBookmarksDto = await tauri.listBookmarks(book.id);

        // Map to state JSON types
        const localProgress: ProgressStateJson | null = localProgressDto
          ? {
              id: localProgressDto.id,
              book_id: localProgressDto.bookId,
              cfi_location: localProgressDto.cfiLocation,
              percentage: localProgressDto.percentage,
              updated_at: new Date(localProgressDto.updatedAt).getTime(),
            }
          : null;

        const localHighlights: HighlightStateJson[] = localHighlightsDto.map((h) => ({
          id: h.id,
          book_id: h.bookId,
          cfi_range: '', // HighlightDto doesn't have CFI — filled from state sync
          text_content: h.text,
          note: h.note ?? null,
          color: h.color,
          updated_at: new Date(h.createdAt).getTime(),
          deleted_at: null,
          recordId: h.id,
          updatedAtEpochMillis: new Date(h.createdAt).getTime(),
          deletedAtEpochMillis: null,
        }));

        const localBookmarks: BookmarkStateJson[] = localBookmarksDto.map((b) => ({
          id: b.id,
          book_id: b.bookId,
          cfi_location: '', // BookmarkDto doesn't have CFI
          title_or_snippet: b.title ?? '',
          updated_at: new Date(b.createdAt).getTime(),
          deleted_at: null,
          recordId: b.id,
          updatedAtEpochMillis: new Date(b.createdAt).getTime(),
          deletedAtEpochMillis: null,
        }));

        // ---- Push local state to Drive ----
        await GoogleDriveStateSync.pushState(
          book.id,
          localProgress,
          localHighlights,
          localBookmarks,
        );

        // ---- Pull remote state and merge ----
        const remote = await GoogleDriveStateSync.pullState(
          book.id,
          localProgress,
          localHighlights,
          localBookmarks,
        );

        // ---- Apply resolved state to local SQLite ----
        if (remote.progress) {
          await tauri.upsertProgress({
            id: remote.progress.id,
            bookId: remote.progress.book_id,
            cfiLocation: remote.progress.cfi_location,
            percentage: remote.progress.percentage,
            updatedAt: new Date(remote.progress.updated_at).toISOString(),
          });
        }

        // Apply highlights: only those that differ from local
        for (const h of remote.highlights) {
          const localMatch = localHighlights.find((lh) => lh.id === h.id);
          if (!localMatch || h.updatedAtEpochMillis > localMatch.updatedAtEpochMillis) {
            // Check if soft-deleted
            if (h.deletedAtEpochMillis !== null) {
              try {
                await tauri.deleteHighlight(h.id);
              } catch {
                // Highlight may not exist locally — ignore
              }
            } else {
              await tauri.saveHighlight({
                id: h.id,
                bookId: h.book_id,
                text: h.text_content,
                color: h.color,
                pageNumber: 0, // CFI-based highlights don't have page numbers
                rectLeft: 0,
                rectRight: 0,
                rectTop: 0,
                rectBottom: 0,
                cfi: h.cfi_range || null,
                note: h.note,
              });
            }
          }
        }

        // Apply bookmarks: only those that differ from local
        for (const b of remote.bookmarks) {
          const localMatch = localBookmarks.find((lb) => lb.id === b.id);
          if (!localMatch || b.updatedAtEpochMillis > localMatch.updatedAtEpochMillis) {
            if (b.deletedAtEpochMillis !== null) {
              try {
                await tauri.deleteBookmark(b.id);
              } catch {
                // Bookmark may not exist locally — ignore
              }
            } else {
              await tauri.saveBookmark({
                id: b.id,
                bookId: b.book_id,
                pageNumber: 0, // CFI-based bookmarks
                title: b.title_or_snippet || undefined,
                createdAt: new Date(b.updated_at).toISOString(),
              });
            }
          }
        }
      } catch (e) {
        console.error(`Failed to sync state for book ${book.id}:`, e);
      }
    }
  }
}
