import type { LibraryBookDto, ReadingStatsSummaryDto } from "$lib/types";

export type StatsBook = LibraryBookDto & {
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
};

export type PeriodKey = "week" | "month" | "year" | "all";
export type Granularity = "day" | "week" | "month";
export type GenreKey = "Desarrollo personal" | "Productividad" | "Finanzas" | "Ficcion" | "Otros";

export type Props = {
  books: StatsBook[];
  stats: ReadingStatsSummaryDto | null;
  isLoading?: boolean;
  disabledReason?: string | null;
};

export const periodLabels: Record<PeriodKey, string> = {
  week: "Esta semana",
  month: "Este mes",
  year: "Este año",
  all: "Todo el tiempo",
};

export const GENRE_COLORS = ["#43d3c4", "#f4b942", "#4d86ff", "#9d59ff", "#ff6b6b"] as const;

export function hashNumber(value: string): number {
  let hash = 0;
  for (const char of value) {
    hash = (hash * 31 + char.charCodeAt(0)) % 997;
  }
  return hash;
}

export function inferGenre(book: StatsBook): GenreKey {
  const text = `${book.title} ${book.author}`.toLowerCase();
  if (/(habitos|mindset|vida|feliz|atomic|self|mejora)/.test(text)) {
    return "Desarrollo personal";
  }
  if (/(deep work|productividad|eficacia|principios|efectiva|work)/.test(text)) {
    return "Productividad";
  }
  if (/(rico|finanza|money|wealth|inversion)/.test(text)) {
    return "Finanzas";
  }
  if (/(novela|cuento|fiction|ficcion)/.test(text)) {
    return "Ficcion";
  }
  return "Otros";
}

export function calculateGenreDistribution(
  books: StatsBook[]
): Array<{ genre: GenreKey; minutes: number; percent: number; color: string }> {
  const buckets: Record<GenreKey, number> = {
    "Desarrollo personal": 0,
    Productividad: 0,
    Finanzas: 0,
    Ficcion: 0,
    Otros: 0,
  };

  for (const book of books) {
    const minutes = Math.max(book.minutesRead, 10);
    buckets[inferGenre(book)] += minutes;
  }

  const total = Object.values(buckets).reduce((sum, value) => sum + value, 0);
  return Object.entries(buckets).map(([genre, minutes], index) => ({
    genre: genre as GenreKey,
    minutes,
    percent: total > 0 ? Math.round((minutes / total) * 100) : 0,
    color: GENRE_COLORS[index],
  }));
}