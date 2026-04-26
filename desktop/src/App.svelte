<script lang="ts">
  import { onMount } from "svelte";
  import DropMenu from "./lib/components/ui/DropMenu.svelte";
  import Button from "./lib/components/ui/Button.svelte";
  import ErrorToast from "./lib/components/ui/ErrorToast.svelte";
  import ErrorFallback from "./lib/components/ui/ErrorFallback.svelte";
  import SettingsPanel from "./lib/domain/settings/SettingsPanel.svelte";
  import HomeDesktopView from "./lib/components/layout/HomeDesktopView.svelte";
  import AppSidebar from "./lib/components/layout/AppSidebar.svelte";
  import LibraryShelfScreen from "./lib/components/layout/LibraryShelfScreen.svelte";
  import SearchPanel from "./lib/components/reader/SearchPanel.svelte";
  import EpubViewer from "./lib/domain/reader/EpubViewer.svelte";
  import PdfViewer from "./lib/domain/reader/PdfViewer.svelte";
  import EditMetadataModal from "./lib/components/library/EditMetadataModal.svelte";
  import CollectionManager from "./lib/components/library/CollectionManager.svelte";
  import BulkImportModal from "./lib/components/library/BulkImportModal.svelte";
  import ShelfActionMenu from "./lib/components/library/ShelfActionMenu.svelte";
  import BookCard from "./lib/components/library/BookCard.svelte";
  import HighlightsView from "./lib/components/layout/HighlightsView.svelte";
  import ReadingStatisticsView from "./lib/components/stats/ReadingStatisticsView.svelte";

  import { importBook, type ImportProgress } from "./lib/services/BookImportService";
  import {
    BulkImportService,
    type BulkImportProgress,
  } from "./lib/services/BulkImportService";
  import { pickFile, pickFolder } from "./lib/services/FilePicker";
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
    addBookToCollection,
    removeBookFromCollection,
    scanFolder,
  } from "./lib/api/tauriClient";
  import { i18n, type MessageKey } from "./lib/i18n";
  import { extractPdfMetadata } from "./lib/services/pdfThumbnail";
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
  } from "./lib/stores/homeState";
  import { initTheme } from "./lib/stores/theme";

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
  } from "./lib/types";

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

  let books = $state<ReaderBook[]>([]);
  let shelfQueryState = $state(createShelfQueryState(""));
  let route = $state<AppRoute>("home");
  let previewBookId = $state<string | null>(null);
  let activeReadingBookId = $state<string | null>(null);
  let shelfDetailsBookId = $state<string | null>(null);
  let libraryUnavailableReason = $state<string | null>(null);
  let statsUnavailableReason = $state<string | null>(null);
  let searchUnavailableReason = $state<string | null>(null);

  let isLoadingLibrary = $state(false);
  let isLoadingStats = $state(false);
  let isSearching = $state(false);
  let isImporting = $state(false);
  let importProgress = $state<ImportProgress | null>(null);

  let cfiLocation = $state("");
  let percentage = $state(0);
  let stats = $state<ReadingStatsSummaryDto | null>(null);
  let searchResponse = $state<SearchBookTextResponse | null>(null);
  let searchTargetLocator = $state<string | null>(null);
  let readerError = $state<string | null>(null);
  let locale = $state<UiLocale>("es");
  let readerSettings = $state<ReaderSettings>(getDefaultReaderSettings());
  const thumbnailGenerationInFlight = new Set<string>();
  const thumbnailGenerationAttempted = new Set<string>();

  let editingBook = $state<ReaderBook | null>(null);
  let collections = $state<CollectionDto[]>([]);
  let isCollectionManagerOpen = $state(false);
  const bulkImportService = new BulkImportService();

  let isBulkImportOpen = $state(false);
  let isBulkScanning = $state(false);
  let isBulkImporting = $state(false);
  let bulkImportFolderPath = $state<string | null>(null);
  let bulkImportFolderName = $state<string | null>(null);
  let bulkScanResult = $state<ScanFolderResult | null>(null);
  let bulkScanError = $state<string | null>(null);
  let bulkImportProgress = $state<BulkImportProgress | null>(null);
  let bulkImportSummary = $state<BulkImportSummary | null>(null);

  const t = (key: MessageKey, params?: Record<string, string | number>) => i18n.t(locale, key, params);

  const SHELF_TAB_OPTIONS = [
    { key: "all", label: "home.shelfTab.all" },
    { key: "favorites", label: "home.shelfTab.favorites" },
    { key: "to_read", label: "home.shelfTab.toRead" },
    { key: "completed", label: "home.shelfTab.completed" },
  ] as const;

  const SHELF_SORT_OPTIONS = [
    { key: "progress", label: "home.shelfSort.progress" },
    { key: "date", label: "home.shelfSort.date" },
    { key: "last_read", label: "home.shelfSort.lastRead" },
    { key: "author", label: "home.shelfSort.author" },
    { key: "title", label: "home.shelfSort.title" },
    { key: "file_size", label: "home.shelfSort.fileSize" },
  ] as const;

  const mapCommandError = (error: unknown) => {
    const typed = error as MaybeCommandError;
    if (typed.commandError) {
      return typed.commandError;
    }

    const fallback = error instanceof Error ? error.message : t("errors.commandFailure");
    return {
      code: "INTERNAL_ERROR",
      message: fallback,
      recoverable: false,
    } satisfies CommandErrorDto;
  };

  const isValidSessionProgressEvent = (event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }) => {
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
  };

  const setDomainUnavailable = (domain: Domain, reason: string | null) => {
    if (domain === DOMAIN.LIBRARY) {
      libraryUnavailableReason = reason;
      return;
    }

    if (domain === DOMAIN.STATS) {
      statsUnavailableReason = reason;
      return;
    }

    searchUnavailableReason = reason;
  };

  const getBookById = (bookId: string | null) => {
    if (!bookId) {
      return null;
    }

    return books.find((book) => book.id === bookId) ?? null;
  };

  const openDetails = (book: ReaderBook) => {
    previewBookId = book.id;
  };

  const openShelfDetails = (book: ReaderBook) => {
    previewBookId = book.id;
    shelfDetailsBookId = book.id;
  };

  const closeShelfDetails = () => {
    shelfDetailsBookId = null;
  };

  const setShelfTab = (tab: (typeof SHELF_TAB_OPTIONS)[number]["key"]) => {
    shelfQueryState = updateShelfQueryState(shelfQueryState, { tab });
  };

  const setShelfSort = (sortKey: (typeof SHELF_SORT_OPTIONS)[number]["key"]) => {
    shelfQueryState = updateShelfQueryState(shelfQueryState, { sortKey });
  };

  const setShelfViewMode = (viewMode: "grid" | "list") => {
    shelfQueryState = updateShelfQueryState(shelfQueryState, { viewMode });
  };

  const handleShelfQueryInput = (event: Event) => {
    const target = event.target as HTMLInputElement;
    shelfQueryState = updateShelfQueryState(shelfQueryState, { rawQuery: target.value });
  };

  const clearShelfQuery = () => {
    shelfQueryState = updateShelfQueryState(shelfQueryState, { rawQuery: "" });
  };

  const continueReadingBooks = $derived.by(() => partitionHomeBooks(books).continueReadingBooks);
  const myShelfBooks = $derived.by(() => partitionHomeBooks(books).myShelfBooks);
  const shelfBooks = $derived.by(() => selectShelfBooks(myShelfBooks, shelfQueryState));
  const shelfWarnings = $derived.by(() => getShelfQueryWarnings(shelfQueryState));
  const shelfSortToken = $derived.by(() => {
    for (let index = shelfQueryState.smartTokens.length - 1; index >= 0; index -= 1) {
      const token = shelfQueryState.smartTokens[index];
      if (token.field === "sort") {
        return token.value;
      }
    }

    return null;
  });
  const selectedShelfBook = $derived.by(() => {
    const selected = getBookById(shelfDetailsBookId);
    if (!selected) {
      return null;
    }

    return myShelfBooks.find((book) => book.id === selected.id) ?? null;
  });

  const navigateToHome = () => {
    route = "home";
    shelfDetailsBookId = null;
  };

  const navigateToLibrary = () => {
    route = "library";
    shelfDetailsBookId = null;
  };

  const navigateToStats = () => {
    route = "stats";
    shelfDetailsBookId = null;
  };

  const navigateToHighlights = () => {
    route = "highlights";
    shelfDetailsBookId = null;
  };

  const navigateToSettings = () => {
    route = "settings";
    shelfDetailsBookId = null;
  };

  const startReading = async (book: ReaderBook) => {
    books = promoteBookForReading(books, book.id);

    activeReadingBookId = book.id;
    shelfDetailsBookId = null;
    route = "reader";
    searchResponse = null;
    searchTargetLocator = null;

    if (book.format.toLowerCase() === "epub") {
      try {
        const progress = await getProgress(book.id);
        cfiLocation = progress?.cfiLocation ?? "";
        percentage = progress?.percentage ?? 0;
      } catch {
        cfiLocation = "";
        percentage = 0;
      }
    }

    void loadStats(book.id);
  };

  const backToHome = () => {
    route = "home";
  };

  const hasResolvedCoverPath = (book: Pick<LibraryBookDto, "coverPath">) => {
    return typeof book.coverPath === "string" && book.coverPath.trim().length > 0;
  };

  const shouldGeneratePdfCover = (book: ReaderBook) => {
    if (book.format.toLowerCase() !== "pdf") {
      return false;
    }

    if (hasResolvedCoverPath(book)) {
      return false;
    }

    return book.filePath.trim().length > 0;
  };

  const ensurePdfCover = async (book: ReaderBook) => {
    if (thumbnailGenerationInFlight.has(book.id)) {
      return;
    }

    thumbnailGenerationInFlight.add(book.id);
    try {
      console.log(`[App] Ensuring cover and metadata for book: ${book.title}`);
      const metadata = await extractPdfMetadata(book.filePath);
      console.log(`[App] Extracted metadata:`, { 
        hasThumbnail: !!metadata.thumbnailBytes, 
        author: metadata.author, 
        totalPages: metadata.totalPages 
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
          syncStatus: "local",
          currentPage: book.currentPage,
          totalPages: metadata.totalPages || book.totalPages || 0,
        };
        await upsertBook(bookDtoUpdate);
      }

      await loadLibrary();
    } catch (e) {
      console.error(`[App] ensurePdfCover failed:`, e);
    } finally {
      thumbnailGenerationInFlight.delete(book.id);
    }

  };

  const loadLibrary = async () => {
    isLoadingLibrary = true;
    readerError = null;

    try {
      const [libraryRows, sourceRows, loadedCollections] = await Promise.all([
        listLibraryBooks(1), 
        listBooks(),
        listCollections()
      ]);
      collections = loadedCollections;
      
      const filePathById = new Map<string, string>(
        sourceRows.map((book: BookDto) => [book.id, book.filePath]),
      );

      // Use collectionIds directly from backend (no N+1 needed)
      const booksWithCollections = libraryRows.map((entry: LibraryBookDto) => ({
        ...entry,
        filePath: filePathById.get(entry.id) ?? "",
        collectionIds: entry.collectionIds ?? []
      }));

      books = booksWithCollections;

      const reconciledState = reconcileHomeState(booksWithCollections, {
        route,
        previewBookId,
        activeReadingBookId,
        shelfDetailsBookId,
      });
      route = reconciledState.route;
      previewBookId = reconciledState.previewBookId;
      activeReadingBookId = reconciledState.activeReadingBookId;
      shelfDetailsBookId = reconciledState.shelfDetailsBookId;

      const pendingThumbnailBooks = books.filter((book) => {
        if (!shouldGeneratePdfCover(book)) {
          return false;
        }

        if (thumbnailGenerationAttempted.has(book.id)) {
          return false;
        }

        thumbnailGenerationAttempted.add(book.id);
        return true;
      });

      const THUMBNAIL_CONCURRENCY = 3;

      setDomainUnavailable(DOMAIN.LIBRARY, null);

      // Process thumbnails in parallel batches
      for (let i = 0; i < pendingThumbnailBooks.length; i += THUMBNAIL_CONCURRENCY) {
        const batch = pendingThumbnailBooks.slice(i, i + THUMBNAIL_CONCURRENCY);
        await Promise.all(batch.map((book) => ensurePdfCover(book)));
      }
    } catch (error) {
      const details = mapCommandError(error);
      if (details.recoverable) {
        setDomainUnavailable(DOMAIN.LIBRARY, details.message);
      } else {
        readerError = details.message;
      }
    } finally {
      isLoadingLibrary = false;
    }
  };

  const loadStats = async (bookId?: string) => {
    isLoadingStats = true;

    try {
      stats = await getReadingStats(bookId);
      setDomainUnavailable(DOMAIN.STATS, null);
    } catch (error) {
      const details = mapCommandError(error);
      if (details.recoverable) {
        setDomainUnavailable(DOMAIN.STATS, details.message);
      } else {
        readerError = details.message;
      }
      stats = null;
    } finally {
      isLoadingStats = false;
    }
  };

  const handleImportFile = async () => {
    const file = await pickFile();
    if (!file) {
      return;
    }

    isImporting = true;
    readerError = null;

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
          importProgress = progress;
        },
      );

      await loadLibrary();
    } catch (error) {
      readerError = error instanceof Error ? error.message : t("import.failed");
    } finally {
      isImporting = false;
      importProgress = null;
    }
  };

  const openBulkImportModal = () => {
    isBulkImportOpen = true;
  };

  const closeBulkImportModal = () => {
    if (isBulkImporting) {
      bulkImportService.cancel();
    }

    isBulkImportOpen = false;
    isBulkScanning = false;
    bulkScanError = null;
    bulkImportProgress = null;
    bulkImportSummary = null;
  };

  const handlePickBulkImportFolder = async () => {
    const selected = await pickFolder(t("library.bulkImport.selectFolderTitle"));
    if (!selected) {
      return;
    }

    bulkImportFolderPath = selected.path;
    bulkImportFolderName = selected.name;
    bulkScanResult = null;
    bulkScanError = null;
    bulkImportProgress = null;
    bulkImportSummary = null;
  };

  const handleScanBulkImportFolder = async () => {
    if (!bulkImportFolderPath) {
      return;
    }

    isBulkScanning = true;
    bulkScanError = null;

    try {
      bulkScanResult = await scanFolder(bulkImportFolderPath);
    } catch (error) {
      bulkScanError = error instanceof Error ? error.message : t("import.failed");
    } finally {
      isBulkScanning = false;
    }
  };

  const handleCancelBulkImport = () => {
    bulkImportService.cancel();
  };

  const handleStartBulkImport = async () => {
    if (!bulkImportFolderPath || !bulkScanResult || bulkScanResult.files.length === 0) {
      return;
    }

    isBulkImporting = true;
    bulkScanError = null;
    bulkImportProgress = null;
    bulkImportSummary = null;

    try {
      const summary = await bulkImportService.importFolder(bulkImportFolderPath, (progress) => {
        bulkImportProgress = progress;
      });

      bulkImportSummary = summary;

      if (summary.success > 0 || summary.skipped > 0 || summary.failed > 0 || summary.cancelled > 0) {
        await loadLibrary();
      }
    } catch (error) {
      bulkScanError = error instanceof Error ? error.message : t("import.failed");
    } finally {
      isBulkImporting = false;
    }
  };

  const handlePdfPageChange = async (page: number, total: number) => {
    const current = getBookById(activeReadingBookId);
    if (!current) {
      return;
    }

    books = books.map((book) =>
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

    void loadStats(current.id);
  };

  const handlePdfSessionProgress = async (event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }) => {
    const current = getBookById(activeReadingBookId);
    if (!current) {
      return;
    }

    if (!isValidSessionProgressEvent(event)) {
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
      void loadStats(current.id);
    } catch {
      // non-blocking stats event path
    }
  };

  const handleEpubLocationChange = async (nextLocation: string, nextPercentage: number) => {
    const current = getBookById(activeReadingBookId);
    if (!current) {
      return;
    }

    cfiLocation = nextLocation;
    percentage = Math.max(0, Math.min(100, nextPercentage));

    const payload: SaveProgressInput = {
      bookId: current.id,
      cfiLocation: nextLocation,
      percentage,
    };

    try {
      await saveProgress(payload);
    } catch {
      // keep UI usable even when save fails
    }

    void loadStats(current.id);
  };

  const handleSearch = async (query: string, page: number) => {
    const activeBook = getBookById(activeReadingBookId);
    if (!activeBook) {
      return;
    }

    isSearching = true;

    try {
      searchResponse = await searchBookText({
        bookId: activeBook.id,
        query,
        page,
        pageSize: 200,
      });
      setDomainUnavailable(DOMAIN.SEARCH, null);
    } catch (error) {
      const details = mapCommandError(error);
      if (details.recoverable) {
        setDomainUnavailable(DOMAIN.SEARCH, details.message);
      } else {
        readerError = details.message;
      }
      searchResponse = null;
    } finally {
      isSearching = false;
    }
  };

  const handleSearchJump = (target: SearchNavigationTarget) => {
    searchTargetLocator = target.locator;
  };

  const handleHideBook = async (book: ReaderBook) => {
    try {
      await hideBookFromLibrary(book.id);

      if (previewBookId === book.id) {
        previewBookId = null;
      }

      if (shelfDetailsBookId === book.id) {
        shelfDetailsBookId = null;
      }

      if (activeReadingBookId === book.id) {
        activeReadingBookId = null;
        route = "home";
      }

      await loadLibrary();
    } catch (error) {
      const details = mapCommandError(error);
      readerError = details.message;
    }
  };

  const handleToggleFavorite = async (book: ReaderBook) => {
    const nextFavorite = !Boolean(book.isFavorite);

    books = books.map((currentBook) => {
      if (currentBook.id !== book.id) {
        return currentBook;
      }

      return {
        ...currentBook,
        isFavorite: nextFavorite,
      };
    });

    const currentSnapshot = books.find((entry) => entry.id === book.id);
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
        syncStatus: "local",
        currentPage: currentSnapshot.currentPage,
        totalPages: currentSnapshot.totalPages,
      });
    } catch (error) {
      books = books.map((currentBook) => {
        if (currentBook.id !== book.id) {
          return currentBook;
        }

        return {
          ...currentBook,
          isFavorite: Boolean(book.isFavorite),
        };
      });

      const details = mapCommandError(error);
      readerError = details.message;
    }
  };

  const handleMarkCompleted = async (book: ReaderBook) => {
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

      books = books.map((currentBook) =>
        currentBook.id === book.id
          ? {
              ...currentBook,
              currentPage: Math.max(currentBook.currentPage, currentBook.totalPages || currentBook.currentPage),
              progressPercentage: 100,
              completed: true,
            }
          : currentBook,
      );

      await loadLibrary();
      await loadStats(undefined);
    } catch (error) {
      const details = mapCommandError(error);
      readerError = details.message;
    }
  };

  const handleReaderLocationContext = () => {
    // reserved for index_book_text integration when extraction pipeline is wired
  };

  const handleLocaleChange = (nextLocale: UiLocale) => {
    locale = nextLocale;
  };

  const loadReaderSettings = async () => {
    try {
      readerSettings = await getReaderSettings();
    } catch {
      readerSettings = getDefaultReaderSettings();
    }
  };

  const handleReaderSettingsChange = (nextSettings: ReaderSettings) => {
    readerSettings = nextSettings;
  };

  const handleEditBook = (book: ReaderBook) => {
    editingBook = book;
  };

  const handleSaveEditedBook = async (updatedBook: LibraryBookDto) => {
    try {
      const readerBook = books.find((b) => b.id === updatedBook.id);
      if (!readerBook) {
        return;
      }

      await upsertBook({
        id: updatedBook.id,
        title: updatedBook.title,
        author: updatedBook.author || "",
        filePath: readerBook.filePath,
        format: readerBook.format,
        syncStatus: "local",
        currentPage: readerBook.currentPage,
        totalPages: readerBook.totalPages,
      });

      books = books.map((b) =>
        b.id === updatedBook.id ? { ...b, title: updatedBook.title, author: updatedBook.author } : b,
      );

      editingBook = null;
    } catch (error) {
      const details = mapCommandError(error);
      readerError = details.message;
    }
  };

  onMount(() => {
    // Apply persisted theme immediately
    initTheme();

    // Load all initialization tasks in parallel
    Promise.all([
      i18n.initializeLocale(),
      loadReaderSettings(),
      loadLibrary(),
      loadStats(undefined)
    ]).then(([nextLocale]) => {
      locale = nextLocale;
      // Note: loadReaderSettings, loadLibrary, loadStats already set their respective state
    }).catch((error) => {
      console.error("Initialization error:", error);
      // Fallback: continue with defaults
      i18n.initializeLocale().then((nextLocale) => {
        locale = nextLocale;
      });
      loadReaderSettings();
      loadLibrary();
      loadStats(undefined);
    });
  });
</script>

<main class="flex h-screen overflow-hidden bg-[var(--color-background)] text-[var(--color-primary)]">
  {#if route !== "reader"}
    <AppSidebar
      activeRoute={route}
      onNavigateHome={navigateToHome}
      onNavigateLibrary={navigateToLibrary}
      onNavigateStats={navigateToStats}
      onNavigateHighlights={navigateToHighlights}
      onNavigateSettings={navigateToSettings}
      {t}
    />
  {/if}
  
  <div class="flex-1 overflow-y-auto p-4 md:p-6 relative">
    <div class="mx-auto max-w-7xl">
      {#if importProgress}
      <p class="mb-3 text-sm text-[var(--color-secondary)]">{importProgress.message}</p>
    {/if}

    {#if readerError}
      <p class="mb-3 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900">{readerError}</p>
    {/if}

    {#if route === "home"}
      {@const previewBook = getBookById(previewBookId)}
      <HomeDesktopView
        stats={stats}
        isLoadingStats={isLoadingStats}
        statsUnavailableReason={statsUnavailableReason}
        selectedBookTitle={previewBook?.title ?? null}
        continueCount={continueReadingBooks.length}
        shelfCount={myShelfBooks.length}
        statsMinutes={stats?.totalMinutesRead ?? 0}
        activeRoute="home"
        onNavigateHome={navigateToHome}
        onNavigateHighlights={navigateToHighlights}
        onNavigateSettings={navigateToSettings}
        onRefreshStats={() => {
          void loadStats(previewBookId ?? undefined);
        }}
        {t}
      >
        {#snippet navbarActions()}
          <Button onclick={handleImportFile} disabled={isImporting} size="sm">
            {isImporting ? t("app.importing") : t("app.importBook")}
          </Button>
          <DropMenu position="bottom-right">
            {#snippet trigger()}
              <button
                class="rounded-md border border-[color:var(--color-border)] bg-[var(--color-surface)] px-2 py-1 text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
                aria-label={t("app.openMenu")}
              >
                {t("app.menu")}
              </button>
            {/snippet}
            <button
              class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
              onclick={navigateToSettings}
            >
              {t("app.settings")}
            </button>
          </DropMenu>
        {/snippet}

        {#snippet continueSection()}
          {#if continueReadingBooks.length === 0}
            <p class="text-sm text-[var(--color-text-muted)]">{t("home.continueReadingPlaceholder")}</p>
          {:else if continueReadingBooks.length === 1}
            {@const book = continueReadingBooks[0]}
            <BookCard
              book={book}
              variant="continue-reading"
              selected={previewBookId === book.id}
              onSelect={() => {
                openDetails(book);
              }}
              onRead={() => {
                void startReading(book);
              }}
              {t}
            />
          {:else}
            <ul class="space-y-2">
              {#each continueReadingBooks as book}
                <li>
                  <BookCard
                    book={book}
                    variant="continue-reading"
                    compact={continueReadingBooks.length > 1}
                    selected={previewBookId === book.id}
                    onSelect={() => {
                      openDetails(book);
                    }}
                    onRead={() => {
                      void startReading(book);
                    }}
                    {t}
                  />
                </li>
              {/each}
            </ul>
            {#if previewBook}
              <p class="mt-2 text-sm text-[var(--color-text-muted)]">{t("app.homeReadHint")}</p>
            {/if}
          {/if}
        {/snippet}

        {#snippet shelfSection()}
          <div class="space-y-3">
            <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div class="flex flex-wrap items-center gap-2" data-testid="shelf-tabs">
                {#each SHELF_TAB_OPTIONS as tabOption}
                  <button
                    type="button"
                    data-testid={`shelf-tab-${tabOption.key}`}
                    class={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${shelfQueryState.tab === tabOption.key ? "border-[var(--color-primary)] bg-[color:color-mix(in_srgb,var(--color-primary)_12%,var(--color-surface))] text-[var(--color-primary)]" : "border-[color:var(--color-border)] bg-[var(--color-background)] text-[var(--color-text-muted)] hover:bg-[color:var(--color-border)]"}`}
                    onclick={() => {
                      setShelfTab(tabOption.key);
                    }}
                  >
                    {t(tabOption.label)}
                  </button>
                {/each}
              </div>

              <div class="flex flex-wrap items-center gap-2">
                <label class="sr-only" for="shelf-sort-select">{t("home.shelfSortLabel")}</label>
                <select
                  id="shelf-sort-select"
                  data-testid="shelf-sort"
                  class="rounded-md border border-[color:var(--color-border)] bg-[var(--color-background)] px-2 py-1 text-xs text-[var(--color-primary)]"
                  value={shelfQueryState.sortKey}
                  onchange={(event) => {
                    const value = (event.target as HTMLSelectElement).value as (typeof SHELF_SORT_OPTIONS)[number]["key"];
                    setShelfSort(value);
                  }}
                >
                  {#each SHELF_SORT_OPTIONS as sortOption}
                    <option value={sortOption.key}>{t(sortOption.label)}</option>
                  {/each}
                </select>

                <div class="inline-flex rounded-md border border-[color:var(--color-border)] bg-[var(--color-background)] p-1" data-testid="shelf-view-toggle">
                  <button
                    type="button"
                    class={`rounded px-2 py-1 text-xs font-medium ${shelfQueryState.viewMode === "grid" ? "bg-[var(--color-surface)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
                    onclick={() => {
                      setShelfViewMode("grid");
                    }}
                  >
                    {t("library.grid")}
                  </button>
                  <button
                    type="button"
                    class={`rounded px-2 py-1 text-xs font-medium ${shelfQueryState.viewMode === "list" ? "bg-[var(--color-surface)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
                    onclick={() => {
                      setShelfViewMode("list");
                    }}
                  >
                    {t("library.list")}
                  </button>
                </div>

                <div class="relative min-w-[220px] flex-1 lg:min-w-[280px]">
                  <input
                    type="text"
                    data-testid="shelf-search"
                    class="w-full rounded-md border border-[color:var(--color-border)] bg-[var(--color-background)] px-3 py-1.5 pr-8 text-sm text-[var(--color-primary)] placeholder-[var(--color-text-muted)]"
                    placeholder={t("home.shelfSearchPlaceholder")}
                    value={shelfQueryState.rawQuery}
                    oninput={handleShelfQueryInput}
                  />
                  {#if shelfQueryState.rawQuery.length > 0}
                    <button
                      type="button"
                      class="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-[var(--color-text-muted)]"
                      aria-label={t("home.shelfClearSearch")}
                      onclick={clearShelfQuery}
                    >
                      x
                    </button>
                  {/if}
                </div>
              </div>
            </div>

            {#if shelfSortToken}
              <p class="text-xs text-[var(--color-text-muted)]">{t("home.shelfSortFromQuery", { value: shelfSortToken })}</p>
            {/if}

            {#if shelfWarnings.length > 0}
              <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900" data-testid="shelf-warnings">
                <p class="font-medium">{t("home.shelfWarningsLabel")}</p>
                <p class="mt-1">{t("home.shelfSearchInvalid", { value: shelfWarnings.join(", ") })}</p>
              </div>
            {/if}

            <p class="text-xs text-[var(--color-text-muted)]">{t("home.shelfResults", { count: shelfBooks.length, total: myShelfBooks.length })}</p>
          </div>

          {#if myShelfBooks.length === 0}
            <p class="text-sm text-[var(--color-text-muted)]">{t("home.myShelfPlaceholder")}</p>
          {:else if shelfBooks.length === 0}
            <p class="text-sm text-[var(--color-text-muted)]">{t("home.shelfNoResults")}</p>
          {:else}
            {#if shelfQueryState.viewMode === "grid"}
              {#if shelfBooks.length === 1}
                {@const book = shelfBooks[0]}
                <BookCard
                  book={book}
                  variant="shelf"
                  selected={previewBookId === book.id}
                  onSelect={() => {
                    openShelfDetails(book);
                  }}
                  onRead={() => {
                    void startReading(book);
                  }}
                  {t}
                >
                  {#snippet actions()}
                    <ShelfActionMenu
                      bookId={book.id}
                      isFavorite={Boolean(book.isFavorite)}
                      readLabel={t("app.read")}
                      editLabel={t("library.editMetadata.title")}
                      removeLabel={t("library.removeFromShelf")}
                      favoriteAddLabel={t("library.favoriteAdd")}
                      favoriteRemoveLabel={t("library.favoriteRemove")}
                      triggerLabel={t("library.optionsFor", { title: book.title })}
                      onEdit={() => {
                        handleEditBook(book);
                      }}
                      onRemove={() => {
                        void handleHideBook(book);
                      }}
                      onToggleFavorite={() => {
                        void handleToggleFavorite(book);
                      }}
                    />
                  {/snippet}
                </BookCard>
              {:else}
                <ul class="grid grid-cols-1 gap-2 md:grid-cols-2">
                  {#each shelfBooks as book}
                    <li>
                      <BookCard
                        book={book}
                        variant="shelf"
                        compact={true}
                        selected={previewBookId === book.id}
                        onSelect={() => {
                          openShelfDetails(book);
                        }}
                        onRead={() => {
                          void startReading(book);
                        }}
                        {t}
                      >
                        {#snippet actions()}
                          <ShelfActionMenu
                            bookId={book.id}
                            isFavorite={Boolean(book.isFavorite)}
                            readLabel={t("app.read")}
                            editLabel={t("library.editMetadata.title")}
                            removeLabel={t("library.removeFromShelf")}
                            favoriteAddLabel={t("library.favoriteAdd")}
                            favoriteRemoveLabel={t("library.favoriteRemove")}
                            triggerLabel={t("library.optionsFor", { title: book.title })}
                            onEdit={() => {
                              handleEditBook(book);
                            }}
                            onRemove={() => {
                              void handleHideBook(book);
                            }}
                            onToggleFavorite={() => {
                              void handleToggleFavorite(book);
                            }}
                          />
                        {/snippet}
                      </BookCard>
                    </li>
                  {/each}
                </ul>
              {/if}
            {:else}
              <ul class="space-y-2">
                {#each shelfBooks as book}
                  <li>
                    <BookCard
                      book={book}
                      variant="shelf"
                      compact={true}
                      selected={previewBookId === book.id}
                      onSelect={() => {
                        openShelfDetails(book);
                      }}
                      onRead={() => {
                        void startReading(book);
                      }}
                      {t}
                    >
                      {#snippet actions()}
                        <ShelfActionMenu
                          bookId={book.id}
                          isFavorite={Boolean(book.isFavorite)}
                          readLabel={t("app.read")}
                          editLabel={t("library.editMetadata.title")}
                          removeLabel={t("library.removeFromShelf")}
                          favoriteAddLabel={t("library.favoriteAdd")}
                          favoriteRemoveLabel={t("library.favoriteRemove")}
                          triggerLabel={t("library.optionsFor", { title: book.title })}
                          onEdit={() => {
                            handleEditBook(book);
                          }}
                          onRemove={() => {
                            void handleHideBook(book);
                          }}
                          onToggleFavorite={() => {
                            void handleToggleFavorite(book);
                          }}
                        />
                      {/snippet}
                    </BookCard>
                  </li>
                {/each}
              </ul>
            {/if}
          {/if}

          {#if selectedShelfBook}
            <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true" aria-label={selectedShelfBook.title}>
              <div class="w-full max-w-xl rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-xl">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <h3 class="line-clamp-2 text-lg font-semibold text-[var(--color-primary)]">{selectedShelfBook.title}</h3>
                    <p class="truncate text-sm text-[var(--color-text-muted)]">{selectedShelfBook.author || t("app.unknownAuthor")}</p>
                  </div>
                  <Button size="sm" variant="ghost" onclick={closeShelfDetails}>{t("settings.close")}</Button>
                </div>
                <div class="mt-4 space-y-1 text-sm text-[var(--color-text-muted)]">
                  <p>{selectedShelfBook.format.toUpperCase()}</p>
                  <p>{selectedShelfBook.currentPage}/{selectedShelfBook.totalPages || "-"} · {Math.round(getSafeProgressPercentage(selectedShelfBook))}%</p>
                </div>
                <div class="mt-4 flex justify-end gap-2">
                  <Button size="sm" variant="ghost" onclick={closeShelfDetails}>{t("settings.close")}</Button>
                  <Button
                    size="sm"
                    onclick={() => {
                      void startReading(selectedShelfBook);
                    }}
                  >
                    {t("app.read")}
                  </Button>
                </div>
              </div>
            </div>
          {/if}
        {/snippet}
      </HomeDesktopView>
    {:else if route === "library"}
      <LibraryShelfScreen
        books={books}
        isImporting={isImporting}
        onImportBook={handleImportFile}
        onOpenBook={(book) => {
          void startReading(book);
        }}
        onContinueReading={(book) => {
          void startReading(book);
        }}
        onToggleFavorite={(book) => {
          void handleToggleFavorite(book);
        }}
        onMarkCompleted={(book) => {
          void handleMarkCompleted(book);
        }}
        onViewDetails={openShelfDetails}
        onRemoveBook={(book) => {
          void handleHideBook(book);
        }}
      />
    {:else if route === "stats"}
      <ReadingStatisticsView
        books={books}
        stats={stats}
        isLoading={isLoadingStats}
        disabledReason={statsUnavailableReason}
      />
    {:else if route === "highlights"}
      <HighlightsView
        {books}
        {t}
      />
    {:else if route === "settings"}
      <section class="space-y-3">
        <div class="flex justify-end">
          <Button size="sm" variant="ghost" onclick={navigateToHome}>{t("app.backToHome")}</Button>
        </div>
        <SettingsPanel
          isOpen={true}
          mode="page"
          onRequestClose={navigateToHome}
          {t}
          {locale}
          onLocaleChange={handleLocaleChange}
          onReaderSettingsChange={handleReaderSettingsChange}
        />
      </section>
    {:else}
      {@const activeReadingBook = getBookById(activeReadingBookId)}
      <section class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <Button size="sm" variant="ghost" onclick={backToHome}>{t("app.backToHome")}</Button>
            <div>
              <h2 class="text-lg font-semibold text-[var(--color-primary)]">{activeReadingBook?.title ?? t("app.openBookPrompt")}</h2>
              {#if activeReadingBook}
                <p class="text-sm text-[var(--color-text-muted)]">
                  {activeReadingBook.author || t("app.unknownAuthor")} · {activeReadingBook.format.toUpperCase()} · {activeReadingBook.currentPage}/{activeReadingBook.totalPages || "-"}
                </p>
              {/if}
            </div>
          </div>
          {#if activeReadingBook && activeReadingBook.format.toLowerCase() === "epub"}
            <p class="text-xs text-[var(--color-text-muted)]">{t("app.locationLabel")}: {cfiLocation || t("app.start")} · {Math.round(percentage)}%</p>
          {/if}
        </div>

        {#if !activeReadingBook}
          <p class="text-sm text-[var(--color-text-muted)]">{t("app.openBookPrompt")}</p>
        {:else if activeReadingBook.format.toLowerCase() === "pdf"}
          <div class="mb-4 h-[520px] overflow-hidden rounded-lg border border-[color:var(--color-border)]">
            <PdfViewer
              filePath={activeReadingBook.filePath}
              bookId={activeReadingBook.id}
              initialPage={Math.max(1, activeReadingBook.currentPage || 1)}
              searchTargetLocator={searchTargetLocator}
              {readerSettings}
              onPageChange={handlePdfPageChange}
              onSessionProgress={handlePdfSessionProgress}
              {t}
            />
          </div>
        {:else}
          <div class="mb-4 h-[520px] overflow-hidden rounded-lg border border-[color:var(--color-border)]">
            <EpubViewer
              filePath={activeReadingBook.filePath}
              initialLocation={cfiLocation}
              initialPercentage={percentage}
              searchTargetLocator={searchTargetLocator}
              {readerSettings}
              onLocationContext={handleReaderLocationContext}
              onLocationChange={handleEpubLocationChange}
              {t}
            />
          </div>
        {/if}

        {#if activeReadingBook}
          <SearchPanel
            bookId={activeReadingBook.id}
            disabledReason={searchUnavailableReason}
            isSearching={isSearching}
            response={searchResponse}
            onSearch={(query, page) => {
              void handleSearch(query, page);
            }}
            onJump={handleSearchJump}
            {t}
          />
        {/if}
      </section>
    {/if}

    <EditMetadataModal
      book={editingBook as any}
      open={editingBook !== null}
      onClose={() => {
        editingBook = null;
      }}
      onSave={handleSaveEditedBook}
      {t}
    />

    <CollectionManager
      open={isCollectionManagerOpen}
      onClose={() => {
        isCollectionManagerOpen = false;
      }}
    />

    <BulkImportModal
      open={isBulkImportOpen}
      folderName={bulkImportFolderName}
      folderPath={bulkImportFolderPath}
      scanResult={bulkScanResult}
      isScanning={isBulkScanning}
      scanError={bulkScanError}
      isImporting={isBulkImporting}
      importProgress={bulkImportProgress}
      importSummary={bulkImportSummary}
      onClose={closeBulkImportModal}
      onPickFolder={handlePickBulkImportFolder}
      onScan={handleScanBulkImportFolder}
      onStartImport={handleStartBulkImport}
      onCancelImport={handleCancelBulkImport}
      {t}
    />

    <ErrorToast />
    <ErrorFallback />
    </div>
  </div>
</main>
