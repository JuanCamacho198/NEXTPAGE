import { readFile } from '@tauri-apps/plugin-fs';
import {
  getProgress,
  saveProgress,
  saveReadingSession,
  updateBookProgress,
  upsertProgress as upsertProgressCmd,
  upsertRemoteReadingSessions as upsertRemoteReadingSessionsCmd,
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

/**
 * D6 (SCEN-duration-1): sessions shorter than this are dropped entirely —
 * not stored locally, not enqueued. Single tunable constant.
 */
const MIN_SESSION_DURATION_SECONDS = 30;

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
    console.warn('[continue] startReading book', book.id, 'epoch', epoch, 'format', format);

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
        if (epoch !== this.openEpoch) {
          console.warn('[continue] startReading local progress stale epoch', epoch, 'current', this.openEpoch);
          return;
        }
        console.warn(
          '[continue] startReading book',
          book.id,
          'local progress',
          progress?.cfiLocation?.slice(0, 60) ?? '(empty)',
          progress?.percentage ?? 0,
          'epoch',
          epoch,
        );
        this.cfiLocation = progress?.cfiLocation ?? '';
        this.percentage = progress?.percentage ?? 0;
      } catch {
        if (epoch !== this.openEpoch) {
          console.warn('[continue] startReading local progress error stale epoch', epoch);
          return;
        }
        console.warn('[continue] startReading book', book.id, 'local progress error, fallback to empty epoch', epoch);
        this.cfiLocation = '';
        this.percentage = 0;
      }
      if (authState.userId) {
        const sync = this.supabaseSync ?? new SupabaseProgressSync(authState.userId);
        this.supabaseSync = sync;
        const localUpdatedAt = await getProgress(book.id)
          .then((value) => value?.updatedAt ?? null)
          .catch(() => null);
        console.warn(
          '[continue] fetchAndApplyBookState queued book',
          book.id,
          'epoch',
          epoch,
          'localUpdatedAt',
          localUpdatedAt,
        );
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
      if (epoch !== this.openEpoch || this.activeReadingBookId !== bookId) {
        console.warn(
          '[continue] remote progress stale epoch',
          epoch,
          'current',
          this.openEpoch,
          'activeBook',
          this.activeReadingBookId,
          'bookId',
          bookId,
        );
        return;
      }
      console.warn(
        '[continue] remote progress fetched book',
        bookId,
        'remote',
        remote.progress?.cfiLocation?.slice(0, 60) ?? '(null)',
        remote.progress?.percentage ?? '(null)',
        'updatedAt',
        remote.progress?.updatedAt ?? '(null)',
        'localUpdatedAt',
        localUpdatedAt,
        'epoch',
        epoch,
      );
      if (
        remote.progress &&
        (!localUpdatedAt || Date.parse(remote.progress.updatedAt) > Date.parse(localUpdatedAt))
      ) {
        console.warn(
          '[continue] remote progress applied book',
          bookId,
          'cfi',
          remote.progress.cfiLocation.slice(0, 60),
          'pct',
          remote.progress.percentage,
          'epoch',
          epoch,
        );
        this.applyRemoteProgress(remote.progress);
      } else if (remote.progress) {
        console.warn('[continue] remote progress ignored (older than local)', bookId, 'epoch', epoch);
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
            // EPUB bookmarks are anchored by CFI (no real page); use 1 as the
            // minimum-valid placeholder instead of 0 (page 0 is not a real page).
            pageNumber: 1,
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
          // Supabase `page` is nullable and EPUB chapter indices are 0-based,
          // so a null/zero page must never crash the pull (the positive-page
          // validation in tauriClient would otherwise throw and abort every
          // subsequent highlight). Fall back to a safe value per format.
          const rawPage = highlight.page ?? 0;
          const pageNumber =
            Number.isInteger(rawPage) && rawPage > 0 ? rawPage : highlight.cfiRange ? 1 : 1;
          void saveHighlight({
            id: highlight.id ?? crypto.randomUUID(),
            bookId: highlight.bookId,
            text: highlight.textContent,
            color: highlight.color,
            pageNumber,
            rectLeft: 0,
            rectRight: 0,
            rectTop: 0,
            rectBottom: 0,
            cfi: highlight.cfiRange || null,
            note: highlight.note,
          }).catch((err) => {
            console.warn('Failed to apply remote highlight locally:', err);
          });
        }
      }
    } catch {
      // Offline-first: local state and the existing outbox remain usable.
    }
  }

  private applyRemoteProgress(progress: SupabaseProgressRow): void {
    console.warn(
      '[continue] applyRemoteProgress book',
      progress.bookId,
      'cfi',
      progress.cfiLocation.slice(0, 60),
      'pct',
      progress.percentage,
    );
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
    // D6 (SCEN-duration-1): drop sub-30s sessions BEFORE validation — they are
    // neither stored nor enqueued (the flush-side gate would no-op anyway).
    if (event.durationSeconds < MIN_SESSION_DURATION_SECONDS) return;

    if (!this.isValidSessionProgressEvent(event)) return;

    const payload: ReadingSessionInput = {
      bookId,
      startedAt: event.startedAt,
      endedAt: event.endedAt,
      durationSeconds: event.durationSeconds,
      startPercentage: event.startPercentage,
      endPercentage: event.endPercentage,
      userId: authState.userId ?? '',
    };

    try {
      const saved = await saveReadingSession(payload);
      void this.onStatsRefreshNeeded?.(bookId);

      // D9 (SCEN-push-1/5): enqueue AFTER the local save succeeded. Plain
      // add() — NEVER addCoalescedSyncOutboxItem (bookId-keyed coalesce would
      // collapse distinct sessions into one remote row). The local row is
      // already safe, so an enqueue failure is logged and swallowed — never
      // a throw that could mask the successful save.
      const outboxPayload = {
        id: saved.id,
        bookId,
        startedAt: event.startedAt,
        endedAt: event.endedAt,
        durationMinutes: saved.durationMinutes,
        date: saved.date,
        userId: authState.userId ?? '',
        updatedAtEpochMillis: saved.updatedAtEpochMillis,
        startPercentage: event.startPercentage,
        endPercentage: event.endPercentage,
      };
      try {
        await outboxDao.add('READING_SESSION', bookId, 'UPSERT', JSON.stringify(outboxPayload));
      } catch (enqueueError) {
        console.error('Failed to enqueue reading session for sync:', enqueueError);
      }
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
  private unsubscribeRemoteSessions: (() => void) | null = null;
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
            // EPUB bookmarks are anchored by CFI (no real page); use 1 as the
            // minimum-valid placeholder instead of 0 (page 0 is not a real page).
            pageNumber: 1,
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
        const { id, bookId, cfiRange, textContent, note, color, deletedAt, page } = payload;
        const key = `highlight:${id ?? cfiRange}`;
        const previous = this.appliedRemote.get(key);
        if (previous && Date.parse(payload.updatedAt) <= Date.parse(previous)) return;
        this.appliedRemote.set(key, payload.updatedAt);

        if (deletedAt) {
          deleteHighlight(id ?? '').catch((e) => {
            console.error('Failed to apply remote highlight delete locally:', e);
          });
        } else {
          // Page anchoring is format-aware (single pipeline, anchor per format):
          // - PDF highlights carry a real, positive `page` from Supabase.
          // - EPUB highlights are anchored by CFI (`cfiRange`), not a page, so
          //   Supabase `page` is null. Match the local EPUB reader fallback
          //   (`pageNumber ?? 1`, see ReaderWorkspace.handleColorSelect) instead
          //   of passing 0, which normalizePageNumber rejects (must be > 0).
          const pageNumber =
            typeof page === 'number' && Number.isInteger(page) && page > 0 ? page : 1;
          saveHighlight({
            id: id ?? crypto.randomUUID(),
            bookId: bookId,
            text: textContent,
            color: color,
            pageNumber,
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
   * Subscribe to Realtime changes for reading sessions (4th channel, D14).
   * (1) Initial pull: fetch all remote sessions for the user and merge them
   * into local SQLite in 500-row chunks via the Rust LWW command (D11).
   * (2) Realtime: on each remote INSERT/UPDATE, merge the single row and
   * refresh stats/streak for the affected book (REQ-refresh).
   */
  subscribeToRemoteSessions(): void {
    if (this.unsubscribeRemoteSessions) return;
    if (!authState.userId) return;

    try {
      if (!this.supabaseSync) {
        this.supabaseSync = new SupabaseProgressSync(authState.userId);
      }

      // (1) Initial pull — merge the full remote set in chunks of 500.
      void this.supabaseSync
        .fetchReadingSessions()
        .then((rows) => {
          const chunkSize = 500;
          for (let i = 0; i < rows.length; i += chunkSize) {
            const chunk = rows.slice(i, i + chunkSize);
            void upsertRemoteReadingSessionsCmd(chunk).catch((e) => {
              console.error('Failed to apply remote reading sessions locally:', e);
            });
          }
        })
        .catch((e) => {
          console.error('Failed to fetch remote reading sessions:', e);
        });

      // (2) Realtime — merge each remote row as it arrives.
      this.unsubscribeRemoteSessions = this.supabaseSync.subscribeToReadingSessions((row) => {
        void upsertRemoteReadingSessionsCmd([row])
          .then(() => {
            void this.onStatsRefreshNeeded?.(row.bookId);
          })
          .catch((e) => {
            console.error('Failed to apply remote reading session locally:', e);
          });
      });
    } catch (e) {
      console.error('Failed to subscribe to remote reading sessions:', e);
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
   * Stop the Realtime subscription for reading sessions.
   */
  unsubscribeFromRemoteSessions(): void {
    this.unsubscribeRemoteSessions?.();
    this.unsubscribeRemoteSessions = null;
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
    this.subscribeToRemoteSessions();
  }

  /**
   * Unsubscribe from all Realtime channels — call on logout.
   */
  unsubscribeFromAllRemoteChanges(): void {
    this.unsubscribeFromRemoteProgress();
    this.unsubscribeFromRemoteBookmarks();
    this.unsubscribeFromRemoteHighlights();
    this.unsubscribeFromRemoteSessions();
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
