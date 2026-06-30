/**
 * Lightweight one-shot metadata extractor for EPUB files used at import time.
 *
 * The full `EpubReaderService` (in `src/lib/features/reader/viewer-epub/epub.ts`)
 * is geared toward rendering and tears down the epubjs `Book` on every cleanup.
 * For import we only need the title (and optionally author) once, so we build
 * a dedicated, side-effect-free helper that:
 *  1. reads the file bytes via the Tauri command,
 *  2. hands them to epubjs,
 *  3. waits for `ready`,
 *  4. pulls the OPF metadata,
 *  5. destroys the book.
 *
 * Failures are swallowed — the caller falls back to the filename.
 */
import ePub from 'epubjs';
import { getFileBytes } from '$lib/shared/api/tauriClient';

export type ImportEpubMetadata = {
  title: string | null;
  author: string | null;
};

export const extractEpubImportMetadata = async (
  filePath: string,
): Promise<ImportEpubMetadata> => {
  const fileData = await getFileBytes(filePath);
  const buffer = new Uint8Array(fileData).buffer as ArrayBuffer;

  // `as any` because the typed `Book` surface doesn't expose `package.metadata`
  // directly — it's the OPF document that epubjs parses internally.
  const book = ePub(buffer) as unknown as {
    ready: Promise<unknown>;
    destroy(): Promise<void> | void;
    package?: { metadata?: { title?: unknown; creator?: unknown } };
  };

  try {
    await book.ready;
    const metadata = book.package?.metadata ?? {};
    const rawTitle = metadata.title;
    const rawAuthor = metadata.creator;

    return {
      title: typeof rawTitle === 'string' && rawTitle.trim().length > 0
        ? rawTitle.trim()
        : null,
      author: typeof rawAuthor === 'string' && rawAuthor.trim().length > 0
        ? rawAuthor.trim()
        : null,
    };
  } finally {
    // Always release the epubjs instance — we only need the metadata, not
    // a live rendition. Without this, each import leaks a worker handle.
    try {
      await book.destroy();
    } catch {
      // best-effort cleanup
    }
  }
};
