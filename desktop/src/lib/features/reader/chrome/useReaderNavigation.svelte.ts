import type { TocEntry } from './ReaderTocPanel.svelte';
import type PdfViewer from '../viewer-pdf/PdfViewer.svelte';
import type EpubNativeViewer from '../viewer-epub/EpubNativeViewer.svelte';
import type { LibraryBookDto } from '$lib/shared/types/library';

type ActiveBook = LibraryBookDto & { filePath: string };

export type ReaderNavigationDeps = {
  getRefs: () => { pdf: PdfViewer | null; epub: EpubNativeViewer | null };
  getActiveBook: () => ActiveBook | null;
  getTocPanelOpen?: () => boolean;
  onPdfPageChange?: (page: number, total: number) => void;
  onPdfSessionProgress?: (event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }) => void;
  onEpubLocationChange?: (cfi: string, pct: number) => void;
  onReaderLocationContext?: (ctx: unknown) => void;
  getDebugState?: () => { readerInfo: unknown; enabled: boolean } | null;
};

export function createReaderNavigation(deps: ReaderNavigationDeps) {
  let currentPdfPage = $state(0);
  let totalPdfPages = $state(0);
  let currentEpubChapter = $state(0);
  let tocEntries = $state<TocEntry[]>([]);
  let tocNavigate = $state<TocEntry | null>(null);
  let showTocPanel = $state(false);

  const isPdf = $derived(deps.getActiveBook()?.format?.toLowerCase() === 'pdf');
  const isEpub = $derived(deps.getActiveBook()?.format?.toLowerCase() === 'epub');

  const bookProgress = $derived(
    isPdf && deps.getActiveBook()?.currentPage && deps.getActiveBook()?.totalPages
      ? Math.round((deps.getActiveBook()!.currentPage! / deps.getActiveBook()!.totalPages!) * 100)
      : 0,
  );

  const headerCurrentPage = $derived(
    isPdf ? currentPdfPage : isEpub ? currentEpubChapter + 1 : 1,
  );
  const headerTotalPages = $derived(
    isPdf ? totalPdfPages : isEpub ? (tocEntries.length > 0 ? tocEntries.length : 0) : 0,
  );
  const showHeaderReadingControls = $derived(
    headerTotalPages > 0 && deps.getActiveBook() !== null,
  );

  const prevDisabled = $derived.by(() => {
    const fmt = deps.getActiveBook()?.format?.toLowerCase();
    if (fmt === 'pdf') return currentPdfPage <= 1;
    if (fmt === 'epub') return currentEpubChapter <= 0;
    return false;
  });

  const nextDisabled = $derived.by(() => {
    const fmt = deps.getActiveBook()?.format?.toLowerCase();
    if (fmt === 'pdf') return totalPdfPages > 0 && currentPdfPage >= totalPdfPages;
    return false;
  });

  function handlePdfPageChange(page: number, total: number): void {
    currentPdfPage = page;
    totalPdfPages = total;
    deps.onPdfPageChange?.(page, total);
  }

  function handleEpubLocationChange(cfi: string, pct: number): void {
    const match = cfi.match(/chapter:(\d+)/);
    if (match) currentEpubChapter = parseInt(match[1], 10);
    deps.onEpubLocationChange?.(cfi, pct);
  }

  function handleTocReady(entries: TocEntry[]): void {
    tocEntries = entries;
  }

  function handleTocNavigate(entry: TocEntry): void {
    tocNavigate = entry;
    showTocPanel = false;
  }

  function toggleTocPanel(): void {
    showTocPanel = !showTocPanel;
  }

  function goPrev(): boolean {
    const fmt = deps.getActiveBook()?.format?.toLowerCase();
    const refs = deps.getRefs();
    if (fmt === 'pdf') {
      if (!refs.pdf) return false;
      if (currentPdfPage <= 1) return false;
      void refs.pdf.navigateToPage(currentPdfPage - 1);
      return true;
    }
    if (fmt === 'epub') {
      if (!refs.epub) return false;
      if (currentEpubChapter <= 0) return false;
      refs.epub.goToPrev();
      return true;
    }
    return false;
  }

  function goNext(): boolean {
    const fmt = deps.getActiveBook()?.format?.toLowerCase();
    const refs = deps.getRefs();
    if (fmt === 'pdf') {
      if (!refs.pdf) return false;
      if (totalPdfPages > 0 && currentPdfPage >= totalPdfPages) return false;
      void refs.pdf.navigateToPage(currentPdfPage + 1);
      return true;
    }
    if (fmt === 'epub') {
      if (!refs.epub) return false;
      refs.epub.goToNext();
      return true;
    }
    return false;
  }

  async function handleHeaderGoToPage(page: number): Promise<boolean> {
    const fmt = deps.getActiveBook()?.format?.toLowerCase();
    const refs = deps.getRefs();
    if (fmt === 'pdf') {
      if (!refs.pdf) return false;
      return refs.pdf.navigateToPage(page);
    }
    if (fmt === 'epub') {
      if (!refs.epub) return false;
      return refs.epub.handleGoToPage(page);
    }
    return false;
  }

  function cleanup(): void {
    // no timers — placeholder for uniform interface
  }

  return {
    get currentPdfPage() {
      return currentPdfPage;
    },
    set currentPdfPage(v: number) {
      currentPdfPage = v;
    },
    get totalPdfPages() {
      return totalPdfPages;
    },
    set totalPdfPages(v: number) {
      totalPdfPages = v;
    },
    get currentEpubChapter() {
      return currentEpubChapter;
    },
    set currentEpubChapter(v: number) {
      currentEpubChapter = v;
    },
    get tocEntries() {
      return tocEntries;
    },
    set tocEntries(v: TocEntry[]) {
      tocEntries = v;
    },
    get tocNavigate() {
      return tocNavigate;
    },
    set tocNavigate(v: TocEntry | null) {
      tocNavigate = v;
    },
    get showTocPanel() {
      return showTocPanel;
    },
    set showTocPanel(v: boolean) {
      showTocPanel = v;
    },
    get isPdf() {
      return isPdf;
    },
    get isEpub() {
      return isEpub;
    },
    get bookProgress() {
      return bookProgress;
    },
    get headerCurrentPage() {
      return headerCurrentPage;
    },
    get headerTotalPages() {
      return headerTotalPages;
    },
    get showHeaderReadingControls() {
      return showHeaderReadingControls;
    },
    get prevDisabled() {
      return prevDisabled;
    },
    get nextDisabled() {
      return nextDisabled;
    },
    handlePdfPageChange,
    handleEpubLocationChange,
    handleTocReady,
    handleTocNavigate,
    toggleTocPanel,
    goPrev,
    goNext,
    handleHeaderGoToPage,
    cleanup,
  };
}

export type ReaderNavigation = ReturnType<typeof createReaderNavigation>;
