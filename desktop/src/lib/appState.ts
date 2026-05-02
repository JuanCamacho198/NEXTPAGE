import { get } from "svelte/store";
import * as homeState from "$lib/features/home/state";
import * as libraryState from "$lib/features/library/state";
import * as readerState from "$lib/features/reader/state";
import * as statsState from "$lib/features/stats/state";
import * as settingsState from "$lib/features/settings/state";
import { initTheme } from "$lib/shared/stores/theme";
import { i18n } from "$lib/shared/i18n";
import { extractPdfMetadata } from "$lib/shared/services/pdfThumbnail";
import { upsertBookCover, upsertBook, saveProgress } from "$lib/shared/api/tauriClient";

export const readerError = globalThis.document ? require('$lib/shared/stores').writable<string | null>(null) : null;
export const thumbnailGenerationInFlight = new Set<string>();
export const thumbnailGenerationAttempted = new Set<string>();

export const appState = {
  get home() {
    return {
      route: homeState.route,
      previewBookId: homeState.previewBookId,
      shelfDetailsBookId: homeState.shelfDetailsBookId,
      shelfTab: homeState.shelfTab,
      shelfSortKey: homeState.shelfSortKey,
      shelfViewMode: homeState.shelfViewMode,
      shelfRawQuery: homeState.shelfRawQuery,
      setRoute: homeState.setRoute,
      openDetails: homeState.openDetails,
      openShelfDetails: homeState.openShelfDetails,
      closeShelfDetails: homeState.closeShelfDetails,
      setShelfTab: homeState.shelfTab.set,
      setShelfSort: homeState.shelfSortKey.set,
      setShelfViewMode: homeState.shelfViewMode.set,
      handleShelfQueryInput: homeState.shelfRawQuery.set,
      clearShelfQuery: () => homeState.shelfRawQuery.set(""),
      getShelfBooks: homeState.getShelfBooks,
    };
  },

  get library() {
    return libraryState;
  },

  get reader() {
    return {
      activeReadingBookId: readerState.activeReadingBookId,
      cfiLocation: readerState.cfiLocation,
      percentage: readerState.percentage,
      searchResponse: readerState.searchResponse,
      searchTargetLocator: readerState.searchTargetLocator,
      isSearching: readerState.isSearching,
      unavailableReason: readerState.unavailableReason,
      ...readerState.ReaderState,
    };
  },

  get stats() {
    return {
      stats: statsState.stats,
      isLoading: statsState.isLoading,
      unavailableReason: statsState.unavailableReason,
      ...statsState.StatsState,
    };
  },

  get settings() {
    return {
      locale: settingsState.locale,
      readerSettings: settingsState.readerSettings,
      loadSettings: settingsState.SettingsState.loadSettings,
      setLocale: settingsState.SettingsState.setLocale,
      setReaderSettings: settingsState.SettingsState.setReaderSettings,
    };
  },

  async init() {
    initTheme();
    try {
      const [nextLocale] = await Promise.all([
        i18n.initializeLocale(),
        this.settings.loadSettings(),
        this.library.loadLibrary((books) => {
          this.reconcileHome(books);
        }),
        this.stats.loadStats()
      ]);
      this.settings.setLocale(nextLocale);
    } catch (error) {
      console.error("Initialization error:", error);
    }
  },

  reconcileHome(books: any[]) {
  },

  async startReading(book: any) {
    await this.reader.startReading(book.id, book.format, (id) => this.stats.loadStats(id));
    this.home.setRoute("reader");
  },

  async backToHome() {
    this.home.setRoute("home");
    this.reader.stopReading();
  },

  async handleMarkCompleted(book: any) {
    await this.library.handleMarkCompleted(book, async () => {
      await saveProgress({
        bookId: book.id,
        cfiLocation: "",
        percentage: 100,
      });
    });
    await this.stats.loadStats();
  },

  shouldGeneratePdfCover(book: any) {
    if (book.format.toLowerCase() !== "pdf") return false;
    if (book.coverPath && book.coverPath.trim().length > 0) return false;
    return book.filePath.trim().length > 0;
  },

  async ensurePdfCover(book: any) {
    if (thumbnailGenerationInFlight.has(book.id)) return;
    thumbnailGenerationInFlight.add(book.id);
    try {
      const metadata = await extractPdfMetadata(book.filePath);
      if (metadata.thumbnailBytes) {
        await upsertBookCover({
          bookId: book.id,
          data: Array.from(metadata.thumbnailBytes),
          mimeType: "image/png",
        });
      }

      if (metadata.author && (!book.author || book.author.trim() === "")) {
        await upsertBook({
          ...book,
          author: metadata.author,
          syncStatus: "local"
        });
      }
      await this.library.loadLibrary();
    } catch (e) {
      console.error(`ensurePdfCover failed:`, e);
    } finally {
      thumbnailGenerationInFlight.delete(book.id);
    }
  }
};

export function createAppState() {
  return appState;
}