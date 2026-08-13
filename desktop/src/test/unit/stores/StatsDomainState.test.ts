import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockGetReadingActivity = vi.hoisted(() => vi.fn());
const mockGetReadingStatsForRange = vi.hoisted(() => vi.fn());
const mockGetReadingStreak = vi.hoisted(() => vi.fn());

vi.mock('$lib/shared/api/tauriClient', () => ({
  getReadingActivity: mockGetReadingActivity,
  getReadingStats: vi.fn().mockResolvedValue(null),
  getReadingStatsForRange: mockGetReadingStatsForRange,
  getReadingStreak: mockGetReadingStreak,
}));

import { statsState } from '$lib/shared/stores/StatsDomainState.svelte';

/**
 * Build a CommandError-shaped error object.
 * Mirrors what attachCommandError does in tauriClient.ts.
 */
function makeCommandError(
  message: string,
  code = 'TEST_ERR',
): Error & { commandError: { code: string; message: string; recoverable: boolean } } {
  const err = new Error(message) as Error & {
    commandError: { code: string; message: string; recoverable: boolean };
  };
  err.commandError = { code, message, recoverable: true };
  return err;
}

describe('StatsDomainState', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset state fields to defaults
    statsState.activitySeries = [];
    statsState.isLoadingActivity = false;
    statsState.activityUnavailableReason = null;
    statsState.currentStats = null;
    statsState.previousStats = null;
    statsState.isLoadingRange = false;
    statsState.rangeUnavailableReason = null;
    statsState.streakDays = 0;
    statsState.isLoadingStreak = false;
    statsState.streakUnavailableReason = null;
  });

  describe('loadActivity', () => {
    it('populates activitySeries and clears unavailableReason on success', async () => {
      const points = [
        { bucket: '2026-06-24', minutes: 10 },
        { bucket: '2026-06-25', minutes: 5 },
      ];
      mockGetReadingActivity.mockResolvedValueOnce(points);

      await statsState.loadActivity('week', 'day');

      expect(statsState.activitySeries).toEqual(points);
      expect(statsState.activityUnavailableReason).toBeNull();
      expect(statsState.isLoadingActivity).toBe(false);
    });

    it('sets activityUnavailableReason on CommandError rejection', async () => {
      mockGetReadingActivity.mockRejectedValueOnce(makeCommandError('Activity data unavailable'));

      await statsState.loadActivity('month', 'day');

      expect(statsState.activitySeries).toEqual([]);
      expect(statsState.activityUnavailableReason).toBe('Activity data unavailable');
      expect(statsState.isLoadingActivity).toBe(false);
    });

    it('uses generic fallback for non-CommandError rejection', async () => {
      mockGetReadingActivity.mockRejectedValueOnce(new Error('network error'));

      await statsState.loadActivity('year', 'week');

      expect(statsState.activitySeries).toEqual([]);
      expect(statsState.activityUnavailableReason).toBeNull();
      expect(statsState.isLoadingActivity).toBe(false);
    });
  });

  describe('loadRangeStats', () => {
    const sampleStats = {
      totalMinutesRead: 120,
      totalSessions: 3,
      booksStarted: 1,
      booksCompleted: 0,
      avgProgressPercentage: 15.5,
    };

    it('populates currentStats and clears unavailableReason on success', async () => {
      mockGetReadingStatsForRange.mockResolvedValueOnce(sampleStats);

      await statsState.loadRangeStats('2026-06-01T00:00:00Z', '2026-06-30T23:59:59Z');

      expect(statsState.currentStats).toEqual(sampleStats);
      expect(statsState.rangeUnavailableReason).toBeNull();
      expect(statsState.isLoadingRange).toBe(false);
    });

    it('sets rangeUnavailableReason on CommandError rejection', async () => {
      mockGetReadingStatsForRange.mockRejectedValueOnce(
        makeCommandError('Stats unavailable for this range'),
      );

      await statsState.loadRangeStats('2026-01-01T00:00:00Z', '2026-01-31T23:59:59Z');

      expect(statsState.currentStats).toBeNull();
      expect(statsState.rangeUnavailableReason).toBe('Stats unavailable for this range');
      expect(statsState.isLoadingRange).toBe(false);
    });

    it('uses generic fallback for non-CommandError rejection', async () => {
      mockGetReadingStatsForRange.mockRejectedValueOnce(new Error('db error'));

      await statsState.loadRangeStats('2026-06-01T00:00:00Z', '2026-06-30T23:59:59Z');

      expect(statsState.currentStats).toBeNull();
      expect(statsState.rangeUnavailableReason).toBeNull();
      expect(statsState.isLoadingRange).toBe(false);
    });
  });

  describe('loadStreak', () => {
    it('populates streakDays and clears unavailableReason on success', async () => {
      mockGetReadingStreak.mockResolvedValueOnce(7);

      await statsState.loadStreak('book-1');

      expect(statsState.streakDays).toBe(7);
      expect(statsState.streakUnavailableReason).toBeNull();
      expect(statsState.isLoadingStreak).toBe(false);
    });

    it('passes userId through to getReadingStreak (D13)', async () => {
      mockGetReadingStreak.mockResolvedValueOnce(5);

      await statsState.loadStreak('book-1', 'user-42');

      expect(mockGetReadingStreak).toHaveBeenCalledWith('book-1', 'user-42');
      expect(statsState.streakDays).toBe(5);
    });

    it('defaults userId to empty string so legacy rows still count (D12)', async () => {
      mockGetReadingStreak.mockResolvedValueOnce(3);

      await statsState.loadStreak('book-1');

      expect(mockGetReadingStreak).toHaveBeenCalledWith('book-1', '');
    });

    it('sets streakUnavailableReason on CommandError rejection', async () => {
      mockGetReadingStreak.mockRejectedValueOnce(makeCommandError('Streak data unavailable'));

      await statsState.loadStreak();

      expect(statsState.streakDays).toBe(0);
      expect(statsState.streakUnavailableReason).toBe('Streak data unavailable');
      expect(statsState.isLoadingStreak).toBe(false);
    });

    it('uses generic fallback for non-CommandError rejection', async () => {
      mockGetReadingStreak.mockRejectedValueOnce(new Error('timeout'));

      await statsState.loadStreak('book-2');

      expect(statsState.streakDays).toBe(0);
      expect(statsState.streakUnavailableReason).toBeNull();
      expect(statsState.isLoadingStreak).toBe(false);
    });
  });
});
