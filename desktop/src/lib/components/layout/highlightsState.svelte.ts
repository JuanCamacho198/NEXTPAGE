import type { HighlightDto, LibraryBookDto } from "$lib/types";
import type { MessageKey } from "$lib/i18n";

export type Props = {
  books: LibraryBookDto[];
  t: (key: MessageKey, params?: Record<string, string | number>) => string;
};

export const PAGE_SIZE = 6;

export const HIGHLIGHT_COLORS = [
  { key: "yellow", hex: "#facc15" },
  { key: "green", hex: "#4ade80" },
  { key: "blue", hex: "#60a5fa" },
  { key: "purple", hex: "#c084fc" },
  { key: "pink", hex: "#f472b6" },
  { key: "orange", hex: "#fb923c" },
] as const;

export type HighlightColorKey = (typeof HIGHLIGHT_COLORS)[number]["key"];

export function formatDate(iso: string): string {
  const d = new Date(iso);
  return (
    d.toLocaleDateString("es-ES", {
      day: "numeric",
      month: "short",
      year: "numeric",
    }) +
    " — " +
    d.toLocaleTimeString("es-ES", {
      hour: "numeric",
      minute: "2-digit",
      hour12: true,
    })
  );
}

export function getDateCutoff(selectedDateRange: string | null): Date {
  const now = new Date();
  if (selectedDateRange === "7d") return new Date(now.getTime() - 7 * 86400000);
  if (selectedDateRange === "30d") return new Date(now.getTime() - 30 * 86400000);
  if (selectedDateRange === "90d") return new Date(now.getTime() - 90 * 86400000);
  return new Date(0);
}

export function filterHighlights(
  highlights: HighlightDto[],
  searchQuery: string,
  selectedColor: string | null,
  selectedBookId: string | null,
  selectedDateRange: string | null,
  bookMap: Map<string, LibraryBookDto>
): HighlightDto[] {
  let result = highlights;

  if (searchQuery.trim().length > 0) {
    const q = searchQuery.toLowerCase();
    result = result.filter((h) => {
      const book = bookMap.get(h.bookId);
      return (
        h.text.toLowerCase().includes(q) ||
        (h.note && h.note.toLowerCase().includes(q)) ||
        (book && book.title.toLowerCase().includes(q)) ||
        (book && book.author.toLowerCase().includes(q))
      );
    });
  }

  if (selectedColor) {
    result = result.filter((h) => h.color.toLowerCase() === selectedColor);
  }

  if (selectedBookId) {
    result = result.filter((h) => h.bookId === selectedBookId);
  }

  if (selectedDateRange) {
    const cutoff = getDateCutoff(selectedDateRange);
    result = result.filter((h) => new Date(h.createdAt) >= cutoff);
  }

  return result;
}