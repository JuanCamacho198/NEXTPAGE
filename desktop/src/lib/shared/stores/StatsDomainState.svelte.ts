import { getReadingStats } from "$lib/shared/api/tauriClient";
import type { ReadingStatsSummaryDto } from "$lib/shared/types";

type MaybeCommandError = Error & { commandError?: { code: string; message: string; recoverable: boolean } };

class StatsDomainState {
  // ─── State ───
  stats = $state<ReadingStatsSummaryDto | null>(null);
  isLoadingStats = $state(false);
  statsUnavailableReason = $state<string | null>(null);

  // ─── Methods ───

  async loadStats(bookId?: string): Promise<void> {
    this.isLoadingStats = true;

    try {
      this.stats = await getReadingStats(bookId);
      this.statsUnavailableReason = null;
    } catch (error) {
      const typed = error as MaybeCommandError;
      if (typed.commandError?.recoverable) {
        this.statsUnavailableReason = typed.commandError.message;
      }
      this.stats = null;
    } finally {
      this.isLoadingStats = false;
    }
  }
}

export const statsState = new StatsDomainState();
export { StatsDomainState };
