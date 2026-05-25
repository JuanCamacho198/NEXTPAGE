import { importBook, type ImportProgress } from "$lib/services/BookImportService";
import {
  BulkImportService,
  type BulkImportProgress,
} from "$lib/services/BulkImportService";
import { pickFile, pickFolder } from "$lib/services/FilePicker";
import {
  getDefaultReaderSettings,
  getProgress,
  getReaderSettings,
  hideBookFromLibrary,
  getReadingStats,
  listBooks,
  listLibraryBooks,
  saveProgress,
  saveReadingSession,
  searchBookText,
  upsertBook,
  upsertBookCover,
  updateBookProgress,
  listCollections,
  scanFolder,
  getFileBytes,
} from "$lib/api/tauriClient";
import { i18n, type MessageKey } from "$lib/i18n";
import { recordMetric, METRIC_NAMES } from "$lib/logger/MetricsStore";
import { extractPdfMetadata } from "$lib/services/pdfThumbnail";
import {
  type AppRoute,
  createShelfQueryState,
  getShelfQueryWarnings,
  partitionHomeBooks,
  promoteBookForReading,
  reconcileHomeState,
  getSafeProgressPercentage,
  selectShelfBooks,
  updateShelfQueryState,
} from "$lib/stores/homeState";
import { initTheme } from "$lib/stores/theme";

import type {
  BookDto,
  CommandErrorDto,
  CollectionDto,
  LibraryBookDto,
  ReadingSessionInput,
  ReadingStatsSummaryDto,
  SaveProgressInput,
  ScanFolderResult,
  BulkImportSummary,
  SearchBookTextResponse,
  SearchNavigationTarget,
  UiLocale,
  ReaderSettings,
} from "$lib/types";

type ReaderBook = LibraryBookDto & {
  filePath: string;
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
  shelfStatus?: "all" | "favorites" | "to_read" | "completed";
};

const DOMAIN = {
  LIBRARY: "library",
  STATS: "stats",
  SEARCH: "search",
} as const;

type Domain = (typeof DOMAIN)[keyof typeof DOMAIN];
type MaybeCommandError = Error & { commandError?: CommandErrorDto };

class AppState {
  // ——— 34 $state vars ———
  books = $state<ReaderBook[]>([]);
  shelfQueryState = $state(createShelfQueryState(""));
  route = $state<AppRoute>("home");
  previewBookId = $state<string | null>(null);
  activeReadingBookId = $state<string | null>(null);
  shelfDetailsBookId = $state<string | null>(null);
  libraryUnavailableReason = $state<string | null>(null);
  statsUnavailableReason = $state<string | null>(null);
  searchUnavailableReason = $state<string | null>(null);

  isLoadingLibrary = $state(false);
  isLoadingStats = $state(false);
  isSearching = $state(false);
  isImporting = $state(false);
  importProgress = $state<ImportProgress | null>(null);

  cfiLocation = $state("");
  percentage = $state(0);
  stats = $state<ReadingStatsSummaryDto | null>(null);
  searchResponse = $state<SearchBookTextResponse | null>(null);
  searchTargetLocator = $state<string | null>(null);
  readerError = $state<string | null>(null);
  locale = $state<UiLocale>("es");
  readerSettings = $state<ReaderSettings>(getDefaultReaderSettings());

  editingBook = $state<ReaderBook | null>(null);
  collections = $state<CollectionDto[]>([]);
  isCollectionManagerOpen = $state(false);

  isBulkImportOpen = $state(false);
  isBulkScanning = $state(false);
  isBulkImporting = $state(false);
  bulkImportFolderPath = $state<string | null>(null);
  bulkImportFolderName = $state<string | null>(null);
  bulkScanResult = $state<ScanFolderResult | null>(null);
  bulkScanError = $state<string | null>(null);
  bulkImportProgress = $state<BulkImportProgress | null>(null);
  bulkImportSummary = $state<BulkImportSummary | null>(null);

  // Preloaded file data for instant reader startup
  preloadedBytes = $state<{ filePath: string; data: number[] } | null>(null);

  // Internal state (class properties, not reactive)
  thumbnailGenerationInFlight = new Set<string>();
  thumbnailGenerationAttempted = new Set<string>();
  bulkImportService = new BulkImportService();

  // ——— 6 $derived ———
  continueReadingBooks = $derived.by(() => partitionHomeBooks(this.books).continueReadingBooks);
  myShelfBooks = $derived.by(() => partitionHomeBooks(this.books).myShelfBooks);
  shelfBooks = $derived.by(() => selectShelfBooks(this.myShelfBooks, this.shelfQueryState));
  shelfWarnings = $derived.by(() => getShelfQueryWarnings(this.shelfQueryState));
  shelfSortToken = $derived.by(() => {
    for (let index = this.shelfQueryState.smartTokens.length - 1; index >= 0; index -= 1) {
      const token = this.shelfQueryState.smartTokens[index];
      if (token.field === "sort") {
        return token.value;
      }
    }

    return null;
  });
  selectedShelfBook = $derived.by(() => {
    const selected = this.getBookById(this.shelfDetailsBookId);
    if (!selected) {
      return null;
    }

    return this.myShelfBooks.find((book) => book.id === selected.id) ?? null;
  });

  // ——— Constants ———
  readonly SHELF_TAB_OPTIONS = [
    { key: "all", label: "home.shelfTab.all" },
    { key: "favorites", label: "home.shelfTab.favorites" },
    { key: "to_read", label: "home.shelfTab.toRead" },
    { key: "completed", label: "home.shelfTab.completed" },
  ] as const;

  readonly SHELF_SORT_OPTIONS = [
    { key: "progress", label: "home.shelfSort.progress" },
    { key: "date", label: "home.shelfSort.date" },
    { key: "last_read", label: "home.shelfSort.lastRead" },
    { key: "author", label: "home.shelfSort.author" },
    { key: "title", label: "home.shelfSort.title" },
    { key: "file_size", label: "home.shelfSort.fileSize" },
  ] as const;

  readonly DOMAIN = DOMAIN;

  // ——— i18n helper ———
  t = (key: MessageKey, params?: Record<string, string | number>) => i18n.t(this.locale, key, params);

  // ——— Helper methods ———

  mapCommandError(error: unknown): CommandErrorDto {
    const typed = error as MaybeCommandError;
    if (typed.commandError) {
      return typed.commandError;
    }

    const fallback = error instanceof Error ? error.message : this.t("errors.commandFailure");
    return {
      code: "INTERNAL_ERROR",
      message: fallback,
      recoverable: false,
    } satisfies CommandErrorDto;
  }

  isValidSessionProgressEvent(event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }): boolean {
    if (!event.endedAt || event.durationSeconds <= 0) {
      return false;
    }

    const startedAt = Date.parse(event.startedAt);
    const endedAt = Date.parse(event.endedAt);
    if (!Number.isFinite(startedAt) || !Number.isFinite(endedAt) || endedAt <= startedAt) {
      return false;
    }

    const percentages = [event.startPercentage, event.endPercentage].filter(
      (value): value is number => typeof value === "number",
    );

    return percentages.every((value) => value >= 0 && value <= 100);
  }

  setDomainUnavailable(domain: Domain, reason: string | null): void {
    if (domain === DOMAIN.LIBRARY) {
      this.libraryUnavailableReason = reason;
      return;
    }

    if (domain === DOMAIN.STATS) {
      this.statsUnavailableReason = reason;
      return;
    }

    this.searchUnavailableReason = reason;
  }

  getBookById(bookId: string | null): ReaderBook | null {
    if (!bookId) {
      return null;
    }

    return this.books.find((book) => book.id === bookId) ?? null;
  }

  hasResolvedCoverPath(book: Pick<LibraryBookDto, "coverPath">): boolean {
    return typeof book.coverPath === "string" && book.coverPath.trim().length > 0;
  }

  shouldGeneratePdfCover(book: ReaderBook): boolean {
    if (book.format.toLowerCase() !== "pdf") {
      return false;
    }

    if (this.hasResolvedCoverPath(book)) {
      return false;
    }

    return book.filePath.trim().length > 0;
  }

  async ensurePdfCover(book: ReaderBook): Promise<void> {
    if (this.thumbnailGenerationInFlight.has(book.id)) {
      return;
    }

    this.thumbnailGenerationInFlight.add(book.id);
    try {
      console.log(`[App] Ensuring cover and metadata for book: ${book.title}`);
      const metadata = await extractPdfMetadata(book.filePath);
      console.log(`[App] Extracted metadata:`, {
        hasThumbnail: !!metadata.thumbnailBytes,
        author: metadata.author,
        totalPages: metadata.totalPages,
      });

      if (metadata.thumbnailBytes) {
        await upsertBookCover({
          bookId: book.id,
          data: Array.from(metadata.thumbnailBytes),
          mimeType: "image/png",
        });
      }

      // Update metadata if something was missing
      const needsAuthorUpdate = metadata.author && (!book.author || book.author.trim() === "");
      const needsPagesUpdate = metadata.totalPages && (!book.totalPages || book.totalPages === 0);

      if (needsAuthorUpdate || needsPagesUpdate) {
        console.log(`[App] Updating book metadata in database...`);
        const bookDtoUpdate = {
          id: book.id,
          title: book.title,
          author: metadata.author || book.author || "",
          format: book.format,
          syncStatus: "local" as const,
          currentPage: book.currentPage,
          totalPages: metadata.totalPages || book.totalPages || 0,
        };
        await upsertBook(bookDtoUpdate);
      }

      await this.loadLibrary();
    } catch (e) {
      console.error(`[App] ensurePdfCover failed:`, e);
    } finally {
      this.thumbnailGenerationInFlight.delete(book.id);
    }
  }

  // ——— Navigation ———

  navigateToHome = (): void => {
    this.route = "home";
    this.shelfDetailsBookId = null;
  };

  navigateToLibrary = (): void => {
    this.route = "library";
    this.shelfDetailsBookId = null;
  };

  navigateToStats = (): void => {
    this.route = "stats";
    this.shelfDetailsBookId = null;
  };

  navigateToHighlights = (): void => {
    this.route = "highlights";
    this.shelfDetailsBookId = null;
  };

  navigateToSettings = (): void => {
    this.route = "settings";
    this.shelfDetailsBookId = null;
  };

  backToHome = (): void => {
    this.route = "home";
  };

  // ——— Data loading ———

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

      // Use collectionIds directly from backend (no N+1 needed)
      const booksWithCollections = libraryRows.map((entry: LibraryBookDto) => ({
        ...entry,
        filePath: filePathById.get(entry.id) ?? "",
        collectionIds: entry.collectionIds ?? [],
      }));

      this.books = booksWithCollections;

      const reconciledState = reconcileHomeState(booksWithCollections, {
        route: this.route,
        previewBookId: this.previewBookId,
        activeReadingBookId: this.activeReadingBookId,
        shelfDetailsBookId: this.shelfDetailsBookId,
      });
      this.route = reconciledState.route;
      this.previewBookId = reconciledState.previewBookId;
      this.activeReadingBookId = reconciledState.activeReadingBookId;
      this.shelfDetailsBookId = reconciledState.shelfDetailsBookId;

      const pendingThumbnailBooks = this.books.filter((book) => {
        if (!this.shouldGeneratePdfCover(book)) {
          return false;
        }

        if (this.thumbnailGenerationAttempted.has(book.id)) {
          return false;
        }

        this.thumbnailGenerationAttempted.add(book.id);
        return true;
      });

      const THUMBNAIL_CONCURRENCY = 3;

      this.setDomainUnavailable(DOMAIN.LIBRARY, null);

      // Process thumbnails in parallel batches
      for (let i = 0; i < pendingThumbnailBooks.length; i += THUMBNAIL_CONCURRENCY) {
        const batch = pendingThumbnailBooks.slice(i, i + THUMBNAIL_CONCURRENCY);
        await Promise.all(batch.map((book) => this.ensurePdfCover(book)));
      }
    } catch (error) {
      const details = this.mapCommandError(error);
      if (details.recoverable) {
        this.setDomainUnavailable(DOMAIN.LIBRARY, details.message);
      } else {
        this.readerError = details.message;
      }
    } finally {
      this.isLoadingLibrary = false;
    }
  }

  async loadStats(bookId?: string): Promise<void> {
    this.isLoadingStats = true;

    try {
      this.stats = await getReadingStats(bookId);
      this.setDomainUnavailable(DOMAIN.STATS, null);
    } catch (error) {
      const details = this.mapCommandError(error);
      if (details.recoverable) {
        this.setDomainUnavailable(DOMAIN.STATS, details.message);
      } else {
        this.readerError = details.message;
      }
      this.stats = null;
    } finally {
      this.isLoadingStats = false;
    }
  }

  async loadReaderSettings(): Promise<void> {
    try {
      this.readerSettings = await getReaderSettings();
    } catch {
      this.readerSettings = getDefaultReaderSettings();
    }
  }

  async init(): Promise<void> {
    initTheme();
    // Reset UI state that was previously component-local
    this.route = "home";
    this.shelfDetailsBookId = null;
    this.shelfQueryState = createShelfQueryState();
    this.previewBookId = null;
    this.readerError = null;
    this.isImporting = false;
    this.importProgress = null;

    try {
      const [nextLocale] = await Promise.all([
        i18n.initializeLocale(),
        this.loadReaderSettings(),
        this.loadLibrary(),
        this.loadStats(undefined),
      ]);
      this.locale = nextLocale;
    } catch (error) {
      console.error("Initialization error:", error);
      // Fallback: continue with defaults
      try {
        this.locale = await i18n.initializeLocale();
      } catch {
        // last resort — locale stays at default
      }
      this.loadReaderSettings();
      this.loadLibrary();
      this.loadStats(undefined);
    }
  }

  // ——— Import ———

  handleImportFile = async (): Promise<void> => {
    const file = await pickFile();
    if (!file) {
      return;
    }

    this.isImporting = true;
    this.readerError = null;

    try {
      const format = file.name.toLowerCase().endsWith(".epub") ? "epub" : "pdf";
      const title = file.name.replace(/\.(pdf|epub)$/i, "");

      // For PDFs, extract author from metadata before importing
      let author: string | undefined;
      if (format === "pdf") {
        try {
          const meta = await extractPdfMetadata(file.path);
          if (meta.author) {
            author = meta.author;
          }
        } catch {
          // metadata extraction is best-effort
        }
      }

      await importBook(
        {
          sourcePath: file.path,
          title,
          author,
          format,
        },
        (progress) => {
          this.importProgress = progress;
        },
      );

      await this.loadLibrary();
    } catch (error) {
      this.readerError = error instanceof Error ? error.message : this.t("import.failed");
    } finally {
      this.isImporting = false;
      this.importProgress = null;
    }
  };

  openBulkImportModal(): void {
    this.isBulkImportOpen = true;
  }

  closeBulkImportModal = (): void => {
    if (this.isBulkImporting) {
      this.bulkImportService.cancel();
    }

    this.isBulkImportOpen = false;
    this.isBulkScanning = false;
    this.bulkScanError = null;
    this.bulkImportProgress = null;
    this.bulkImportSummary = null;
  };

  handlePickBulkImportFolder = async (): Promise<void> => {
    const selected = await pickFolder(this.t("library.bulkImport.selectFolderTitle"));
    if (!selected) {
      return;
    }

    this.bulkImportFolderPath = selected.path;
    this.bulkImportFolderName = selected.name;
    this.bulkScanResult = null;
    this.bulkScanError = null;
    this.bulkImportProgress = null;
    this.bulkImportSummary = null;
  };

  handleScanBulkImportFolder = async (): Promise<void> => {
    if (!this.bulkImportFolderPath) {
      return;
    }

    this.isBulkScanning = true;
    this.bulkScanError = null;

    try {
      this.bulkScanResult = await scanFolder(this.bulkImportFolderPath);
    } catch (error) {
      this.bulkScanError = error instanceof Error ? error.message : this.t("import.failed");
    } finally {
      this.isBulkScanning = false;
    }
  };

  handleCancelBulkImport = (): void => {
    this.bulkImportService.cancel();
  };

  handleStartBulkImport = async (): Promise<void> => {
    if (!this.bulkImportFolderPath || !this.bulkScanResult || this.bulkScanResult.files.length === 0) {
      return;
    }

    this.isBulkImporting = true;
    this.bulkScanError = null;
    this.bulkImportProgress = null;
    this.bulkImportSummary = null;

    try {
      const summary = await this.bulkImportService.importFolder(
        this.bulkImportFolderPath,
        (progress) => {
          this.bulkImportProgress = progress;
        },
      );

      this.bulkImportSummary = summary;

      if (summary.success > 0 || summary.skipped > 0 || summary.failed > 0 || summary.cancelled > 0) {
        await this.loadLibrary();
      }
    } catch (error) {
      this.bulkScanError = error instanceof Error ? error.message : this.t("import.failed");
    } finally {
      this.isBulkImporting = false;
    }
  };

  // ——— Reading ———

  async startReading(book: ReaderBook): Promise<void> {
    this.books = promoteBookForReading(this.books, book.id);

    this.activeReadingBookId = book.id;
    this.shelfDetailsBookId = null;
    this.route = "reader";
    this.searchResponse = null;
    this.searchTargetLocator = null;

    // Record reader open metric
    recordMetric(METRIC_NAMES.READER_OPEN, {
      feature: book.format.toLowerCase(),
    });

    // Clear previous preload data
    this.preloadedBytes = null;

    // Start preloading file data during the navigation transition
    const format = book.format.toLowerCase();

    if (format === "epub") {
      // Preload EPUB bytes — the viewer will use them instead of calling getFileBytes
      getFileBytes(book.filePath)
        .then((bytes) => {
          this.preloadedBytes = { filePath: book.filePath, data: bytes };
        })
        .catch(() => {
          // Preload failed silently — viewer will load normally
        });
    } else if (format === "pdf") {
      // Pre-start PDF streaming early so the document caches before viewer mounts.
      // We also preload the raw bytes as a fallback for small files.
      void getFileBytes(book.filePath).then((bytes) => {
        this.preloadedBytes = { filePath: book.filePath, data: bytes };
      }).catch(() => {
        // Preload failed silently — viewer will load normally
      });

      // Also kick off streaming via pdfStreaming.ts which will cache the document
      import("$lib/features/reader/pdf/pdfStreaming").then(({ createPdfDocument }) => {
        void createPdfDocument(book.filePath).catch(() => {
          // Preload failed — viewer will try fresh
        });
      });
    }

    if (format === "epub") {
      try {
        const progress = await getProgress(book.id);
        this.cfiLocation = progress?.cfiLocation ?? "";
        this.percentage = progress?.percentage ?? 0;
      } catch {
        this.cfiLocation = "";
        this.percentage = 0;
      }
    }

    void this.loadStats(book.id);
  }

  handlePdfPageChange = async (page: number, total: number): Promise<void> => {
    const current = this.getBookById(this.activeReadingBookId);
    if (!current) {
      return;
    }

    this.books = this.books.map((book) =>
      book.id === current.id
        ? {
            ...book,
            currentPage: page,
            totalPages: total,
          }
        : book,
    );

    try {
      await updateBookProgress(current.id, page);
    } catch {
      // keep reader responsive even if progress write fails
    }

    void this.loadStats(current.id);
  };

  handlePdfSessionProgress = async (event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }): Promise<void> => {
    const current = this.getBookById(this.activeReadingBookId);
    if (!current) {
      return;
    }

    if (!this.isValidSessionProgressEvent(event)) {
      return;
    }

    const payload: ReadingSessionInput = {
      bookId: current.id,
      startedAt: event.startedAt,
      endedAt: event.endedAt,
      durationSeconds: event.durationSeconds,
      startPercentage: event.startPercentage,
      endPercentage: event.endPercentage,
    };

    try {
      await saveReadingSession(payload);
      void this.loadStats(current.id);
    } catch {
      // non-blocking stats event path
    }
  };

  handleEpubLocationChange = async (nextLocation: string, nextPercentage: number): Promise<void> => {
    const current = this.getBookById(this.activeReadingBookId);
    if (!current) {
      return;
    }

    this.cfiLocation = nextLocation;
    this.percentage = Math.max(0, Math.min(100, nextPercentage));

    const payload: SaveProgressInput = {
      bookId: current.id,
      cfiLocation: nextLocation,
      percentage: this.percentage,
    };

    try {
      await saveProgress(payload);
    } catch {
      // keep UI usable even when save fails
    }

    void this.loadStats(current.id);
  };

  handleReaderLocationContext = (): void => {
    // reserved for index_book_text integration when extraction pipeline is wired
  };

  // ——— Search ———

  handleSearch = async (query: string, page: number): Promise<void> => {
    const activeBook = this.getBookById(this.activeReadingBookId);
    if (!activeBook) {
      return;
    }

    this.isSearching = true;

    try {
      this.searchResponse = await searchBookText({
        bookId: activeBook.id,
        query,
        page,
        pageSize: 200,
      });
      this.setDomainUnavailable(DOMAIN.SEARCH, null);
    } catch (error) {
      const details = this.mapCommandError(error);
      if (details.recoverable) {
        this.setDomainUnavailable(DOMAIN.SEARCH, details.message);
      } else {
        this.readerError = details.message;
      }
      this.searchResponse = null;
    } finally {
      this.isSearching = false;
    }
  };

  handleSearchJump = (target: SearchNavigationTarget): void => {
    this.searchTargetLocator = target.locator;
  };

  // ——— Book actions ———

  async handleHideBook(book: ReaderBook): Promise<void> {
    try {
      await hideBookFromLibrary(book.id);

      if (this.previewBookId === book.id) {
        this.previewBookId = null;
      }

      if (this.shelfDetailsBookId === book.id) {
        this.shelfDetailsBookId = null;
      }

      if (this.activeReadingBookId === book.id) {
        this.activeReadingBookId = null;
        this.route = "home";
      }

      await this.loadLibrary();
    } catch (error) {
      const details = this.mapCommandError(error);
      this.readerError = details.message;
    }
  }

  async handleToggleFavorite(book: ReaderBook): Promise<void> {
    const nextFavorite = !Boolean(book.isFavorite);

    this.books = this.books.map((currentBook) => {
      if (currentBook.id !== book.id) {
        return currentBook;
      }

      return {
        ...currentBook,
        isFavorite: nextFavorite,
      };
    });

    const currentSnapshot = this.books.find((entry) => entry.id === book.id);
    if (!currentSnapshot) {
      return;
    }

    try {
      await upsertBook({
        id: currentSnapshot.id,
        title: currentSnapshot.title,
        author: currentSnapshot.author || "",
        filePath: currentSnapshot.filePath,
        format: currentSnapshot.format,
        syncStatus: "local" as const,
        currentPage: currentSnapshot.currentPage,
        totalPages: currentSnapshot.totalPages,
      });
    } catch (error) {
      this.books = this.books.map((currentBook) => {
        if (currentBook.id !== book.id) {
          return currentBook;
        }

        return {
          ...currentBook,
          isFavorite: Boolean(book.isFavorite),
        };
      });

      const details = this.mapCommandError(error);
      this.readerError = details.message;
    }
  }

  async handleMarkCompleted(book: ReaderBook): Promise<void> {
    try {
      if (book.format.toLowerCase() === "epub") {
        await saveProgress({
          bookId: book.id,
          cfiLocation: "",
          percentage: 100,
        });
      } else {
        await updateBookProgress(book.id, Math.max(1, book.totalPages || book.currentPage || 1));
      }

      this.books = this.books.map((currentBook) =>
        currentBook.id === book.id
          ? {
              ...currentBook,
              currentPage: Math.max(
                currentBook.currentPage,
                currentBook.totalPages || currentBook.currentPage,
              ),
              progressPercentage: 100,
              completed: true,
            }
          : currentBook,
      );

      await this.loadLibrary();
      await this.loadStats(undefined);
    } catch (error) {
      const details = this.mapCommandError(error);
      this.readerError = details.message;
    }
  }

  handleEditBook(book: ReaderBook): void {
    this.editingBook = book;
  }

  handleSaveEditedBook = async (updatedBook: LibraryBookDto): Promise<void> => {
    try {
      const readerBook = this.books.find((b) => b.id === updatedBook.id);
      if (!readerBook) {
        return;
      }

      await upsertBook({
        id: updatedBook.id,
        title: updatedBook.title,
        author: updatedBook.author || "",
        filePath: readerBook.filePath,
        format: readerBook.format,
        syncStatus: "local" as const,
        currentPage: readerBook.currentPage,
        totalPages: readerBook.totalPages,
      });

      this.books = this.books.map((b) =>
        b.id === updatedBook.id
          ? { ...b, title: updatedBook.title, author: updatedBook.author }
          : b,
      );

      this.editingBook = null;
    } catch (error) {
      const details = this.mapCommandError(error);
      this.readerError = details.message;
    }
  };

  handleReaderSettingsChange = (nextSettings: ReaderSettings): void => {
    this.readerSettings = nextSettings;
  };

  handleLocaleChange = (nextLocale: UiLocale): void => {
    this.locale = nextLocale;
  };

  // ——— Shelf ———

  openDetails(book: ReaderBook): void {
    this.previewBookId = book.id;
  }

  openShelfDetails = (book: ReaderBook): void => {
    this.previewBookId = book.id;
    this.shelfDetailsBookId = book.id;
  };

  closeShelfDetails = (): void => {
    this.shelfDetailsBookId = null;
  };

  setShelfTab(tab: (typeof this.SHELF_TAB_OPTIONS)[number]["key"]): void {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { tab });
  }

  setShelfSort(sortKey: (typeof this.SHELF_SORT_OPTIONS)[number]["key"]): void {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { sortKey });
  }

  setShelfViewMode(viewMode: "grid" | "list"): void {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { viewMode });
  }

  handleShelfQueryInput = (event: Event): void => {
    const target = event.target as HTMLInputElement;
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, {
      rawQuery: target.value,
    });
  };

  clearShelfQuery = (): void => {
    this.shelfQueryState = updateShelfQueryState(this.shelfQueryState, { rawQuery: "" });
  };
}

export const appState = new AppState();
