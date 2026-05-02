import { writable, get } from "svelte/store";
import {
  getProgress,
  saveProgress,
  saveReadingSession,
  searchBookText,
  updateBookProgress,
} from "$lib/shared/api/tauriClient";
import type {
  ReadingSessionInput,
  SaveProgressInput,
  SearchBookTextResponse,
  SearchNavigationTarget,
} from "$lib/shared/types";

export const activeReadingBookId = writable<string | null>(null);
export const cfiLocation = writable("");
export const percentage = writable(0);
export const searchResponse = writable<SearchBookTextResponse | null>(null);
export const searchTargetLocator = writable<string | null>(null);
export const isSearching = writable(false);
export const unavailableReason = writable<string | null>(null);

export const ReaderState = {
  async startReading(bookId: string, format: string, onStatsLoad?: (id: string) => void) {
    activeReadingBookId.set(bookId);
    searchResponse.set(null);
    searchTargetLocator.set(null);

    if (format.toLowerCase() === "epub") {
      try {
        const progress = await getProgress(bookId);
        cfiLocation.set(progress?.cfiLocation ?? "");
        percentage.set(progress?.percentage ?? 0);
      } catch {
        cfiLocation.set("");
        percentage.set(0);
      }
    }

    if (onStatsLoad) {
      onStatsLoad(bookId);
    }
  },

  async handlePdfPageChange(bookId: string, page: number) {
    try {
      await updateBookProgress(bookId, page);
    } catch {
      // ignore
    }
  },

  async handlePdfSessionProgress(bookId: string, event: any) {
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
    } catch {
      // ignore
    }
  },

  async handleEpubLocationChange(bookId: string, nextLocation: string, nextPercentage: number) {
    cfiLocation.set(nextLocation);
    percentage.set(Math.max(0, Math.min(100, nextPercentage)));

    const payload: SaveProgressInput = {
      bookId,
      cfiLocation: nextLocation,
      percentage: get(percentage),
    };

    try {
      await saveProgress(payload);
    } catch {
      // ignore
    }
  },

  async handleSearch(bookId: string, query: string, page: number) {
    isSearching.set(true);
    try {
      searchResponse.set(await searchBookText({
        bookId,
        query,
        page,
        pageSize: 200,
      }));
      unavailableReason.set(null);
    } catch (err: any) {
      unavailableReason.set(err.message);
      searchResponse.set(null);
    } finally {
      isSearching.set(false);
    }
  },

  handleSearchJump(target: SearchNavigationTarget) {
    searchTargetLocator.set(target.locator);
  },

  stopReading() {
    activeReadingBookId.set(null);
  }
};