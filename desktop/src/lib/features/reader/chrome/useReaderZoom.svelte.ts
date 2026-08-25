import { getDefaultReaderSettings, upsertReaderSettings } from '$lib/shared/api/tauriClient';
import { clampZoomPercent } from '$lib/features/reader/viewer-pdf/pdfNavigation';
import { hasEditableContext } from '$lib/features/reader/viewer-epub/keyboardNav';
import type { ReaderSettings } from '$lib/shared/types';
import type { LibraryBookDto } from '$lib/shared/types/library';
import type PdfViewer from '../viewer-pdf/PdfViewer.svelte';
import type EpubNativeViewer from '../viewer-epub/EpubNativeViewer.svelte';

type ActiveBook = LibraryBookDto & { filePath: string };

export type ReaderZoomDeps = {
  getActiveBook: () => ActiveBook | null;
  getRefs: () => { pdf: PdfViewer | null; epub: EpubNativeViewer | null };
  persist?: (s: ReaderSettings) => Promise<unknown>;
};

export function createReaderZoom(deps: ReaderZoomDeps) {
  const persist = deps.persist ?? ((s: ReaderSettings) => upsertReaderSettings(s));

  let localReaderSettings = $state<ReaderSettings>(getDefaultReaderSettings());
  let persistTimer: ReturnType<typeof setTimeout> | null = null;
  let pendingWheelDelta = 0;
  let pendingWheelFrame: number | null = null;

  function handleTextSettingsChange(updated: ReaderSettings): void {
    localReaderSettings = updated;
    if (persistTimer) clearTimeout(persistTimer);
    persistTimer = setTimeout(() => {
      void persist(updated);
    }, 500);
  }

  function syncFromProps(next: ReaderSettings | undefined): void {
    if (next) {
      localReaderSettings = JSON.parse(JSON.stringify(next));
    }
  }

  function adjustZoom(delta: number): void {
    const current = clampZoomPercent(localReaderSettings.epub.fontSize ?? 100);
    const next = clampZoomPercent(current + delta);
    if (next === current) return;
    const updated: ReaderSettings = {
      ...localReaderSettings,
      epub: { ...localReaderSettings.epub, fontSize: next },
    };
    handleTextSettingsChange(updated);
    const fmt = deps.getActiveBook()?.format?.toLowerCase();
    const refs = deps.getRefs();
    if (refs.pdf && fmt === 'pdf') {
      void refs.pdf.setScale(next / 100);
    }
    if (refs.epub && fmt === 'epub') {
      void refs.epub.setZoom(next);
    }
  }

  function handleHeaderFontSizeChange(size: number): void {
    const clamped = clampZoomPercent(size);
    const updated: ReaderSettings = {
      ...localReaderSettings,
      epub: { ...localReaderSettings.epub, fontSize: clamped },
    };
    handleTextSettingsChange(updated);
    const fmt = deps.getActiveBook()?.format?.toLowerCase();
    const refs = deps.getRefs();
    if (refs.pdf && fmt === 'pdf') {
      void refs.pdf.setScale(clamped / 100);
    }
    if (refs.epub && fmt === 'epub') {
      void refs.epub.setZoom(clamped);
    }
  }

  function handleGlobalWheel(e: WheelEvent): void {
    if (!e.ctrlKey && !e.metaKey) return;
    e.preventDefault();
    pendingWheelDelta += e.deltaY;
    if (pendingWheelFrame !== null) return;
    pendingWheelFrame = requestAnimationFrame(() => {
      pendingWheelFrame = null;
      const delta = pendingWheelDelta > 0 ? -10 : 10;
      pendingWheelDelta = 0;
      adjustZoom(delta);
    });
  }

  function handleGlobalKeydown(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && (e.key === '=' || e.key === '+' || e.key === '-' || e.key === '_')) {
      if (hasEditableContext(e.target as Element | null)) return;
      e.preventDefault();
      const step = e.key === '-' || e.key === '_' ? -10 : 10;
      adjustZoom(step);
    }
  }

  function cleanup(): void {
    if (persistTimer) {
      clearTimeout(persistTimer);
      persistTimer = null;
    }
    if (pendingWheelFrame !== null) {
      cancelAnimationFrame(pendingWheelFrame);
      pendingWheelFrame = null;
    }
    pendingWheelDelta = 0;
  }

  return {
    get localReaderSettings() {
      return localReaderSettings;
    },
    set localReaderSettings(v: ReaderSettings) {
      localReaderSettings = v;
    },
    get _persistTimer() {
      return persistTimer;
    },
    get _pendingWheelFrame() {
      return pendingWheelFrame;
    },
    get _pendingWheelDelta() {
      return pendingWheelDelta;
    },
    handleTextSettingsChange,
    syncFromProps,
    adjustZoom,
    handleHeaderFontSizeChange,
    handleGlobalWheel,
    handleGlobalKeydown,
    cleanup,
  };
}

export type ReaderZoomState = ReturnType<typeof createReaderZoom>;
