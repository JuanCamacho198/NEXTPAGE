import { readFile } from '@tauri-apps/plugin-fs';
import {
  getProgress,
  saveProgress,
  saveReadingSession,
  updateBookProgress,
  upsertProgress as upsertProgressCmd,
  saveBookmark,
  deleteBookmark,
  saveHighlight,
  deleteHighlight,
} from '$lib/shared/api/tauriClient';
import type { ReaderBook, ReadingSessionInput, ReadingProgressDto, SaveProgressInput } from '$lib/shared/types';
import { authState } from '$lib/stores/authState.svelte';
import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
import { SupabaseProgressSync } from '$lib/shared/sync/SupabaseProgressSync';

const outboxDao = new SyncOutboxDao();

class ReaderDomainState {
  // ─── State ───
  activeReadingBookId = $state<string | null>(null);
  cfiLocation = $state('');
  percentage = $state(0);
  preloadedBytes = $state<{ filePath: string; data: Uint8Array } | null>(null);
  readerError = $state<string | null>(null);
  isFullscreen = $state(false);

  // ─── Callbacks ───
  onStatsRefreshNeeded: ((bookId: string) => Promise<void>) | null = null;
  onPageChangeCallback: ((bookId: string, page: number, total: number) => void) | null = null;

  // ─── Validation ───

  isValidSessionProgressEvent(event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }): boolean {
    if (!event.endedAt || event.durationSeconds <= 0) return false;

    const startedAt = Date.parse(event.startedAt);
    const endedAt = Date.parse(event.endedAt);
    if (!Number.isFinite(startedAt) || !Number.isFinite(endedAt) || endedAt <= startedAt) {
      return false;
    }

    const percentages = [event.startPercentage, event.endPercentage].filter(
      (value): value is number => typeof value === 'number',
    );

    return percentages.every((value) => value >= 0 && value <= 100);
  }

  // ─── Reading lifecycle ───

  async startReading(book: ReaderBook): Promise<void> {
    this.activeReadingBookId = book.id;
    this.preloadedBytes = null;

    const format = book.format.toLowerCase();

    // Start preloading file data
    if (format === 'epub' || format === 'pdf') {
      readFile(book.filePath)
        .then((bytes) => {
          this.preloadedBytes = { filePath: book.filePath, data: bytes };
        })
        .catch(() => {
          // Preload failed silently
        });
    }

    if (format === 'pdf') {
      // Kick off PDF streaming cache
      import('$lib/features/reader/viewer-pdf/pdfStreaming').then(({ createPdfDocument }) => {
        void createPdfDocument(book.filePath).catch(() => {});
      });
    }

    if (format === 'epub') {
      try {
        const progress = await getProgress(book.id);
        this.cfiLocation = progress?.cfiLocation ?? '';
        this.percentage = progress?.percentage ?? 0;
      } catch {
        this.cfiLocation = '';
        this.percentage = 0;
      }
    }
  }

  // ─── Progress ───

  async handleEpubLocationChange(
    bookId: string,
    nextLocation: string,
    nextPercentage: number,
  ): Promise<void> {
    this.cfiLocation = nextLocation;
    this.percentage = Math.max(0, Math.min(100, nextPercentage));

    const payload: SaveProgressInput = {
      bookId,
      cfiLocation: nextLocation,
      percentage: this.percentage,
    };

    try {
      await saveProgress(payload);

      // If signed in, queue Supabase sync via outbox
      if (authState.userId) {
        const outboxPayload = {
          userId: authState.userId,
          bookId,
          cfiLocation: nextLocation,
          percentage: this.percentage,
          updatedAt: new Date().toISOString(),
        };
        void outboxDao.add('READING_PROGRESS', bookId, 'UPSERT', JSON.stringify(outboxPayload));
      }
    } catch {
      // Keep UI usable even when save fails
    }

    void this.onStatsRefreshNeeded?.(bookId);
  }

  async handlePdfPageChange(bookId: string, page: number, total: number): Promise<void> {
    this.onPageChangeCallback?.(bookId, page, total);

    try {
      await updateBookProgress(bookId, page);
    } catch {
      // Keep reader responsive
    }

    void this.onStatsRefreshNeeded?.(bookId);
  }

  async handlePdfSessionProgress(
    bookId: string,
    event: {
      startedAt: string;
      endedAt?: string;
      durationSeconds: number;
      startPercentage?: number;
      endPercentage?: number;
    },
  ): Promise<void> {
    if (!this.isValidSessionProgressEvent(event)) return;

    const payload: ReadingSessionInput = {
      bookId,
      startedAt: event.startedAt,
      endedAt: event.endedAt,
      durationSeconds: event.durationSeconds,
      startPercentage: event.startPercentage,
      endPercentage: event.endPercentage,
    };

    try {
      await saveReadingSession(payload);
      void this.onStatsRefreshNeeded?.(bookId);
    } catch {
      // Non-blocking
    }
  }

  handleReaderLocationContext(): void {
    // Reserved for index_book_text integration
  }

  // ─── Realtime subscription (cross-device sync) ───

  private supabaseSync: SupabaseProgressSync | null = null;
  private unsubscribeRemote: (() => void) | null = null;
  private unsubscribeRemoteBookmarks: (() => void) | null = null;
  private unsubscribeRemoteHighlights: (() => void) | null = null;

  /**
   * Start listening for reading_progress changes from Supabase Realtime.
   * When remote progress arrives, upsert into local SQLite.
   * If the user is currently reading the same book, update in-memory state.
   */
  subscribeToRemoteProgress(): void {
    // Already subscribed
    if (this.unsubscribeRemote) return;
    // Need authenticated user
    if (!authState.userId) return;

    try {
      this.supabaseSync = new SupabaseProgressSync(authState.userId);

      this.unsubscribeRemote = this.supabaseSync.subscribeToProgress((payload) => {
        const { bookId, cfiLocation, percentage, updatedAt } = payload;

        // Upsert the remote progress into local SQLite
        const progressInput: ReadingProgressDto = {
          id: payload.id ?? crypto.randomUUID(),
          bookId: bookId,
          cfiLocation: cfiLocation,
          percentage,
          updatedAt: updatedAt,
        };
        upsertProgressCmd(progressInput).catch((e) => {
          console.error('Failed to apply remote progress locally:', e);
        });

        // If the user is currently reading this book, update in-memory state
        if (this.activeReadingBookId === bookId) {
          // Only apply if remote is newer — trust the upsert
          this.cfiLocation = cfiLocation;
          this.percentage = percentage;
        }
      });
    } catch (e) {
      console.error('Failed to subscribe to remote progress:', e);
    }
  }

  /**
   * Subscribe to Realtime changes for bookmarks.
   * When remote bookmark changes arrive, upsert/delete into local SQLite.
   */
  subscribeToRemoteBookmarks(): void {
    if (this.unsubscribeRemoteBookmarks) return;
    if (!authState.userId) return;

    try {
      if (!this.supabaseSync) {
        this.supabaseSync = new SupabaseProgressSync(authState.userId);
      }

      this.unsubscribeRemoteBookmarks = this.supabaseSync.subscribeToBookmarks((payload) => {
        const { id, bookId, titleSnippet, deletedAt, updatedAt } = payload;

        if (deletedAt) {
          // Soft-delete: propagate tombstone
          deleteBookmark(id ?? '').catch((e) => {
            console.error('Failed to apply remote bookmark delete locally:', e);
          });
        } else {
          saveBookmark({
            id: id ?? crypto.randomUUID(),
            bookId: bookId,
            pageNumber: 0,
            title: titleSnippet ?? undefined,
            createdAt: updatedAt,
          }).catch((e) => {
            console.error('Failed to apply remote bookmark locally:', e);
          });
        }
      });
    } catch (e) {
      console.error('Failed to subscribe to remote bookmarks:', e);
    }
  }

  /**
   * Subscribe to Realtime changes for highlights.
   * When remote highlight changes arrive, upsert/delete into local SQLite.
   */
  subscribeToRemoteHighlights(): void {
    if (this.unsubscribeRemoteHighlights) return;
    if (!authState.userId) return;

    try {
      if (!this.supabaseSync) {
        this.supabaseSync = new SupabaseProgressSync(authState.userId);
      }

      this.unsubscribeRemoteHighlights = this.supabaseSync.subscribeToHighlights((payload) => {
        const { id, bookId, cfiRange, textContent, note, color, deletedAt } = payload;

        if (deletedAt) {
          deleteHighlight(id ?? '').catch((e) => {
            console.error('Failed to apply remote highlight delete locally:', e);
          });
        } else {
          saveHighlight({
            id: id ?? crypto.randomUUID(),
            bookId: bookId,
            text: textContent,
            color: color,
            pageNumber: 0,
            rectLeft: 0,
            rectRight: 0,
            rectTop: 0,
            rectBottom: 0,
            cfi: cfiRange || null,
            note: note,
          }).catch((e) => {
            console.error('Failed to apply remote highlight locally:', e);
          });
        }
      });
    } catch (e) {
      console.error('Failed to subscribe to remote highlights:', e);
    }
  }

  /**
   * Stop the Realtime subscription for progress. Call on logout or dispose.
   */
  unsubscribeFromRemoteProgress(): void {
    this.unsubscribeRemote?.();
    this.unsubscribeRemote = null;
    this.supabaseSync = null;
  }

  /**
   * Stop the Realtime subscription for bookmarks.
   */
  unsubscribeFromRemoteBookmarks(): void {
    this.unsubscribeRemoteBookmarks?.();
    this.unsubscribeRemoteBookmarks = null;
  }

  /**
   * Stop the Realtime subscription for highlights.
   */
  unsubscribeFromRemoteHighlights(): void {
    this.unsubscribeRemoteHighlights?.();
    this.unsubscribeRemoteHighlights = null;
  }

  /**
   * Re-subscribe all — useful when userId changes (login/logout cycle).
   */
  refreshRemoteProgressSubscription(): void {
    this.unsubscribeFromRemoteProgress();
    this.subscribeToRemoteProgress();
  }

  /**
   * Re-subscribe all Realtime channels — call on login.
   */
  subscribeToAllRemoteChanges(): void {
    this.subscribeToRemoteProgress();
    this.subscribeToRemoteBookmarks();
    this.subscribeToRemoteHighlights();
  }

  /**
   * Unsubscribe from all Realtime channels — call on logout.
   */
  unsubscribeFromAllRemoteChanges(): void {
    this.unsubscribeFromRemoteProgress();
    this.unsubscribeFromRemoteBookmarks();
    this.unsubscribeFromRemoteHighlights();
  }

  // ─── Reset ───

  resetReader(): void {
    this.activeReadingBookId = null;
    this.cfiLocation = '';
    this.percentage = 0;
    this.preloadedBytes = null;
    this.readerError = null;
  }
}

export const readerState = new ReaderDomainState();
export { ReaderDomainState };
