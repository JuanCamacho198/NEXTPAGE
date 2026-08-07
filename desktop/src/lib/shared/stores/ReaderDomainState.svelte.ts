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
  setReadingStatus,
} from '$lib/shared/api/tauriClient';
import type {
  ReaderBook,
  ReadingSessionInput,
  ReadingProgressDto,
  SaveProgressInput,
} from '$lib/shared/types';
import { authState } from '$lib/stores/authState.svelte';
import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
import { SupabaseProgressSync } from '$lib/shared/sync/SupabaseProgressSync';
import type { SupabaseProgressRow } from '$lib/shared/sync/SupabaseProgressSync';

const outboxDao = new SyncOutboxDao();

class ReaderDomainState {
  // ─── State ───
  activeReadingBookId = $state<string | null>(null);
  cfiLocation = $state('');
  percentage = $state(0);
  locatorJson = $state<string | null>(null);
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
    const epoch = ++this.openEpoch;
    this.activeReadingBookId = book.id;
    this.preloadedBytes = null;

    // Persist the lifecycle transition before reader data becomes available.
    // This keeps a zero-progress start visible in Continue Reading.
    await setReadingStatus(book.id, 'reading').catch(() => {});

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
        if (epoch !== this.openEpoch) return;
        this.cfiLocation = progress?.cfiLocation ?? '';
        this.percentage = progress?.percentage ?? 0;
      } catch {
        if (epoch !== this.openEpoch) return;
        this.cfiLocation = '';
        this.percentage = 0;
      }
      if (authState.userId) {
        const sync = this.supabaseSync ?? new SupabaseProgressSync(authState.userId);
        this.supabaseSync = sync;
        const localUpdatedAt = await getProgress(book.id)
          .then((value) => value?.updatedAt ?? null)
          .catch(() => null);
        void this.fetchAndApplyBookState(sync, book.id, epoch, localUpdatedAt);
      }
    }
  }

  private async fetchAndApplyBookState(
    sync: SupabaseProgressSync,
    bookId: string,
    epoch: number,
    localUpdatedAt: string | null,
  ): Promise<void> {
    try {
      const remote = await sync.fetchBookState(bookId);
      if (epoch !== this.openEpoch || this.activeReadingBookId !== bookId) return;
      if (
        remote.progress &&
        (!localUpdatedAt || Date.parse(remote.progress.updatedAt) > Date.parse(localUpdatedAt))
      ) {
        this.applyRemoteProgress(remote.progress);
      }
      for (const bookmark of remote.bookmarks) {
        this.appliedRemote.set(
          `bookmark:${bookmark.id ?? bookmark.cfiLocation}`,
          bookmark.updatedAt,
        );
        if (bookmark.deletedAt && bookmark.id) {
          void deleteBookmark(bookmark.id);
        } else {
          void saveBookmark({
            id: bookmark.id ?? crypto.randomUUID(),
            bookId: bookmark.bookId,
            pageNumber: 0,
            title: bookmark.titleSnippet ?? undefined,
            createdAt: bookmark.updatedAt,
          });
        }
      }
      for (const highlight of remote.highlights) {
        this.appliedRemote.set(
          `highlight:${highlight.id ?? highlight.cfiRange}`,
          highlight.updatedAt,
        );
        if (highlight.deletedAt && highlight.id) {
          void deleteHighlight(highlight.id);
        } else {
          void saveHighlight({
            id: highlight.id ?? crypto.randomUUID(),
            bookId: highlight.bookId,
            text: highlight.textContent,
            color: highlight.color,
            pageNumber: highlight.page ?? 0,
            rectLeft: 0,
            rectRight: 0,
            rectTop: 0,
            rectBottom: 0,
            cfi: highlight.cfiRange || null,
            note: highlight.note,
          });
        }
      }
    } catch {
      // Offline-first: local state and the existing outbox remain usable.
    }
  }

  private applyRemoteProgress(progress: SupabaseProgressRow): void {
    this.cfiLocation = progress.cfiLocation;
    this.percentage = progress.percentage;
    this.locatorJson = progress.locatorJson ?? null;
    this.appliedRemote.set(`progress:${progress.bookId}`, progress.updatedAt);
    void upsertProgressCmd({
      id: progress.id ?? crypto.randomUUID(),
      bookId: progress.bookId,
      cfiLocation: progress.cfiLocation,
      percentage: progress.percentage,
      updatedAt: progress.updatedAt,
    });
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
      await setReadingStatus(bookId, this.percentage >= 100 ? 'completed' : 'reading');

      // If signed in, queue Supabase sync via outbox. Coalesced enqueue (D5,
      // SR-4.1): one IPC per location change, transactional UPDATE-else-INSERT
      // per (user_id, book_id) with the latest client updatedAt winning (D6) —
      // the per-location-change flood collapses to a single outbox row.
      if (authState.userId) {
        const outboxPayload = {
          userId: authState.userId,
          bookId,
          cfiLocation: nextLocation,
          percentage: this.percentage,
          locatorJson: this.locatorJson,
          updatedAt: new Date().toISOString(),
        };
        void outboxDao.addCoalesced(
          'READING_PROGRESS',
          bookId,
          'UPSERT',
          JSON.stringify(outboxPayload),
        );
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

  handleReaderLocationContext(ctx?: unknown): void {
    if (!ctx || typeof ctx !== 'object' || !('locator' in ctx)) return;
    const locator = (ctx as { locator?: unknown }).locator;
    this.locatorJson = typeof locator === 'string' ? locator : null;
  }

  // ─── Realtime subscription (cross-device sync) ───

  private supabaseSync: SupabaseProgressSync | null = null;
  private unsubscribeRemote: (() => void) | null = null;
  private unsubscribeRemoteBookmarks: (() => void) | null = null;
  private unsubscribeRemoteHighlights: (() => void) | null = null;
  private openEpoch = 0;
  private appliedRemote = new Map<string, string>();

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
        const key = `progress:${bookId}`;
        const previous = this.appliedRemote.get(key);
        if (previous && Date.parse(updatedAt) <= Date.parse(previous)) return;
        this.appliedRemote.set(key, updatedAt);

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
          this.applyRemoteProgress(payload);
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
        const key = `bookmark:${id ?? payload.cfiLocation}`;
        const previous = this.appliedRemote.get(key);
        if (previous && Date.parse(updatedAt) <= Date.parse(previous)) return;
        this.appliedRemote.set(key, updatedAt);

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
        const key = `highlight:${id ?? cfiRange}`;
        const previous = this.appliedRemote.get(key);
        if (previous && Date.parse(payload.updatedAt) <= Date.parse(previous)) return;
        this.appliedRemote.set(key, payload.updatedAt);

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
    this.openEpoch += 1;
    this.activeReadingBookId = null;
    this.cfiLocation = '';
    this.percentage = 0;
    this.preloadedBytes = null;
    this.readerError = null;
  }
}

export const readerState = new ReaderDomainState();
export { ReaderDomainState };
