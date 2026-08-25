import { untrack } from 'svelte';
import { debugState } from '$lib/shared/debug/debugState.svelte';
import { authState } from '$lib/stores/authState.svelte';
import { readerState } from '$lib/shared/stores/ReaderDomainState.svelte';
import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';
import {
  saveHighlight,
  deleteHighlight,
  updateHighlight,
  listHighlights,
} from '$lib/shared/api/tauriClient';
import type { HighlightDto } from '$lib/shared/types/book';
import type { LibraryBookDto } from '$lib/shared/types/library';
import type { SpineResolver } from './useSpineResolver.svelte';

type ActiveBook = LibraryBookDto & { filePath: string };

export type PersistedHighlight = {
  id: string;
  color: string;
  pageNumber: number;
  rects: Array<{ left: number; top: number; width: number; height: number }>;
  cfi?: string | null;
  text?: string;
  note?: string | null;
};

export type SelectionData = {
  text: string;
  bounds: { left: number; top: number; right: number; bottom: number };
  rects: Array<{ left: number; top: number; width: number; height: number }>;
  pageNumber: number;
  cfi: string | null;
};

export type HighlightsDeps = {
  getBook: () => ActiveBook | null;
  spine: SpineResolver;
  outbox: SyncOutboxDao;
  getHighlightsVersion?: () => number;
  getUserId?: () => string | null;
  getDebugState?: () => typeof debugState | null;
};

export function createHighlights(deps: HighlightsDeps) {
  const { getBook, spine, outbox } = deps;
  const getUserId = deps.getUserId ?? (() => authState.userId);
  const getDbg = deps.getDebugState ?? (() => debugState);

  let persistedHighlights = $state<PersistedHighlight[]>([]);
  let highlightReloadTimer: ReturnType<typeof setTimeout> | null = null;
  let highlightReloadInFlight = false;
  let highlightReloadQueued = false;

  function reloadHighlights(): void {
    if (highlightReloadTimer) clearTimeout(highlightReloadTimer);
    highlightReloadTimer = setTimeout(() => {
      highlightReloadTimer = null;
      void runReloadHighlights();
    }, 32);
  }

  async function runReloadHighlights(): Promise<void> {
    if (highlightReloadInFlight) {
      highlightReloadQueued = true;
      return;
    }
    highlightReloadInFlight = true;
    try {
      do {
        highlightReloadQueued = false;
        const book = untrack(getBook);
        if (!book) {
          persistedHighlights = [];
          break;
        }
        const bookId = book.id;
        try {
          const rows: HighlightDto[] = await listHighlights(bookId);
          if (untrack(() => getBook()?.id) !== bookId) {
            console.debug('RW: listHighlights stale bookId ignored', bookId.slice(0, 4));
            continue;
          }
          console.warn(
            'RW: listHighlights loaded',
            rows.length,
            rows.map((r) => `${r.pageNumber}:${r.id.slice(0, 4)}`).join(','),
            'bookId',
            bookId.slice(0, 4),
          );
          const rowIds = new Set(rows.map((r) => r.id));
          const optimistic = untrack(() => persistedHighlights).filter((h) => !rowIds.has(h.id));
          if (book.format?.toLowerCase() === 'epub' && book.filePath) {
            await spine.ensureSpineHrefs(bookId, book.filePath);
          }
          let merged: PersistedHighlight[] = rows.map((r) => {
            let pageNumber = r.pageNumber;
            if (r.cfi) {
              const cfiTrim = r.cfi.trim();
              const m = /epubcfi\(\/6\/(\d+)!/.exec(cfiTrim);
              if (m) {
                const idx = parseInt(m[1], 10) - 1;
                if (idx >= 0 && idx !== pageNumber) {
                  console.warn('RW: fixing page mismatch', r.id.slice(0, 4), `page ${r.pageNumber} -> ${idx}`);
                  pageNumber = idx;
                  void updateHighlight({ id: r.id, pageNumber }).catch(() => {});
                }
              } else if (cfiTrim.startsWith('readium:')) {
                const spineIdx = spine.getSpineIndexForHref(cfiTrim, spine.epubSpineHrefs);
                if (spineIdx !== null && spineIdx >= 0 && spineIdx !== pageNumber) {
                  console.warn(
                    'RW: fixing page mismatch (readium)',
                    r.id.slice(0, 4),
                    `page ${r.pageNumber} -> ${spineIdx}`,
                    `href ${cfiTrim.slice(0, 40)}`,
                  );
                  pageNumber = spineIdx;
                  void updateHighlight({ id: r.id, pageNumber }).catch(() => {});
                } else if (spineIdx === null) {
                  console.warn(
                    'RW: readium href not in spine',
                    r.id.slice(0, 4),
                    cfiTrim.slice(0, 60),
                    'spineLen',
                    spine.epubSpineHrefs.length,
                  );
                }
              }
            }
            return {
              id: r.id,
              color: r.color,
              pageNumber,
              rects: [],
              cfi: r.cfi ?? null,
              text: r.text,
              note: r.note ?? null,
            };
          });
          if (optimistic.length > 0) {
            console.warn(
              'RW: preserving',
              optimistic.length,
              'optimistic highlights not yet in DB',
              optimistic.map((o) => `${o.pageNumber}:${o.id.slice(0, 4)}`).join(','),
            );
            merged = [...merged, ...optimistic];
          }
          persistedHighlights = merged;
        } catch (err) {
          console.error('Failed to load highlights:', err);
        }
      } while (highlightReloadQueued);
    } finally {
      highlightReloadInFlight = false;
    }
  }

  function cleanup(): void {
    if (highlightReloadTimer) {
      clearTimeout(highlightReloadTimer);
      highlightReloadTimer = null;
    }
  }

  async function handleColorSelect(color: string, data: SelectionData): Promise<void> {
    const dbg = getDbg();
    if (dbg) {
      dbg.epub.colorPickCount++;
      dbg.epub.lastPickedColor = color;
    }
    console.warn('RW: handleColorSelect data.pageNumber', data.pageNumber, 'cfi', data.cfi?.slice(0, 40) ?? '(null)', 'text', data.text.slice(0, 30));

    const book = untrack(getBook);
    if (data && book) {
      const highlightId = crypto.randomUUID();
      const bounds = data.bounds;
      const pageNumber = data.pageNumber ?? (book.format?.toLowerCase() === 'epub' ? 0 : 1);
      const cfi = data.cfi ?? null;
      console.warn(
        'RW: push highlight pageNumber=',
        pageNumber,
        'cfi',
        cfi?.slice(0, 60) ?? '(null)',
        'id',
        highlightId.slice(0, 4),
        'text',
        data.text.slice(0, 30),
      );

      persistedHighlights = [
        ...persistedHighlights,
        {
          id: highlightId,
          color,
          pageNumber,
          rects: data.rects,
          cfi,
          text: data.text,
          note: null,
        },
      ];

      try {
        const dbg2 = getDbg();
        if (dbg2) dbg2.epub.saveHighlightCallCount++;
        await saveHighlight({
          id: highlightId,
          bookId: book.id,
          text: data.text,
          color,
          pageNumber,
          rectLeft: bounds.left,
          rectRight: bounds.right,
          rectTop: bounds.top,
          rectBottom: bounds.bottom,
          cfi,
        });
        const uid = getUserId();
        if (uid) {
          void outbox.add(
            'HIGHLIGHT',
            highlightId,
            'UPSERT',
            JSON.stringify({
              userId: uid,
              bookId: book.id,
              cfiRange: cfi ?? '',
              textContent: data.text,
              color,
              page: pageNumber,
              locatorJson: readerState.locatorJson,
              updatedAt: new Date().toISOString(),
            }),
          );
        }
      } catch (err) {
        const dbg3 = getDbg();
        if (dbg3) {
          dbg3.epub.saveHighlightLastError = String(err);
          if (!dbg3.epub.failedHighlightIds.includes(highlightId)) {
            dbg3.epub.failedHighlightIds.push(highlightId);
          }
        }
        console.warn('Failed to save highlight:', err);
      }
    }

    // 220ms delay before clearing selection — preserves transition and avoids
    // selectionchange race (captured data is used, not global lastSelectionData)
    setTimeout(() => {
      window.getSelection()?.removeAllRanges();
    }, 220);
  }

  function updateHighlightColor(id: string, color: string): void {
    persistedHighlights = persistedHighlights.map((h) => (h.id === id ? { ...h, color } : h));
    updateHighlight({ id, color }).catch((err) => {
      console.error('Failed to update highlight color:', err);
    });
    enqueueHighlightUpdate(id, { color });
  }

  function updateHighlightNote(id: string, note: string | null): void {
    persistedHighlights = persistedHighlights.map((h) => (h.id === id ? { ...h, note } : h));
    updateHighlight({ id, note: note ?? undefined }).catch((err) => {
      console.error('Failed to update highlight note:', err);
    });
    enqueueHighlightUpdate(id, { note });
  }

  function deleteHighlightById(id: string): void {
    const highlight = persistedHighlights.find((item) => item.id === id);
    persistedHighlights = persistedHighlights.filter((h) => h.id !== id);
    deleteHighlight(id).catch((err) => console.error('Failed to delete highlight:', err));
    const uidDel = getUserId();
    if (uidDel && highlight) {
      const book = untrack(getBook);
      const updatedAt = new Date().toISOString();
      void outbox.add(
        'HIGHLIGHT',
        id,
        'DELETE',
        JSON.stringify({
          userId: uidDel,
          bookId: book?.id ?? id,
          cfiRange: highlight.cfi ?? '',
          textContent: highlight.text ?? '',
          color: highlight.color,
          page: highlight.pageNumber,
          locatorJson: readerState.locatorJson,
          deletedAt: updatedAt,
          updatedAt,
        }),
      );
    }
  }

  function enqueueHighlightUpdate(id: string, changes: { color?: string; note?: string | null }): void {
    const uid = getUserId();
    if (!uid) return;
    const highlight = persistedHighlights.find((item) => item.id === id);
    if (!highlight) return;
    const book = untrack(getBook);
    void outbox.add(
      'HIGHLIGHT',
      id,
      'UPSERT',
      JSON.stringify({
        userId: uid,
        bookId: book?.id ?? id,
        cfiRange: highlight.cfi ?? '',
        textContent: highlight.text ?? '',
        color: changes.color ?? highlight.color,
        note: changes.note ?? highlight.note ?? null,
        page: highlight.pageNumber,
        locatorJson: readerState.locatorJson,
        updatedAt: new Date().toISOString(),
      }),
    );
  }

  return {
    get persistedHighlights() {
      return persistedHighlights;
    },
    set persistedHighlights(v: PersistedHighlight[]) {
      persistedHighlights = v;
    },
    reloadHighlights,
    runReloadHighlights,
    handleColorSelect,
    updateHighlightColor,
    updateHighlightNote,
    deleteHighlightById,
    enqueueHighlightUpdate,
    cleanup,
    get _timer() {
      return highlightReloadTimer;
    },
    get _inFlight() {
      return highlightReloadInFlight;
    },
    get _queued() {
      return highlightReloadQueued;
    },
  };
}

export type HighlightsState = ReturnType<typeof createHighlights>;
