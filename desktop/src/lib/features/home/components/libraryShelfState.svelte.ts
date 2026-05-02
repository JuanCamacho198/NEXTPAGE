import type { LibraryBookDto } from "$lib/shared/types";

export type ShelfBook = LibraryBookDto & {
  filePath: string;
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
};

export type ShelfFilter = "all" | "reading" | "pending" | "completed" | "favorites";
export type ShelfSort = "date_added" | "last_read" | "progress" | "title";
export type ShelfView = "grid" | "list";

export const FILTER_OPTIONS: Array<{ key: ShelfFilter; label: string }> = [
  { key: "all", label: "Todos" },
  { key: "reading", label: "Leyendo" },
  { key: "pending", label: "Pendientes" },
  { key: "completed", label: "Completados" },
  { key: "favorites", label: "Favoritos" },
];

export const SORT_OPTIONS: Array<{ key: ShelfSort; label: string }> = [
  { key: "date_added", label: "Fecha agregada" },
  { key: "last_read", label: "Ultima lectura" },
  { key: "progress", label: "Progreso" },
  { key: "title", label: "Titulo" },
];

export function getSafeProgressPercentage(book: LibraryBookDto): number {
  const progress = book.progressPercentage;
  const total = book.totalPages;

  if (!total || total <= 0) {
    return 0;
  }

  if (!progress || progress < 0) {
    return 0;
  }

  return Math.min(100, progress);
}

export function getBookState(book: ShelfBook): ShelfFilter {
  const progress = getSafeProgressPercentage(book);

  if (book.completed || progress >= 100) {
    return "completed";
  }

  if (progress > 0) {
    return "reading";
  }

  if (book.isFavorite) {
    return "favorites";
  }

  return "pending";
}

export function getStateLabel(book: ShelfBook): string {
  const progress = getSafeProgressPercentage(book);

  if (book.completed || progress >= 100) {
    return "Completado";
  }

  if (progress > 0) {
    return "En lectura";
  }

  if (book.isFavorite) {
    return "Favorito";
  }

  return "Pendiente";
}

export function getTimestamp(book: ShelfBook): number {
  const parsed = Date.parse(book.updatedAt);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function formatPercent(book: ShelfBook): string {
  return `${Math.round(getSafeProgressPercentage(book))}%`;
}
