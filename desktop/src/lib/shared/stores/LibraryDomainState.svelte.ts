import {
  listLibraryBooks,
  listBooks,
  listCollections,
  addBookToCollection,
  removeBookFromCollection,
  setReadingStatus,
  upsertBook,
  upsertBookCover,
  hideBookFromLibrary,
  extractEpubCover,
  deleteBookCover,
} from '$lib/shared/api/tauriClient';
import { extractPdfMetadata } from '$lib/shared/services/pdfThumbnail';
import { recordMetric } from '$lib/shared/logger/MetricsStore';
import { METRIC_NAMES } from '$lib/shared/logger/metricTypes';
import {
  createShelfQueryState,
  updateShelfQueryState,
  partitionHomeBooks,
  selectShelfBooks,
  getShelfQueryWarnings,
  promoteBookForReading,
} from '$lib/shared/stores/homeState';
import type { BookDto, CollectionDto, LibraryBookDto, ReaderBook } from '$lib/shared/types';

type MaybeCommandError = Error & {
  commandError?: { code: string; message: string; recoverable: boolean };
};

class LibraryDomainState {
  // ─── State ───
  books = $state<ReaderBook[]>([]);
  shelfQueryState = $state(createShelfQueryState(''));
  collections = $state<CollectionDto[]>([]);
  isLoadingLibrary = $state(false);
  readerError = $state<string | null>(null);
  editingBook = $state<ReaderBook | null>(null);
  isCollectionManagerOpen = $state(false);

  // Internal state (class properties, not reactive)
  thumbnailGenerationInFlight = new Set<string>();
  thumbnailGenerationAttempted = new Set<string>();

  // ─── $derived ───
  continueReadingBooks = $derived.by(() => partitionHomeBooks(this.books).continueReadingBooks);
  myShelfBooks = $derived.by(() => partitionHomeBooks(this.books).myShelfBooks);
  shelfBooks = $derived.by(() => selectShelfBooks(this.myShelfBooks, this.shelfQueryState));
  shelfWarnings = $derived.by(() => getShelfQueryWarnings(this.shelfQueryState));
  shelfSortToken = $derived.by(() => {
    for (let index = this.shelfQueryState.smartTokens.length - 1; index >= 0; index -= 1) {
      const token = this.shelfQueryState.smartTokens[index];
      if (token.field === 'sort') {
        return token.value;
      }
    }
    return null;
  });

  // ─── Constants ───
  readonly SHELF_TAB_OPTIONS = [
    { key: 'all', label: 'home.shelfTab.all' },
    { key: 'favorites', label: 'home.shelfTab.favorites' },
    { key: 'to_read', label: 'home.shelfTab.toRead' },
    { key: 'completed', label: 'home.shelfTab.completed' },
  ] as const;

  readonly SHELF_SORT_OPTIONS = [
    { key: 'progress', label: 'home.shelfSort.progress' },
    { key: 'date', label: 'home.shelfSort.date' },
    { key: 'last_read', label: 'home.shelfSort.lastRead' },
    { key: 'author', label: 'home.shelfSort.author' },
    { key: 'title', label: 'home.shelfSort.title' },
    { key: 'file_size', label: 'home.shelfSort.fileSize' },
  ] as const;

  // ─── Utility ───

  getBookById(bookId: string | null): ReaderBook | null {
    if (!bookId) return null;
    return this.books.find((book) => book.id === bookId) ?? null;
  }

  hasResolvedCoverPath(book: Pick<LibraryBookDto, 'coverPath'>): boolean {
    return typeof book.coverPath === 'string' && book.coverPath.trim().length > 0;
  }

  shouldGeneratePdfCover(book: ReaderBook): boolean {
    if (book.format.toLowerCase() !== 'pdf') return false;
    if (this.hasResolvedCoverPath(book)) return false;
    return book.filePath.trim().length > 0;
  }

  shouldGenerateEpubCover(book: ReaderBook): boolean {
    if (book.format.toLowerCase() !== 'epub') return false;
    if (this.hasResolvedCoverPath(book)) return false;
    if (book.coverUserDeleted) return false;
    return book.filePath.trim().length > 0;
  }

  // ─── Thumbnail generation ───

  async ensureEpubCover(book: ReaderBook): Promise<void> {
    if (this.thumbnailGenerationInFlight.has(book.id)) return;
    this.thumbnailGenerationInFlight.add(book.id);
    try {
      const found = await extractEpubCover(book.id, book.filePath);
      if (found) {
        await this.loadLibrary();
      }
    } catch (e) {
      console.error('[Library] ensureEpubCover failed:', e);
    } finally {
      this.thumbnailGenerationInFlight.delete(book.id);
    }
  }

  async ensurePdfCover(book: ReaderBook): Promise<void> {
    if (this.thumbnailGenerationInFlight.has(book.id)) return;
    this.thumbnailGenerationInFlight.add(book.id);
    try {
      const metadata = await extractPdfMetadata(book.filePath);
      if (metadata.thumbnailBytes) {
        await upsertBookCover({
          bookId: book.id,
          data: Array.from(metadata.thumbnailBytes),
          mimeType: 'image/png',
        });
      }

      const needsAuthorUpdate = metadata.author && (!book.author || book.author.trim() === '');
      const needsPagesUpdate = metadata.totalPages && (!book.totalPages || book.totalPages === 0);

      if (needsAuthorUpdate || needsPagesUpdate) {
        await upsertBook({
          id: book.id,
          title: book.title,
          author: metadata.author || book.author || '',
          filePath: book.filePath,
          format: book.format,
          syncStatus: 'local' as const,
          currentPage: book.currentPage,
          totalPages: metadata.totalPages || book.totalPages || 0,
        });
      }

      await this.loadLibrary();
    } catch (e) {
      console.error('[Library] ensurePdfCover failed:', e);
    } finally {
      this.thumbnailGenerationInFlight.delete(book.id);
    }
  }

  // ─── Data loading (pure — fetches data into local state, no cross-domain) ───

  async loadLibrary(): Promise<void> {
    this.isLoadingLibrary = true;
    this.readerError = null;

    try {
      const [libraryRows, sourceRows, loadedCollections] = await Promise.all([
        listLibraryBooks(1),
        listBooks(),
        listCollections(),
      ]);
      this.collections = loadedCollections;

      const filePathById = new Map<string, string>(
        sourceRows.map((book: BookDto) => [book.id, book.filePath]),
      );

      const booksWithCollections = libraryRows.map((entry: LibraryBookDto) => ({
        ...entry,
        filePath: filePathById.get(entry.id) ?? '',
        collectionIds: entry.collectionIds ?? [],
      }));

      this.books = booksWithCollections;

      // Reconcile navigation after books change (delegated to AppState coordinator)
      this._booksJustChanged = true;

      const pendingPdfThumbnails = this.books.filter((book) => {
        if (!this.shouldGeneratePdfCover(book)) return false;
        if (this.thumbnailGenerationAttempted.has(book.id)) return false;
        this.thumbnailGenerationAttempted.add(book.id);
        return true;
      });

      const pendingEpubCovers = this.books.filter((book) => {
        if (!this.shouldGenerateEpubCover(book)) return false;
        if (this.thumbnailGenerationAttempted.has(book.id)) return false;
        this.thumbnailGenerationAttempted.add(book.id);
        return true;
      });

      const THUMBNAIL_CONCURRENCY = 3;

      for (let i = 0; i < pendingPdfThumbnails.length; i += THUMBNAIL_CONCURRENCY) {
        const batch = pendingPdfThumbnails.slice(i, i + THUMBNAIL_CONCURRENCY);
        await Promise.all(batch.map((book) => this.ensurePdfCover(book)));
      }

      for (let i = 0; i < pendingEpubCovers.length; i += THUMBNAIL_CONCURRENCY) {
        const batch = pendingEpubCovers.slice(i, i + THUMBNAIL_CONCURRENCY);
        await Promise.all(batch.map((book) => this.ensureEpubCover(book)));
      }
    } catch (error) {
      const typed = error as MaybeCommandError;
      if (typed.commandError?.recoverable) {
        this._lastRecoverableError = {
          code: typed.commandError.code,
          message: typed.commandError.message,
        };
      } else {
        this.readerError =
          typed.commandError?.message ?? (error instanceof Error ? error.message : 'Unknown error');
      }
    } finally {
      this.isLoadingLibrary = false;
    }
  }

  // Internal flags consumed by AppState coordinator after loadLibrary()
  _booksJustChanged = false;
  _lastRecoverableError: { code: string; message: string } | null = null;

  consumeBooksJustChanged(): boolean {
    const val = this._booksJustChanged;
    this._booksJustChanged = false;
    return val;
  }

  consumeLastRecoverableError(): { code: string; message: string } | null {
    const val = this._lastRecoverableError;
    this._lastRecoverableError = null;
    return val;
  }

  // ─── Book actions ───

  async handleHideBook(book: ReaderBook): Promise<void> {
    try {
      await hideBookFromLibrary(book.id);
      await this.loadLibrary();
    } catch (error) {
      const typed = error as MaybeCommandError;
      this.readerError =
        typed.commandError?.message ?? (error instanceof Error ? error.message : 'Unknown error');
    }
  }

  async handleToggleFavorite(book: ReaderBook): Promise<void> {
    const isFav = book.collectionIds?.includes(1) ?? false;

    try {
      if (isFav) {
        await removeBookFromCollection({ bookId: book.id, collectionId: 1 });
      } else {
        await addBookToCollection({ bookId: book.id, collectionId: 1 });
      }
      await this.loadLibrary();
    } catch (error) {
      const typed = error as MaybeCommandError;
      this.readerError =
        typed.commandError?.message ?? (error instanceof Error ? error.message : 'Unknown error');
    }
  }

  async handleStatusChange(book: ReaderBook, status: 'to_read' | 'reading' | 'completed'): Promise<void> {
    try {
      await setReadingStatus(book.id, status);
      await this.loadLibrary();
    } catch (error) {
      const typed = error as MaybeCommandError;
      this.readerError =
        typed.commandError?.message ?? (error instanceof Error ? error.message : 'Unknown error');
    }
  }

  handleEditBook(book: ReaderBook): void {
    this.editingBook = book;
  }

  async handleDeleteCover(book: ReaderBook): Promise<void> {
    await deleteBookCover(book.id);
    // Update local state: set coverPath to null
    const found = this.books.find((b) => b.id === book.id);
    if (found) {
      found.coverPath = null;
    }
  }

  async handleSaveEditedBook(updatedBook: LibraryBookDto): Promise<void> {
    try {
      const readerBook = this.books.find((b) => b.id === updatedBook.id);
      if (!readerBook) return;

      const normalizedGenre =
        typeof updatedBook.genre === 'string' && updatedBook.genre.trim().length > 0
          ? updatedBook.genre.trim()
          : null;

      await upsertBook({
        id: updatedBook.id,
        title: updatedBook.title,
        author: updatedBook.author || '',
        filePath: readerBook.filePath,
        format: readerBook.format,
        syncStatus: 'local' as const,
        currentPage: readerBook.currentPage,
        totalPages: readerBook.totalPages,
        genre: normalizedGenre,
      });

      this.books = this.books.map((b) =>
        b.id === updatedBook.id
          ? { ...b, title: updatedBook.title, author: updatedBook.author, genre: normalizedGenre }
          : b,
      );

      this.editingBook = null;
    } catch (error) {
      const typed = error as MaybeCommandError;
      this.readerError =
        typed.commandError?.message ?? (error instanceof Error ? error.message : 'Unknown error');
    }
  }

  // ─── Shelf ───

  setShelfTab(tab: (typeof this.SHELF_TAB_OPTIONS)[number]['key']): void {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { tab });
  }

  setShelfSort(sortKey: (typeof this.SHELF_SORT_OPTIONS)[number]['key']): void {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { sortKey });
  }

  setShelfViewMode(viewMode: 'grid' | 'list'): void {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { viewMode });
  }

  handleShelfQueryInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, {
      rawQuery: target.value,
    });
  }

  clearShelfQuery(): void {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { rawQuery: '' });
  }

  // ─── Reading helpers (used by coordinator) ───

  promoteBookForReading(bookId: string): void {
    this.books = promoteBookForReading(this.books, bookId);
  }

  updateBookPage(bookId: string, page: number, total: number): void {
    this.books = this.books.map((book) =>
      book.id === bookId ? { ...book, currentPage: page, totalPages: total } : book,
    );
  }

  recordReaderOpenMetric(format: string): void {
    recordMetric(METRIC_NAMES.READER_OPEN, { feature: format.toLowerCase() });
  }
}

export const libraryState = new LibraryDomainState();
export { LibraryDomainState };
