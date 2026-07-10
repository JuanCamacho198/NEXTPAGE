/**
 * SyncService — syncs local SQLite state with Google Drive.
 * Replaces Supabase table sync with Drive JSON state files (GoogleDriveStateSync)
 * and Drive file sync (GDriveProvider). LWW conflict resolution via ConflictResolver.
 *
 * Dual-write mode: after Drive sync, also upserts progress to Supabase.
 * Cutover: when `readingProgressSync` is 'supabase', Drive writes are skipped.
 */
import { authState } from '$lib/stores/authState.svelte';
import { GDriveProvider } from './storage/GDriveProvider';
import { GoogleDriveStateSync } from './GoogleDriveStateSync';
import { SupabaseProgressSync } from '../sync/SupabaseProgressSync';
import type { ProgressStateJson, HighlightStateJson, BookmarkStateJson } from './GoogleDriveStateSync';
import type { TagDto } from '$lib/shared/types';
import * as tauri from '$lib/shared/api/tauriClient';

/** Sync mode for reading progress: 'drive' (legacy), 'dual' (both), 'supabase' (cutover). */
type ReadingProgressSyncMode = 'drive' | 'dual' | 'supabase';
type BookmarkSyncMode = 'drive' | 'dual' | 'supabase';
type HighlightSyncMode = 'drive' | 'dual' | 'supabase';

export class SyncService {
  private static gdrive = new GDriveProvider();

  /** Which mode to use for reading progress sync. Defaults to 'dual' during transition. */
  private static readingProgressSync: ReadingProgressSyncMode = 'dual';

  /** Which mode to use for bookmark sync. Independent of readingProgressSync. */
  private static bookmarkSync: BookmarkSyncMode = 'dual';

  /** Which mode to use for highlight sync (includes tags). Independent of readingProgressSync. */
  private static highlightSync: HighlightSyncMode = 'dual';

  /** Flag to track whether Drive → Supabase import was done this session. */
  private static supabaseImportDone = false;

  /**
   * Override the sync mode at runtime.
   * - 'drive': legacy Drive-only (no Supabase writes)
   * - 'dual': write to both Drive and Supabase (transition)
   * - 'supabase': Supabase-only (cutover complete)
   */
  static setReadingProgressSyncMode(mode: ReadingProgressSyncMode): void {
    this.readingProgressSync = mode;
  }

  static setBookmarkSyncMode(mode: BookmarkSyncMode): void {
    this.bookmarkSync = mode;
  }

  static setHighlightSyncMode(mode: HighlightSyncMode): void {
    this.highlightSync = mode;
  }

  /**
   * Whether any import is still pending (import not done and at least one domain in dual/supabase mode).
   */
  static get hasPendingImport(): boolean {
    if (this.supabaseImportDone) return false;
    return (
      this.readingProgressSync !== 'drive' ||
      this.bookmarkSync !== 'drive' ||
      this.highlightSync !== 'drive'
    );
  }

  static async syncMetadata(): Promise<void> {
    if (!authState.isSignedIn) return;

    await Promise.all([this.syncBooks(), this.syncState()]);
  }

  /**
   * On first sync after login, run the one-time import of Drive progress,
   * bookmarks, highlights, and tags to Supabase.
   */
  private static async ensureSupabaseImport(): Promise<void> {
    if (this.supabaseImportDone) return;
    if (this.readingProgressSync === 'drive' && this.bookmarkSync === 'drive' && this.highlightSync === 'drive') return;
    if (!authState.userId) return;

    try {
      const sync = new SupabaseProgressSync(authState.userId);
      const localBooks = await tauri.listBooks();

      // ── Import progress ──
      if (this.readingProgressSync !== 'drive') {
        const progressRows: Array<{
          userId: string;
          bookId: string;
          cfiLocation: string;
          percentage: number;
          updatedAt: string;
        }> = [];

        for (const book of localBooks) {
          const progress = await tauri.getProgress(book.id);
          if (progress) {
            progressRows.push({
              userId: authState.userId,
              bookId: progress.bookId,
              cfiLocation: progress.cfiLocation,
              percentage: progress.percentage,
              updatedAt: progress.updatedAt,
            });
          }
        }

        if (progressRows.length > 0) {
          await sync.importFromDrive(progressRows);
        }
      }

      // ── Import bookmarks ──
      if (this.bookmarkSync !== 'drive') {
        const bookmarkRows: Array<{
          userId: string;
          bookId: string;
          cfiLocation: string;
          titleSnippet: string | null;
          updatedAt: string;
        }> = [];

        for (const book of localBooks) {
          const bookmarks = await tauri.listBookmarks(book.id);
          for (const b of bookmarks) {
            bookmarkRows.push({
              userId: authState.userId,
              bookId: b.bookId,
              cfiLocation: '',
              titleSnippet: b.title ?? null,
              updatedAt: b.createdAt,
            });
          }
        }

        if (bookmarkRows.length > 0) {
          await sync.importBookmarksFromDrive(
            bookmarkRows.map((b) => ({
              ...b,
              id: crypto.randomUUID(),
              locatorJson: null,
              deletedAt: null,
            })),
          );
        }
      }

      // ── Import highlights + tags ──
      if (this.highlightSync !== 'drive') {
        const highlightRows: Array<{
          id: string;
          userId: string;
          bookId: string;
          cfiRange: string;
          textContent: string;
          note: string | null;
          color: string;
          rectJson: Record<string, number> | null;
          updatedAt: string;
        }> = [];

        for (const book of localBooks) {
          const highlights = await tauri.listHighlights(book.id);
          for (const h of highlights) {
            highlightRows.push({
              id: h.id,
              userId: authState.userId,
              bookId: h.bookId,
              cfiRange: h.cfi ?? '',
              textContent: h.text,
              note: h.note ?? null,
              color: h.color,
              rectJson: h.pageNumber
                ? { left: 0, right: 0, top: 0, bottom: 0 }
                : null,
              updatedAt: h.createdAt,
            });
          }

          // Import tags for each highlight
          for (const h of highlights) {
            const tags: TagDto[] = await tauri.listTagsForHighlight(h.id).catch(() => []);
            for (const tag of tags) {
              const tagRow = await sync.upsertTag({
                userId: authState.userId,
                name: tag.name,
                color: tag.color ?? null,
              });
              await sync.linkTagToHighlight(h.id, tagRow.id!);
            }
          }
        }

        if (highlightRows.length > 0) {
          await sync.importHighlightsFromDrive(
            highlightRows.map((h) => ({
              ...h,
              type: null,
              locatorJson: null,
              deletedAt: null,
            })),
          );
        }
      }

      this.supabaseImportDone = true;
    } catch (e) {
      console.error('Failed to import data to Supabase:', e);
      // Non-blocking — retry on next sync
    }
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
    // Import existing progress to Supabase on first sync (if in dual/supabase mode)
    await this.ensureSupabaseImport();

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

        // ---- Push local state to Drive (skip if cutover) ----
        if (this.readingProgressSync !== 'supabase') {
          await GoogleDriveStateSync.pushState(
            book.id,
            localProgress,
            localHighlights,
            localBookmarks,
          );
        }

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

        // ---- Dual-write: also upsert resolved progress to Supabase ----
        if (this.readingProgressSync !== 'drive' && authState.userId && remote.progress) {
          try {
            const sync = new SupabaseProgressSync(authState.userId);
            await sync.upsertProgress({
              userId: authState.userId,
              bookId: remote.progress.book_id,
              cfiLocation: remote.progress.cfi_location,
              percentage: remote.progress.percentage,
              updatedAt: new Date(remote.progress.updated_at).toISOString(),
            });
          } catch (e) {
            console.error(`Failed to sync progress to Supabase for book ${book.id}:`, e);
          }
        }
        // Also upsert local-only progress that wasn't in Drive
        else if (this.readingProgressSync !== 'drive' && authState.userId && localProgress && !remote.progress) {
          try {
            const sync = new SupabaseProgressSync(authState.userId);
            await sync.upsertProgress({
              userId: authState.userId,
              bookId: localProgress.book_id,
              cfiLocation: localProgress.cfi_location,
              percentage: localProgress.percentage,
              updatedAt: new Date(localProgress.updated_at).toISOString(),
            });
          } catch (e) {
            console.error(`Failed to sync local progress to Supabase for book ${book.id}:`, e);
          }
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
