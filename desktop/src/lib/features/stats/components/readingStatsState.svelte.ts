import type { LibraryBookDto, ReadingStatsSummaryDto } from '$lib/types';
import { UNCLASSIFIED_GENRE, type CanonicalGenre } from '$lib/shared/services/genreHeuristic';

export type StatsBook = LibraryBookDto & {
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
};

export type PeriodKey = 'week' | 'month' | 'year' | 'all';
export type Granularity = 'day' | 'week' | 'month';
export type GenreKey = CanonicalGenre;

export type Props = {
  books: StatsBook[];
  stats: ReadingStatsSummaryDto | null;
  isLoading?: boolean;
  disabledReason?: string | null;
};

export const periodLabels: Record<PeriodKey, string> = {
  week: 'Esta semana',
  month: 'Este mes',
  year: 'Este año',
  all: 'Todo el tiempo',
};

export const GENRE_COLORS = ['#43d3c4', '#f4b942', '#4d86ff', '#9d59ff', '#ff6b6b'] as const;

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

export function calculateGenreDistribution(
  books: StatsBook[],
): Array<{ genre: string; minutes: number; percent: number; color: string }> {
  const groups = groupBooksByGenre(books);
  const total = Array.from(groups.values()).reduce((sum, value) => sum + value, 0);

  return Array.from(groups.entries())
    .sort(([left], [right]) => left.localeCompare(right, 'es'))
    .map(([genre, minutes], index) => ({
      genre,
      minutes,
      percent: total > 0 ? Math.round((minutes / total) * 100) : 0,
      color: GENRE_COLORS[index % GENRE_COLORS.length],
    }));
}
