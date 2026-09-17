/**
 * Discover trending-chip taxonomy + client-side chip filtering.
 *
 * Extracted from `DiscoverDomainState.svelte.ts` so the thematic rail rotation
 * derives its term set from these very tables: `CHIP_KEYWORDS` stays the single
 * source of truth for label -> English search-term mapping.
 */
import type { CatalogBook } from '$lib/shared/services/catalog';

/** Seven static chips; selection filters loaded rails client-side only (no catalog call). */
export const TRENDING_CHIPS: readonly string[] = [
  'Ficción',
  'Clásicos',
  'Aventura',
  'Misterio',
  'Romance',
  'Ciencia ficción',
  'Historia',
];

/**
 * Spanish chip label → English subject keywords. Catalog subjects arrive in
 * English (Gutendex/Open Library), so a normalized substring check alone
 * would miss (`ficción` vs `fiction`); keywords bridge the locale gap.
 * Pure client-side filter — never triggers a catalog call.
 */
export const CHIP_KEYWORDS: Record<string, readonly string[]> = {
  Ficción: ['fiction'],
  Clásicos: ['classic'],
  Aventura: ['adventure'],
  Misterio: ['mystery', 'detective'],
  Romance: ['romance', 'love'],
  'Ciencia ficción': ['science'],
  Historia: ['history'],
};

function normalizeHaystack(value: string): string {
  return value.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
}

/** True when a book matches a trending chip (label substring or keyword hit). */
export function matchesChip(book: CatalogBook, chip: string): boolean {
  const haystack = normalizeHaystack(
    `${book.title} ${book.authors.join(' ')} ${book.subjects.join(' ')}`,
  );
  const needle = normalizeHaystack(chip);
  if (needle !== '' && haystack.includes(needle)) return true;
  const keywords = CHIP_KEYWORDS[chip] ?? [];
  return keywords.some((keyword) => haystack.includes(keyword));
}

/** Client-side rail filter; `null` chip returns the slice untouched. */
export function filterBooksByChip(books: CatalogBook[], chip: string | null): CatalogBook[] {
  if (chip === null) return books;
  return books.filter((book) => matchesChip(book, chip));
}
