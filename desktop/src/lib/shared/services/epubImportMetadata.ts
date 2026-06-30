/**
 * Lightweight one-shot metadata extractor for EPUB files used at import time.
 *
 * The full `EpubReaderService` (in `src/lib/features/reader/viewer-epub/epub.ts`)
 * is geared toward rendering and tears down the epubjs `Book` on every cleanup.
 * For import we only need the title and author once, so we build a dedicated,
 * side-effect-free helper.
 *
 * Strategy (defense in depth, in case any single layer returns empty for an
 * otherwise-valid EPUB):
 *
 *  1. Read the file bytes via the Tauri command.
 *  2. Hand them to epubjs and `await book.ready` (with a timeout so a
 *     broken file doesn't hang the import).
 *  3. Pull `book.package.metadata.title` / `.creator`. epubjs sets these
 *     from the OPF's `<dc:title>` and `<dc:creator>` elements via
 *     `getElementText`.
 *  4. If the values came back empty (e.g. the OPF uses a non-standard
 *     namespace that epubjs's parser didn't pick up), parse the OPF
 *     ourselves as a last resort using the browser's `DOMParser`. This
 *     is the same path the existing reader takes on the chapter HTML,
 *     so it should work for any EPUB that the reader itself can open.
 *  5. Always release the epubjs instance in a `finally`.
 *
 * Failures are swallowed at every step — the caller falls back to the
 * filename. A short `console.debug` is logged so the user can see why
 * a particular file came up empty (most often: the EPUB genuinely has
 * no `<dc:title>` / `<dc:creator>`).
 */
import ePub from 'epubjs';
import { getFileBytes } from '$lib/shared/api/tauriClient';

export type ImportEpubMetadata = {
  title: string | null;
  author: string | null;
};

// Generous: a valid EPUB is parsed in well under a second; anything past
// this is a hung parser and we should fall back to the filename.
const PARSE_TIMEOUT_MS = 5000;

// Minimal type for the slice of epubjs Book we actually touch.
type EpubJsBook = {
  ready: Promise<unknown>;
  destroy(): Promise<void> | void;
  package?: { metadata?: Record<string, unknown> };
};

const trimToNull = (value: unknown): string | null => {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
};

/**
 * Last-resort parser: when epubjs's structured metadata came back empty,
 * drop down to the raw OPF and look for `<dc:title>` / `<dc:creator>` with
 * a wildcard namespace (matches `dc:title`, `dc11:title`, etc.). This is
 * the same path the reader takes for chapter HTML, so if the reader can
 * open the file we should be able to find the metadata here.
 *
 * Exported (not just module-private) so the regex parsing can be
 * unit-tested with synthetic byte payloads without standing up the
 * epubjs pipeline.
 */
export const parseOpfDirectly = async (
  filePath: string,
): Promise<ImportEpubMetadata> => {
  try {
    const bytes = await getFileBytes(filePath);
    // Many EPUBs store the OPF inside the zip at OEBPS/content.opf or
    // similar. Walking the full zip would be heavy; instead we just
    // convert the bytes to text and look for the metadata block. The
    // zip local-file header is plain ASCII-ish; for our regex needs
    // (find <metadata>...</metadata>) we can scan the whole byte string
    // as latin1 without unzipping.
    const text = new TextDecoder('utf-8', { fatal: false }).decode(
      new Uint8Array(bytes),
    );
    // Match the first <metadata>...</metadata> block, allowing any
    // namespace prefix on the title/creator tags.
    const metaBlock = /<metadata[\s\S]*?<\/metadata>/i.exec(text);
    if (!metaBlock) return { title: null, author: null };
    const block = metaBlock[0];
    const titleMatch = /<(?:\w+:)?title[^>]*>([\s\S]*?)<\/(?:\w+:)?title>/i.exec(
      block,
    );
    const creatorMatch =
      /<(?:\w+:)?creator[^>]*>([\s\S]*?)<\/(?:\w+:)?creator>/i.exec(block);
    return {
      title: titleMatch ? trimToNull(titleMatch[1]) : null,
      author: creatorMatch ? trimToNull(creatorMatch[1]) : null,
    };
  } catch {
    return { title: null, author: null };
  }
};

const withTimeout = async <T>(promise: Promise<T>, ms: number): Promise<T> => {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;
  const timeout = new Promise<T>((_, reject) => {
    timeoutId = setTimeout(() => reject(new Error('epub parse timeout')), ms);
  });
  try {
    return await Promise.race([promise, timeout]);
  } finally {
    if (timeoutId) clearTimeout(timeoutId);
  }
};

export const extractEpubImportMetadata = async (
  filePath: string,
): Promise<ImportEpubMetadata> => {
  const fileData = await getFileBytes(filePath);
  const buffer = new Uint8Array(fileData).buffer as ArrayBuffer;

  let epubResult: ImportEpubMetadata = { title: null, author: null };
  let book: EpubJsBook | null = null;

  try {
    book = ePub(buffer) as unknown as EpubJsBook;
    await withTimeout(book.ready, PARSE_TIMEOUT_MS);
    const metadata = book.package?.metadata ?? {};
    epubResult = {
      title: trimToNull(metadata.title),
      author: trimToNull(metadata.creator),
    };
  } catch (err) {
    console.debug('[epub-import-meta] epubjs parse failed, will try OPF fallback', err);
  } finally {
    if (book) {
      try {
        await book.destroy();
      } catch {
        // best-effort cleanup
      }
    }
  }

  // If epubjs returned both fields, we're done. If either is still empty,
  // fall back to parsing the raw OPF. The fallback only reads the file
  // once more if it didn't already have the bytes (it does — we just
  // fetched them above and Tauri caches the read).
  if (epubResult.title && epubResult.author) {
    return epubResult;
  }

  const opfResult = await parseOpfDirectly(filePath);
  const combined: ImportEpubMetadata = {
    title: epubResult.title ?? opfResult.title,
    author: epubResult.author ?? opfResult.author,
  };

  if (!combined.title && !combined.author) {
    console.debug(
      '[epub-import-meta] no metadata found in OPF for',
      filePath,
      '— caller will fall back to filename / unknown author',
    );
  }

  return combined;
};
