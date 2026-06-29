import { readFile } from '@tauri-apps/plugin-fs';
import {
  getProgress,
  saveProgress,
  saveReadingSession,
  updateBookProgress,
} from '$lib/shared/api/tauriClient';
import type { ReaderBook, ReadingSessionInput, SaveProgressInput } from '$lib/shared/types';

class ReaderDomainState {
  // ─── State ───
  activeReadingBookId = $state<string | null>(null);
  cfiLocation = $state('');
  percentage = $state(0);
  preloadedBytes = $state<{ filePath: string; data: Uint8Array } | null>(null);
  readerError = $state<string | null>(null);

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
