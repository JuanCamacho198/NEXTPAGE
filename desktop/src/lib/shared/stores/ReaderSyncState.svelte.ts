/**
 * ReaderSyncState — Realtime sync extraction (P2-5).
 * Owns 4 Supabase Realtime channels (progress/bookmarks/highlights/sessions),
 * 500-row pull reconciliation, appliedRemote dedupe map, highlightPullInFlight
 * guard, and highlightsVersion bump that drives local highlight reload.
 *
 * Injected into ReaderDomainState; AppState's authenticated sync now delegates
 * to this state. Byte-identical to the pre-extraction ReaderDomainState sync
 * block — no behaviour change.
 */
import {
  deleteBookmark,
  deleteHighlight,
  saveBookmark,
  saveHighlight,
  upsertRemoteHighlights as upsertRemoteHighlightsCmd,
  upsertRemoteReadingSessions as upsertRemoteReadingSessionsCmd,
  upsertProgress as upsertProgressCmd,
} from '$lib/shared/api/tauriClient';
import type { ReadingProgressDto } from '$lib/shared/types';
import { authState } from '$lib/shared/stores/AuthState.svelte';
import { SupabaseProgressSync } from '$lib/shared/sync/SupabaseProgressSync';
import type { SupabaseProgressRow } from '$lib/shared/sync/SupabaseProgressSync';

function isScopeEnabled(scope: string): boolean {
  try {
    const raw = localStorage.getItem('sync.scopes');
    if (!raw) return true;
    const map = JSON.parse(raw) as Record<string, boolean>;
    return map[scope] !== false;
  } catch {
    return true;
  }
}

export class ReaderSyncState {
  // ─── Dedupe + version ───
  appliedRemote = new Map<string, string>();
  highlightsVersion = $state(0);
  highlightPullInFlight = false;

  // ─── Supabase sync ───
  private supabaseSync: SupabaseProgressSync | null = null;
  private unsubscribeRemote: (() => void) | null = null;
  private unsubscribeRemoteBookmarks: (() => void) | null = null;
  private unsubscribeRemoteHighlights: (() => void) | null = null;
  private unsubscribeRemoteSessions: (() => void) | null = null;

  // Callback injected from ReaderDomainState (stats refresh)
  onStatsRefreshNeeded: ((bookId: string) => Promise<void>) | null = null;

  // Optional hooks for remote progress apply (injected to avoid circular import)
  private applyRemoteProgressHook: ((progress: SupabaseProgressRow) => void) | null = null;
  private getActiveReadingBookId: (() => string | null) | null = null;

  injectDomainHooks(hooks: {
    applyRemoteProgress: (progress: SupabaseProgressRow) => void;
    getActiveReadingBookId: () => string | null;
    onStatsRefreshNeeded?: () => ((bookId: string) => Promise<void>) | null;
  }): void {
    this.applyRemoteProgressHook = hooks.applyRemoteProgress;
    this.getActiveReadingBookId = hooks.getActiveReadingBookId;
    if (hooks.onStatsRefreshNeeded) {
      // keep getter lazy — domain may reassign
      Object.defineProperty(this, 'onStatsRefreshNeeded', {
        get: () => hooks.onStatsRefreshNeeded!(),
        set: (v) => {
          // no-op: domain owns the value, sync just reads it
        },
        configurable: true,
      });
    }
  }

  // ─── Pull: fetchAndApplyBookState (called from ReaderDomainState.startReading) ───
  async fetchAndApplyBookState(
    sync: SupabaseProgressSync,
    bookId: string,
    epoch: number,
    localUpdatedAt: string | null,
    isStale: () => boolean,
  ): Promise<void> {
    try {
      const remote = await sync.fetchBookState(bookId);
      if (isStale()) {
        console.warn(
          '[continue] remote progress stale epoch',
          epoch,
          'activeBook',
          this.getActiveReadingBookId?.() ?? null,
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
        this.applyRemoteProgressInternal(remote.progress);
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
        try {
          if (highlight.deletedAt && highlight.id) {
            await deleteHighlight(highlight.id);
          } else {
            const rawPage = highlight.page ?? 0;
            const pageNumber =
              Number.isInteger(rawPage) && rawPage > 0 ? rawPage : highlight.cfiRange ? 1 : 1;
            await saveHighlight({
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
            });
          }
        } catch (err) {
          console.warn('Failed to apply remote highlight locally:', err);
        }
      }
      if (remote.highlights.length > 0) {
        console.warn(
          '[highlights] fetchAndApplyBookState applied',
          remote.highlights.length,
          'highlights for book',
          bookId.slice(0, 4),
          'bumping highlightsVersion',
        );
        this.highlightsVersion++;
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('highlights:changed', { detail: { bookId } }));
        }
      }
    } catch {
      // Offline-first: local state and the existing outbox remain usable.
    }
  }

  private applyRemoteProgressInternal(progress: SupabaseProgressRow): void {
    console.warn(
      '[continue] applyRemoteProgress book',
      progress.bookId,
      'cfi',
      progress.cfiLocation.slice(0, 60),
      'pct',
      progress.percentage,
    );
    if (this.applyRemoteProgressHook) {
      this.applyRemoteProgressHook(progress);
    }
    this.appliedRemote.set(`progress:${progress.bookId}`, progress.updatedAt);
    void upsertProgressCmd({
      id: progress.id ?? crypto.randomUUID(),
      bookId: progress.bookId,
      cfiLocation: progress.cfiLocation,
      percentage: progress.percentage,
      updatedAt: progress.updatedAt,
    });
  }

  // Direct apply for Realtime progress channel (used by subscribeToRemoteProgress)
  private applyRemoteProgressRealtime(payload: SupabaseProgressRow): void {
    this.applyRemoteProgressInternal(payload);
  }

  // ─── Realtime subscriptions ───

  subscribeToRemoteProgress(): void {
    if (this.unsubscribeRemote) return;
    if (!authState.userId) return;
    try {
      this.supabaseSync = new SupabaseProgressSync(authState.userId);
      this.unsubscribeRemote = this.supabaseSync.subscribeToProgress((payload) => {
        const { bookId, cfiLocation, percentage, updatedAt } = payload;
        const key = `progress:${bookId}`;
        const previous = this.appliedRemote.get(key);
        if (previous && Date.parse(updatedAt) <= Date.parse(previous)) return;
        this.appliedRemote.set(key, updatedAt);
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
        if (this.getActiveReadingBookId?.() === bookId) {
          this.applyRemoteProgressRealtime(payload);
        }
      });
    } catch (e) {
      console.error('Failed to subscribe to remote progress:', e);
    }
  }

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
          deleteBookmark(id ?? '').catch((e) => {
            console.error('Failed to apply remote bookmark delete locally:', e);
          });
        } else {
          saveBookmark({
            id: id ?? crypto.randomUUID(),
            bookId: bookId,
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

  subscribeToRemoteHighlights(): void {
    if (this.unsubscribeRemoteHighlights) return;
    if (!authState.userId) return;
    try {
      if (!this.supabaseSync) {
        this.supabaseSync = new SupabaseProgressSync(authState.userId);
      }
      if (!this.highlightPullInFlight) {
        this.highlightPullInFlight = true;
        void this.supabaseSync
          .fetchAllHighlightsForPull()
          .then((rows) => {
            if (rows.length > 0) {
              console.warn('[highlights] initial pull fetched', rows.length, 'rows');
            }
            const chunkSize = 500;
            for (let i = 0; i < rows.length; i += chunkSize) {
              const chunk = rows.slice(i, i + chunkSize);
              void upsertRemoteHighlightsCmd(chunk)
                .then(() => {
                  for (const h of chunk) {
                    const iso = new Date(h.updatedAtEpochMillis).toISOString();
                    this.appliedRemote.set(`highlight:${h.id}`, iso);
                  }
                  if (chunk.length > 0) {
                    this.highlightsVersion++;
                    if (typeof window !== 'undefined') {
                      window.dispatchEvent(new CustomEvent('highlights:changed', { detail: { source: 'pull' } }));
                    }
                  }
                })
                .catch((e) => {
                  console.error('Failed to apply remote highlights locally:', e);
                });
            }
          })
          .catch((e) => {
            console.error('Failed to fetch remote highlights:', e);
          });
      }
      this.unsubscribeRemoteHighlights = this.supabaseSync.subscribeToHighlights((payload) => {
        const { id, bookId, cfiRange, textContent, note, color, deletedAt, page } = payload;
        const key = `highlight:${id ?? cfiRange}`;
        const previous = this.appliedRemote.get(key);
        if (previous && Date.parse(payload.updatedAt) <= Date.parse(previous)) return;
        this.appliedRemote.set(key, payload.updatedAt);
        const bump = (): void => {
          this.highlightsVersion++;
          if (typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('highlights:changed', { detail: { bookId } }));
          }
        };
        if (deletedAt) {
          deleteHighlight(id ?? '')
            .then(bump)
            .catch((e) => {
              console.error('Failed to apply remote highlight delete locally:', e);
            });
        } else {
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
          })
            .then(bump)
            .catch((e) => {
              console.error('Failed to apply remote highlight locally:', e);
            });
        }
      });
    } catch (e) {
      console.error('Failed to subscribe to remote highlights:', e);
    }
  }

  subscribeToRemoteSessions(): void {
    if (this.unsubscribeRemoteSessions) return;
    if (!authState.userId) return;
    try {
      if (!this.supabaseSync) {
        this.supabaseSync = new SupabaseProgressSync(authState.userId);
      }
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
      this.unsubscribeRemoteSessions = this.supabaseSync.subscribeToReadingSessions((row) => {
        void upsertRemoteReadingSessionsCmd([row])
          .then(() => {
            const cb = this.onStatsRefreshNeeded as unknown as ((bookId: string) => Promise<void>) | null;
            void cb?.(row.bookId);
          })
          .catch((e) => {
            console.error('Failed to apply remote reading session locally:', e);
          });
      });
    } catch (e) {
      console.error('Failed to subscribe to remote reading sessions:', e);
    }
  }

  private maybeClearSupabaseSync(): void {
    if (
      !this.unsubscribeRemote &&
      !this.unsubscribeRemoteBookmarks &&
      !this.unsubscribeRemoteHighlights &&
      !this.unsubscribeRemoteSessions
    ) {
      if (this.supabaseSync) {
        try {
          this.supabaseSync.destroy();
        } catch {}
        this.supabaseSync = null;
      }
    }
  }

  unsubscribeFromRemoteProgress(): void {
    try {
      this.unsubscribeRemote?.();
    } catch {}
    this.unsubscribeRemote = null;
    this.maybeClearSupabaseSync();
  }

  unsubscribeFromRemoteBookmarks(): void {
    try {
      this.unsubscribeRemoteBookmarks?.();
    } catch {}
    this.unsubscribeRemoteBookmarks = null;
    this.maybeClearSupabaseSync();
  }

  unsubscribeFromRemoteHighlights(): void {
    try {
      this.unsubscribeRemoteHighlights?.();
    } catch {}
    this.unsubscribeRemoteHighlights = null;
    this.highlightPullInFlight = false;
    this.maybeClearSupabaseSync();
  }

  unsubscribeFromRemoteSessions(): void {
    try {
      this.unsubscribeRemoteSessions?.();
    } catch {}
    this.unsubscribeRemoteSessions = null;
    this.maybeClearSupabaseSync();
  }

  refreshRemoteProgressSubscription(): void {
    this.unsubscribeFromRemoteProgress();
    this.subscribeToRemoteProgress();
  }

  subscribeToAllRemoteChanges(): void {
    if (isScopeEnabled('progress')) this.subscribeToRemoteProgress();
    if (isScopeEnabled('bookmarks')) this.subscribeToRemoteBookmarks();
    if (isScopeEnabled('highlights')) this.subscribeToRemoteHighlights();
    if (isScopeEnabled('sessions')) this.subscribeToRemoteSessions();
  }

  unsubscribeFromAllRemoteChanges(): void {
    try {
      this.unsubscribeRemote?.();
    } catch {}
    this.unsubscribeRemote = null;
    try {
      this.unsubscribeRemoteBookmarks?.();
    } catch {}
    this.unsubscribeRemoteBookmarks = null;
    try {
      this.unsubscribeRemoteHighlights?.();
    } catch {}
    this.unsubscribeRemoteHighlights = null;
    this.highlightPullInFlight = false;
    try {
      this.unsubscribeRemoteSessions?.();
    } catch {}
    this.unsubscribeRemoteSessions = null;
    if (this.supabaseSync) {
      try {
        this.supabaseSync.destroy();
      } catch {}
      this.supabaseSync = null;
    }
  }

  // For testing / direct access to sync instance
  getSupabaseSync(): SupabaseProgressSync | null {
    return this.supabaseSync;
  }

  setSupabaseSync(sync: SupabaseProgressSync | null): void {
    this.supabaseSync = sync;
  }
}

export const readerSyncState = new ReaderSyncState();
