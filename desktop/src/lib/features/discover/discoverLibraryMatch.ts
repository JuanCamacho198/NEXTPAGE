import type { ReaderBook } from '$lib/shared/types';

/**
 * The library row that satisfies Discover's "already in your library" state.
 *
 * Discover reuses the catalog id as the library id, so the match is exact. A
 * visible library row can still have an empty `filePath` when the stored file is
 * missing; such a row must not flip the detail to the in-library state, because
 * "Open book" would point at a file that cannot open and the Download CTA would
 * be hidden. Only a usable, non-empty path counts.
 *
 * Scope note: this is a path-presence guard, not a filesystem check, so it does
 * not detect a file deleted after the library row was loaded.
 */
export function openableLibraryBook(book: ReaderBook | null | undefined): ReaderBook | null {
  if (!book) return null;
  return book.filePath.trim().length > 0 ? book : null;
}
