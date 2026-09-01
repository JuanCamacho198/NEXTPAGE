import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import HomeStatsGrid from '$lib/features/home/components/HomeStatsGrid.svelte';
import type { ReadingStatsSummaryDto } from '$lib/shared/types';

const hexPattern = /#[0-9a-fA-F]{3,8}/;

const dictionary: Record<string, string> = {
  'stats.booksStartedLabel': 'Started',
  'stats.booksCompletedLabel': 'Completed',
  'stats.sessionsLabel': 'Sessions',
  'stats.streakLabel': 'Streak',
  'stats.days': '{{count}} days',
  'home.metrics.dailyGoalLabel': 'Daily goal',
  'home.metrics.minutesFormat': '{{current}}/{{total}} min',
};

const t = (key: string, params?: Record<string, string | number>): string => {
  const template = dictionary[key] ?? key;
  if (!params) {
    return template;
  }
  return template.replace(/\{\{\s*([\w.-]+)\s*\}\}/g, (_match, token: string) =>
    String(params[token] ?? ''),
  );
};

const stats: ReadingStatsSummaryDto = {
  totalMinutesRead: 0,
  totalSessions: 7,
  booksStarted: 4,
  booksCompleted: 2,
  avgProgressPercentage: 0,
};

describe('HomeStatsGrid', () => {
  it('renders five metric cards in Pen order with translated labels and values', () => {
    const { container } = render(HomeStatsGrid, {
      props: {
        stats,
        t,
        todayMinutes: 10,
        dailyGoalMinutes: 20,
        goalProgress: 0.5,
        streakDays: 3,
        isLoadingStreak: false,
      },
    });

    const grid = container.querySelector('[data-testid="stats-grid"]');
    expect(grid).not.toBeNull();

    for (const label of ['Started', 'Completed', 'Daily goal', 'Sessions', 'Streak']) {
      expect(grid).toHaveTextContent(label);
    }

    const text = grid?.textContent ?? '';
    const labels = ['Started', 'Completed', 'Daily goal', 'Sessions', 'Streak'];
    const indexes = labels.map((label) => text.indexOf(label));
    expect(indexes.every((index) => index >= 0)).toBe(true);
    expect(indexes).toEqual([...indexes].sort((a, b) => a - b));

    expect(grid).toHaveTextContent('4');
    expect(grid).toHaveTextContent('2');
    expect(grid).toHaveTextContent('10/20 min');
    expect(grid).toHaveTextContent('7');
    expect(grid).toHaveTextContent('3 days');
  });

  it('shows a progress bar on the daily goal card with the goal progress', () => {
    const { container } = render(HomeStatsGrid, {
      props: {
        stats,
        t,
        todayMinutes: 10,
        dailyGoalMinutes: 20,
        goalProgress: 0.5,
        streakDays: 0,
      },
    });

    const bar = container.querySelector('[role="progressbar"]');
    expect(bar).not.toBeNull();
    expect(bar).toHaveAttribute('aria-valuenow', '50');
  });

  it('shows an em dash for the streak while the streak is loading', () => {
    const { container } = render(HomeStatsGrid, {
      props: { stats, t, streakDays: 3, isLoadingStreak: true },
    });

    expect(container.querySelector('[data-testid="stats-grid"]')).toHaveTextContent('—');
    expect(container.querySelector('[data-testid="stats-grid"]')).not.toHaveTextContent('3 days');
  });

  it('shows skeletons instead of values while loading', () => {
    const { container } = render(HomeStatsGrid, {
      props: { stats, t, isLoading: true },
    });

    const grid = container.querySelector('[data-testid="stats-grid"]');
    expect(grid?.querySelectorAll('.animate-pulse').length).toBe(5);
    expect(grid).not.toHaveTextContent('4');
  });

  it('renders the disabled reason panel instead of the cards', () => {
    const { container } = render(HomeStatsGrid, {
      props: { stats, t, disabledReason: 'Stats unavailable right now.' },
    });

    expect(screen.getByText('Stats unavailable right now.')).toBeInTheDocument();
    expect(container.querySelector('[data-testid="stats-grid"]')).toBeNull();
  });

  it('contains no hardcoded hex colors', () => {
    const { container } = render(HomeStatsGrid, {
      props: { stats, t, todayMinutes: 10, dailyGoalMinutes: 20, goalProgress: 0.5 },
    });

    expect(container.innerHTML).not.toMatch(hexPattern);
  });
});
