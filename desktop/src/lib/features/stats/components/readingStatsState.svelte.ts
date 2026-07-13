import type { LibraryBookDto } from '$lib/types';
import { UNCLASSIFIED_GENRE, type CanonicalGenre } from '$lib/shared/services/genreHeuristic';
import type { AppState } from '$lib/shared/stores/AppState.svelte';

export type StatsBook = LibraryBookDto;

export type PeriodKey = 'week' | 'month' | 'year' | 'all';
export type Granularity = 'day' | 'week' | 'month';
export type GenreKey = CanonicalGenre;

export type Props = {
  appState: AppState;
};

export const periodLabels: Record<PeriodKey, string> = {
  week: 'Esta semana',
  month: 'Este mes',
  year: 'Este año',
  all: 'Todo el tiempo',
};

export const GENRE_COLORS = [
  '#4e8cff', '#43d3c4', '#f4b942', '#ff6b6b',
  '#9d59ff', '#ff9f43', '#2ed573', '#a29bfe',
  '#fd79a8', '#00cec9', '#e17055', '#6c5ce7',
] as const;

export function hashNumber(value: string): number {
  let hash = 0;
  for (const char of value) {
    hash = (hash * 31 + char.charCodeAt(0)) % 997;
  }
  return hash;
}

const resolveGenre = (book: StatsBook): string => {
  const value = book.genre;
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }
  return UNCLASSIFIED_GENRE;
};

export function groupBooksByGenre(books: StatsBook[]): Map<string, number> {
  const groups = new Map<string, number>();
  for (const book of books) {
    const minutes = Math.max(book.minutesRead, 10);
    const key = resolveGenre(book);
    groups.set(key, (groups.get(key) ?? 0) + minutes);
  }
  return groups;
}

export function periodWindow(period: PeriodKey): { from: string; to: string } {
  const now = new Date();
  const to = now.toISOString();
  let from: Date;
  switch (period) {
    case 'week':
      from = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      break;
    case 'month':
      from = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      break;
    case 'year':
      from = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);
      break;
    case 'all':
      return { from: '1970-01-01T00:00:00Z', to };
  }
  return { from: from.toISOString(), to };
}

export function previousWindow(period: PeriodKey): { from: string; to: string } {
  const now = new Date();
  let length: number;
  switch (period) {
    case 'week':
      length = 7;
      break;
    case 'month':
      length = 30;
      break;
    case 'year':
      length = 365;
      break;
    case 'all':
      return { from: '1970-01-01T00:00:00Z', to: now.toISOString() };
  }
  const windowMs = length * 24 * 60 * 60 * 1000;
  const to = new Date(now.getTime() - windowMs);
  const from = new Date(to.getTime() - windowMs);
  return { from: from.toISOString(), to: to.toISOString() };
}

export function computeDelta(current: number, previous: number): number | null {
  if (previous <= 0) return null;
  return Math.round(((current - previous) / previous) * 100 * 10) / 10;
}

export const periodDeltaLabels: Record<PeriodKey, string> = {
  week: 'vs. semana anterior',
  month: 'vs. mes anterior',
  year: 'vs. año anterior',
  all: 'vs. periodo anterior',
};

export function calculateGenreDistribution(
  books: StatsBook[],
): Array<{ genre: string; minutes: number; percent: number; color: string }> {
  const groups = groupBooksByGenre(books);
  const total = Array.from(groups.values()).reduce((sum, value) => sum + value, 0);

  return Array.from(groups.entries())
    .sort(([, leftMin], [, rightMin]) => rightMin - leftMin)
    .map(([genre, minutes], index) => ({
      genre,
      minutes,
      percent: total > 0 ? Math.round((minutes / total) * 100) : 0,
      color: GENRE_COLORS[index % GENRE_COLORS.length],
    }));
}
