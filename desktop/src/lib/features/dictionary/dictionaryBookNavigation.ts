import type { DictionaryWordDto, ReaderBook } from '$lib/shared/types';

/**
 * Where `Ver libro` sends the reader: the source book plus the passage locator
 * captured with the entry, when there is one.
 */
export type DictionaryBookTarget = {
  bookId: string;
  /** The entry's `sourceLocator` (an EPUB CFI) or null. */
  locator: string | null;
};

/**
 * The reader-navigation surface `Ver libro` drives. It is explicit so the
 * sequence stays testable without the domain singletons; production binds it to
 * `libraryState` / `readerState` / `navigationState` / `searchState` / `statsState`
 * in `DictionaryView`.
 *
 * The shape mirrors the highlights screen's "view in book" action
 * (`HighlightsView.handleViewInBook`), which is the app's existing
 * screen-to-reader-at-a-position path.
 */
export type DictionaryBookNavigationDeps = {
  getBookById: (bookId: string) => ReaderBook | null;
  promoteBookForReading: (bookId: string) => void;
  setActiveReadingBookId: (bookId: string) => void;
  clearShelfDetails: () => void;
  openReader: () => void;
  resetSearch: () => void;
  recordReaderOpenMetric: (format: string) => void;
  startReading: (book: ReaderBook) => Promise<void>;
  loadStats: (bookId: string) => void;
  setSearchTargetLocator: (locator: string | null) => void;
};

/**
 * The navigation target an entry can offer, or null when it has no usable
 * source book. An entry captured by hand, or one whose book was deleted, has
 * no `sourceBookId`, so the action must not fire with an empty id.
 */
export function resolveBookTarget(
  entry: Pick<DictionaryWordDto, 'sourceBookId' | 'sourceLocator'> | null,
): DictionaryBookTarget | null {
  const bookId = entry?.sourceBookId?.trim();
  if (!bookId) return null;
  return { bookId, locator: entry?.sourceLocator?.trim() || null };
}

/**
 * Opens the entry's source book in the reader, at the captured passage when the
 * entry carries a locator. A missing target, or a book no longer in the library,
 * is a no-op rather than a navigation into a dead id.
 *
 * The step order mirrors `HighlightsView.handleViewInBook`: the target locator
 * is set last, after `startReading`, so the reader picks it up for the freshly
 * opened book instead of resetting it during start.
 */
export async function openDictionaryBook(
  target: DictionaryBookTarget | null,
  deps: DictionaryBookNavigationDeps,
): Promise<void> {
  if (!target) return;
  const book = deps.getBookById(target.bookId);
  if (!book) return;
  deps.promoteBookForReading(book.id);
  deps.setActiveReadingBookId(book.id);
  deps.clearShelfDetails();
  deps.openReader();
  deps.resetSearch();
  deps.recordReaderOpenMetric(book.format);
  await deps.startReading(book);
  deps.loadStats(book.id);
  deps.setSearchTargetLocator(target.locator);
}
