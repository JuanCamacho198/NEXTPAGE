import { writable, get } from "svelte/store";
import type { ReadingStatsSummaryDto } from "$lib/shared/types";
import { getReadingStats } from "$lib/shared/api/tauriClient";

export const stats = writable<ReadingStatsSummaryDto | null>(null);
export const isLoading = writable(false);
export const unavailableReason = writable<string | null>(null);

export const StatsState = {
  async loadStats(bookId?: string) {
    isLoading.set(true);
    try {
      stats.set(await getReadingStats(bookId));
      unavailableReason.set(null);
    } catch (err: any) {
      unavailableReason.set(err.message);
      stats.set(null);
    } finally {
      isLoading.set(false);
    }
  }
};