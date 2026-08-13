import {
  getReadingActivity,
  getReadingStats,
  getReadingStatsForRange,
  getReadingStreak,
} from '$lib/shared/api/tauriClient';
import type { ActivityPoint, ReadingStatsSummaryDto } from '$lib/shared/types';

type MaybeCommandError = Error & {
  commandError?: { code: string; message: string; recoverable: boolean };
};

class StatsDomainState {
  // ─── State ───
  stats = $state<ReadingStatsSummaryDto | null>(null);
  isLoadingStats = $state(false);
  statsUnavailableReason = $state<string | null>(null);

  // ─── Reading activity ───
  activitySeries = $state<ActivityPoint[]>([]);
  isLoadingActivity = $state(false);
  activityUnavailableReason = $state<string | null>(null);

  // ─── Range stats (current + previous for delta) ───
  currentStats = $state<ReadingStatsSummaryDto | null>(null);
  previousStats = $state<ReadingStatsSummaryDto | null>(null);
  isLoadingRange = $state(false);
  rangeUnavailableReason = $state<string | null>(null);

  // ─── Streak ───
  streakDays = $state<number>(0);
  isLoadingStreak = $state(false);
  streakUnavailableReason = $state<string | null>(null);

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

  async loadActivity(period: string, granularity: string, bookId?: string): Promise<void> {
    this.isLoadingActivity = true;
    this.activityUnavailableReason = null;

    try {
      this.activitySeries = await getReadingActivity(period, granularity, bookId);
    } catch (error) {
      const typed = error as MaybeCommandError;
      if (typed.commandError?.recoverable) {
        this.activityUnavailableReason = typed.commandError.message;
      }
      this.activitySeries = [];
    } finally {
      this.isLoadingActivity = false;
    }
  }

  async loadRangeStats(
    from: string,
    to: string,
    bookId?: string,
    target: 'current' | 'previous' = 'current',
  ): Promise<void> {
    if (target === 'current') {
      this.isLoadingRange = true;
      this.rangeUnavailableReason = null;
    }

    try {
      const stats = await getReadingStatsForRange(from, to, bookId);
      if (target === 'current') {
        this.currentStats = stats;
      } else {
        this.previousStats = stats;
      }
    } catch (error) {
      const typed = error as MaybeCommandError;
      if (typed.commandError?.recoverable) {
        if (target === 'current') {
          this.rangeUnavailableReason = typed.commandError.message;
        }
      }
      if (target === 'current') {
        this.currentStats = null;
      } else {
        this.previousStats = null;
      }
    } finally {
      if (target === 'current') {
        this.isLoadingRange = false;
      }
    }
  }

  async loadStreak(bookId?: string, userId = ''): Promise<void> {
    this.isLoadingStreak = true;
    this.streakUnavailableReason = null;

    try {
      this.streakDays = await getReadingStreak(bookId, userId);
    } catch (error) {
      const typed = error as MaybeCommandError;
      if (typed.commandError?.recoverable) {
        this.streakUnavailableReason = typed.commandError.message;
      }
      this.streakDays = 0;
    } finally {
      this.isLoadingStreak = false;
    }
  }
}

export const statsState = new StatsDomainState();
export { StatsDomainState };
