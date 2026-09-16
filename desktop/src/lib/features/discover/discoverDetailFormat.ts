import {
  BUILTIN_GUTENDEX,
  BUILTIN_OPENLIBRARY,
  type CatalogBook,
} from '$lib/shared/services/catalog';

/** Off-app destination derivable from fields already present on the model. */
export interface DiscoverExternalLink {
  url: string;
  kind: 'gutenberg' | 'openlibrary';
}

const GUTENDEX_PREFIX = 'gutendex:';
const OPENLIBRARY_PREFIX = 'openlibrary:';

/**
 * Stable display order for the formats chip row. `text/plain` (no charset)
 * is treated as TXT alongside the charset-qualified variant because live
 * Gutendex payloads use the bare key.
 */
const FORMAT_LABELS: ReadonlyArray<readonly [string, string]> = [
  ['application/epub+zip', 'EPUB'],
  ['application/x-mobipocket-ebook', 'MOBI'],
  ['application/pdf', 'PDF'],
  ['text/plain; charset=utf-8', 'TXT'],
  ['text/plain', 'TXT'],
  ['text/html', 'HTML'],
];

/**
 * Chip labels derived from the real `formats` map keys, in stable display
 * order. Unknown keys are skipped; an empty/undefined map yields an empty
 * list so the formats row hides.
 */
export function discoverFormatLabels(formats: Record<string, string> | undefined): string[] {
  if (!formats) return [];
  const labels: string[] = [];
  for (const [mime, label] of FORMAT_LABELS) {
    if (Object.prototype.hasOwnProperty.call(formats, mime) && !labels.includes(label)) {
      labels.push(label);
    }
  }
  return labels;
}

/**
 * Off-app URL for first-party books only. Returns null for anything that is
 * not a resolvable Gutenberg/Open Library id (Google Books, curated, and
 * registry entries included), which hides the external-link CTA.
 */
export function discoverExternalLink(book: CatalogBook): DiscoverExternalLink | null {
  if (book.provider === BUILTIN_GUTENDEX) {
    if (!book.id.startsWith(GUTENDEX_PREFIX)) return null;
    const numericId = Number(book.id.slice(GUTENDEX_PREFIX.length));
    if (!Number.isInteger(numericId) || numericId <= 0) return null;
    return { url: `https://www.gutenberg.org/ebooks/${numericId}`, kind: 'gutenberg' };
  }
  if (book.provider === BUILTIN_OPENLIBRARY) {
    if (!book.id.startsWith(OPENLIBRARY_PREFIX)) return null;
    const key = book.id.slice(OPENLIBRARY_PREFIX.length);
    if (!key.startsWith('/')) return null;
    return { url: `https://openlibrary.org${key}`, kind: 'openlibrary' };
  }
  return null;
}

/**
 * Description text for the detail block. Null/blank hides the block rather
 * than showing a placeholder paragraph.
 */
export function discoverDescription(book: CatalogBook): string | undefined {
  const text = book.description;
  if (text === null || text === undefined || text.trim() === '') return undefined;
  return text;
}
