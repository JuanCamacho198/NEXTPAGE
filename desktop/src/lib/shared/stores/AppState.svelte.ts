import { i18n, type MessageKey } from "$lib/shared/i18n";
import { initTheme } from "$lib/shared/stores/theme";
import { reconcileHomeState } from "$lib/shared/stores/homeState";
import {
  navigationState,
  NavigationDomainState,
} from "$lib/shared/stores/NavigationDomainState.svelte";
import {
  libraryState,
  LibraryDomainState,
} from "$lib/shared/stores/LibraryDomainState.svelte";
import {
  readerState,
  ReaderDomainState,
} from "$lib/shared/stores/ReaderDomainState.svelte";
import {
  searchState,
  SearchDomainState,
} from "$lib/shared/stores/SearchDomainState.svelte";
import {
  bulkImportState,
  BulkImportDomainState,
} from "$lib/shared/stores/BulkImportDomainState.svelte";
import {
  statsState,
  StatsDomainState,
} from "$lib/shared/stores/StatsDomainState.svelte";
import {
  settingsState,
  SettingsDomainState,
} from "$lib/shared/stores/SettingsDomainState.svelte";

import type {
  CommandErrorDto,
  LibraryBookDto,
  ReaderBook,
  ReaderSettings,
  SearchNavigationTarget,
  UiLocale,
  BulkImportSummary,
  ScanFolderResult,
  SearchBookTextResponse,
  ReadingStatsSummaryDto,
} from "$lib/shared/types";
import { createShelfQueryState } from "$lib/shared/stores/homeState";

type MaybeCommandError = Error & { commandError?: CommandErrorDto };

class AppState {
  // ─── Domain States ───
  navigation = navigationState;
  library = libraryState;
  reader = readerState;
  search = searchState;
  bulkImport = bulkImportState;
  settings = settingsState;
  // Stats domain: use statsDomain to avoid name collision with the stats getter
  private statsDomain = statsState;

  // ─── Property passthrough: Navigation ───
  get route() { return this.navigation.route; }
  set route(v) { this.navigation.route = v; }
  get previewBookId() { return this.navigation.previewBookId; }
  set previewBookId(v) { this.navigation.previewBookId = v; }
  get shelfDetailsBookId() { return this.navigation.shelfDetailsBookId; }
  set shelfDetailsBookId(v) { this.navigation.shelfDetailsBookId = v; }
  get libraryUnavailableReason() { return this.navigation.libraryUnavailableReason; }
  set libraryUnavailableReason(v) { this.navigation.libraryUnavailableReason = v; }
  get statsUnavailableReason() { return this.navigation.statsUnavailableReason; }
  set statsUnavailableReason(v) { this.navigation.statsUnavailableReason = v; }
  get searchUnavailableReason() { return this.navigation.searchUnavailableReason; }
  set searchUnavailableReason(v) { this.navigation.searchUnavailableReason = v; }

  // ─── Property passthrough: Library ───
  get books() { return this.library.books; }
  set books(v) { this.library.books = v; }
  get shelfQueryState() { return this.library.shelfQueryState; }
  set shelfQueryState(v) { this.library.shelfQueryState = v; }
  get collections() { return this.library.collections; }
  set collections(v) { this.library.collections = v; }
  get isLoadingLibrary() { return this.library.isLoadingLibrary; }
  set isLoadingLibrary(v) { this.library.isLoadingLibrary = v; }
  get readerError() { return this.library.readerError; }
  set readerError(v) { this.library.readerError = v; }
  get editingBook() { return this.library.editingBook; }
  set editingBook(v) { this.library.editingBook = v; }
  get isCollectionManagerOpen() { return this.library.isCollectionManagerOpen; }
  set isCollectionManagerOpen(v) { this.library.isCollectionManagerOpen = v; }

  get continueReadingBooks() { return this.library.continueReadingBooks; }
  get myShelfBooks() { return this.library.myShelfBooks; }
  get shelfBooks() { return this.library.shelfBooks; }
  get shelfWarnings() { return this.library.shelfWarnings; }
  get shelfSortToken() { return this.library.shelfSortToken; }
  selectedShelfBook = $derived.by(() => {
    return this.library.myShelfBooks.find(
      (book) => book.id === this.navigation.shelfDetailsBookId,
    ) ?? null;
  });

  // ─── Property passthrough: Reader ───
  get activeReadingBookId() { return this.reader.activeReadingBookId; }
  set activeReadingBookId(v) { this.reader.activeReadingBookId = v; }
  get cfiLocation() { return this.reader.cfiLocation; }
  set cfiLocation(v) { this.reader.cfiLocation = v; }
  get percentage() { return this.reader.percentage; }
  set percentage(v) { this.reader.percentage = v; }
  get preloadedBytes() { return this.reader.preloadedBytes; }
  set preloadedBytes(v) { this.reader.preloadedBytes = v; }

  // ─── Property passthrough: Search ───
  get searchResponse() { return this.search.searchResponse; }
  set searchResponse(v) { this.search.searchResponse = v; }
  get searchTargetLocator() { return this.search.searchTargetLocator; }
  set searchTargetLocator(v) { this.search.searchTargetLocator = v; }
  get isSearching() { return this.search.isSearching; }
  set isSearching(v) { this.search.isSearching = v; }

  // ─── Property passthrough: BulkImport ───
  get isBulkImportOpen() { return this.bulkImport.isBulkImportOpen; }
  set isBulkImportOpen(v) { this.bulkImport.isBulkImportOpen = v; }
  get isBulkScanning() { return this.bulkImport.isBulkScanning; }
  set isBulkScanning(v) { this.bulkImport.isBulkScanning = v; }
  get isBulkImporting() { return this.bulkImport.isBulkImporting; }
  set isBulkImporting(v) { this.bulkImport.isBulkImporting = v; }
  get bulkImportFolderPath() { return this.bulkImport.bulkImportFolderPath; }
  set bulkImportFolderPath(v) { this.bulkImport.bulkImportFolderPath = v; }
  get bulkImportFolderName() { return this.bulkImport.bulkImportFolderName; }
  set bulkImportFolderName(v) { this.bulkImport.bulkImportFolderName = v; }
  get bulkScanResult() { return this.bulkImport.bulkScanResult; }
  set bulkScanResult(v) { this.bulkImport.bulkScanResult = v; }
  get bulkScanError() { return this.bulkImport.bulkScanError; }
  set bulkScanError(v) { this.bulkImport.bulkScanError = v; }
  get bulkImportProgress() { return this.bulkImport.bulkImportProgress; }
  set bulkImportProgress(v) { this.bulkImport.bulkImportProgress = v; }
  get bulkImportSummary() { return this.bulkImport.bulkImportSummary; }
  set bulkImportSummary(v) { this.bulkImport.bulkImportSummary = v; }
  get isImporting() { return this.bulkImport.isImporting; }
  set isImporting(v) { this.bulkImport.isImporting = v; }
  get importProgress() { return this.bulkImport.importProgress; }
  set importProgress(v) { this.bulkImport.importProgress = v; }

  // ─── Property passthrough: Stats ───
  get stats() { return this.statsDomain.stats; }
  set stats(v) { this.statsDomain.stats = v; }
  get isLoadingStats() { return this.statsDomain.isLoadingStats; }
  set isLoadingStats(v) { this.statsDomain.isLoadingStats = v; }

  // ─── Property passthrough: Settings ───
  get locale() { return this.settings.locale; }
  set locale(v) { this.settings.locale = v; }
  get readerSettings() { return this.settings.readerSettings; }
  set readerSettings(v) { this.settings.readerSettings = v; }

  // ─── Constants ───
  readonly SHELF_TAB_OPTIONS = this.library.SHELF_TAB_OPTIONS;
  readonly SHELF_SORT_OPTIONS = this.library.SHELF_SORT_OPTIONS;
  readonly DOMAIN = navigationState.DOMAIN;

  // ─── i18n helper ───
  t = (key: MessageKey, params?: Record<string, string | number>) =>
    i18n.t(this.settings.locale, key, params);

  // ─── Utility ───
  mapCommandError(error: unknown): CommandErrorDto {
    const typed = error as MaybeCommandError;
    if (typed.commandError) return typed.commandError;

    const fallback = error instanceof Error ? error.message : this.t("errors.commandFailure");
    return { code: "INTERNAL_ERROR", message: fallback, recoverable: false };
  }

  isValidSessionProgressEvent(event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }): boolean {
    return this.reader.isValidSessionProgressEvent(event);
  }

  getBookById(bookId: string | null): ReaderBook | null {
    return this.library.getBookById(bookId);
  }

  hasResolvedCoverPath(book: Pick<LibraryBookDto, "coverPath">): boolean {
    return this.library.hasResolvedCoverPath(book);
  }

  shouldGeneratePdfCover(book: ReaderBook): boolean {
    return this.library.shouldGeneratePdfCover(book);
  }

  shouldGenerateEpubCover(book: ReaderBook): boolean {
    return this.library.shouldGenerateEpubCover(book);
  }

  // ─── Navigation ───
  navigateToHome = (): void => { this.navigation.navigateToHome(); };
  navigateToLibrary = (): void => { this.navigation.navigateToLibrary(); };
  navigateToStats = (): void => { this.navigation.navigateToStats(); };
  navigateToHighlights = (): void => { this.navigation.navigateToHighlights(); };
  navigateToSettings = (): void => { this.navigation.navigateToSettings(); };
  backToHome = (): void => { this.navigation.backToHome(); };
  openDetails = (book: ReaderBook): void => { this.navigation.openDetails(book.id); };
  openShelfDetails = (book: ReaderBook): void => { this.navigation.openShelfDetails(book.id); };
  closeShelfDetails = (): void => { this.navigation.closeShelfDetails(); };
  setDomainUnavailable = (domain: "library" | "stats" | "search", reason: string | null): void => {
    this.navigation.setDomainUnavailable(domain, reason);
  };

  // ─── Library methods ───
  setShelfTab = (tab: (typeof this.library.SHELF_TAB_OPTIONS)[number]["key"]): void => {
    this.library.setShelfTab(tab);
  };
  setShelfSort = (sortKey: (typeof this.library.SHELF_SORT_OPTIONS)[number]["key"]): void => {
    this.library.setShelfSort(sortKey);
  };
  setShelfViewMode = (viewMode: "grid" | "list"): void => {
    this.library.setShelfViewMode(viewMode);
  };
  handleShelfQueryInput = (event: Event): void => { this.library.handleShelfQueryInput(event); };
  clearShelfQuery = (): void => { this.library.clearShelfQuery(); };
  handleEditBook = (book: ReaderBook): void => { this.library.handleEditBook(book); };
  handleSaveEditedBook = async (updatedBook: LibraryBookDto): Promise<void> => {
    await this.library.handleSaveEditedBook(updatedBook);
  };
  handleHideBook = async (book: ReaderBook): Promise<void> => {
    const snapshot = {
      route: this.navigation.route,
      previewBookId: this.navigation.previewBookId,
      activeReadingBookId: this.reader.activeReadingBookId,
      shelfDetailsBookId: this.navigation.shelfDetailsBookId,
    };

    await this.library.handleHideBook(book);

    // Consume reconciliation flags set by loadLibrary() inside handleHideBook
    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route;
      this.navigation.previewBookId = reconciled.previewBookId;
      this.reader.activeReadingBookId = reconciled.activeReadingBookId;
      this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }

    const error = this.library.consumeLastRecoverableError();
    if (error) {
      this.navigation.setDomainUnavailable("library", error.message);
    }
  };
  handleToggleFavorite = async (book: ReaderBook): Promise<void> => {
    await this.library.handleToggleFavorite(book);
  };
  handleMarkCompleted = async (book: ReaderBook): Promise<void> => {
    const snapshot = {
      route: this.navigation.route,
      previewBookId: this.navigation.previewBookId,
      activeReadingBookId: this.reader.activeReadingBookId,
      shelfDetailsBookId: this.navigation.shelfDetailsBookId,
    };

    await this.library.handleMarkCompleted(book);

    // Consume reconciliation flags set by loadLibrary() inside handleMarkCompleted
    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route;
      this.navigation.previewBookId = reconciled.previewBookId;
      this.reader.activeReadingBookId = reconciled.activeReadingBookId;
      this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }

    void this.statsDomain.loadStats(undefined);
  };

  // ─── Reader methods (delegated) ───
  handleReaderSettingsChange = (nextSettings: ReaderSettings): void => {
    this.settings.handleReaderSettingsChange(nextSettings);
  };
  handleLocaleChange = (nextLocale: UiLocale): void => {
    this.settings.handleLocaleChange(nextLocale);
  };
  handleReaderLocationContext = (): void => { this.reader.handleReaderLocationContext(); };

  // ─── Search methods ───
  handleSearch = async (query: string, page: number): Promise<void> => {
    if (!this.reader.activeReadingBookId) return;
    await this.search.handleSearch(this.reader.activeReadingBookId, query, page);
    // Propagate unavailable reason to navigation
    if (this.search.searchUnavailableReason) {
      this.navigation.setDomainUnavailable("search", this.search.searchUnavailableReason);
    }
  };
  handleSearchJump = (target: SearchNavigationTarget): void => {
    this.search.handleSearchJump(target);
  };

  // ─── Bulk import methods ───
  openBulkImportModal = (): void => { this.bulkImport.openBulkImportModal(); };
  closeBulkImportModal = (): void => { this.bulkImport.closeBulkImportModal(); };
  handlePickBulkImportFolder = async (): Promise<void> => {
    await this.bulkImport.handlePickBulkImportFolder(this.t("library.bulkImport.selectFolderTitle"));
  };
  handleScanBulkImportFolder = async (): Promise<void> => {
    await this.bulkImport.handleScanBulkImportFolder();
  };
  handleStartBulkImport = async (): Promise<void> => {
    await this.bulkImport.handleStartBulkImport();
  };
  handleCancelBulkImport = (): void => { this.bulkImport.handleCancelBulkImport(); };
  handleImportFile = async (): Promise<void> => {
    try {
      await this.bulkImport.handleImportFile();
    } catch (error) {
      this.library.readerError = error instanceof Error ? error.message : this.t("import.failed");
    }
  };

  // ─── Stats methods ───
  loadStats = async (bookId?: string): Promise<void> => {
    await this.statsDomain.loadStats(bookId);
    if (this.statsDomain.statsUnavailableReason) {
      this.navigation.setDomainUnavailable("stats", this.statsDomain.statsUnavailableReason);
    }
  };

  // ─── Cross-domain: loadLibrary ───
  async loadLibrary(): Promise<void> {
    const snapshot = {
      route: this.navigation.route,
      previewBookId: this.navigation.previewBookId,
      activeReadingBookId: this.reader.activeReadingBookId,
      shelfDetailsBookId: this.navigation.shelfDetailsBookId,
    };

    await this.library.loadLibrary();

    // Apply navigation reconciliation
    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route;
      this.navigation.previewBookId = reconciled.previewBookId;
      this.reader.activeReadingBookId = reconciled.activeReadingBookId;
      this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }

    // Apply recoverable error to navigation
    const error = this.library.consumeLastRecoverableError();
    if (error) {
      this.navigation.setDomainUnavailable("library", error.message);
    }
  }

  // ─── Cross-domain: startReading ───
  async startReading(book: ReaderBook): Promise<void> {
    this.library.promoteBookForReading(book.id);
    this.reader.activeReadingBookId = book.id;
    this.navigation.shelfDetailsBookId = null;
    this.navigation.route = "reader";
    this.search.resetSearch();

    this.library.recordReaderOpenMetric(book.format);

    await this.reader.startReading(book);
    void this.statsDomain.loadStats(book.id);
  }

  // ─── Cross-domain: progress handlers ───
  handleEpubLocationChange = async (nextLocation: string, nextPercentage: number): Promise<void> => {
    if (!this.reader.activeReadingBookId) return;
    await this.reader.handleEpubLocationChange(
      this.reader.activeReadingBookId,
      nextLocation,
      nextPercentage,
    );
  };

  handlePdfPageChange = async (page: number, total: number): Promise<void> => {
    if (!this.reader.activeReadingBookId) return;
    const activeId = this.reader.activeReadingBookId;

    // Update book page in library
    this.library.updateBookPage(activeId, page, total);

    await this.reader.handlePdfPageChange(activeId, page, total);
  };

  handlePdfSessionProgress = async (event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }): Promise<void> => {
    if (!this.reader.activeReadingBookId) return;
    await this.reader.handlePdfSessionProgress(this.reader.activeReadingBookId, event);
  };

  // ─── Cross-domain: init ───
  async init(): Promise<void> {
    initTheme();

    this.navigation.route = "home";
    this.navigation.shelfDetailsBookId = null;
    this.library.shelfQueryState = createShelfQueryState();
    this.navigation.previewBookId = null;
    this.library.readerError = null;
    this.bulkImport.isImporting = false;
    this.bulkImport.importProgress = null;

    try {
      const [nextLocale] = await Promise.all([
        i18n.initializeLocale(),
        this.settings.loadReaderSettings(),
        this.loadLibrary(),
        this.statsDomain.loadStats(undefined),
      ]);
      this.settings.locale = nextLocale;
    } catch (error) {
      console.error("Initialization error:", error);
      try {
        this.settings.locale = await i18n.initializeLocale();
      } catch {
        // last resort
      }
      this.settings.loadReaderSettings();
      this.loadLibrary();
      this.statsDomain.loadStats(undefined);
    }
  }
  // ─── Internal helpers ───
  // (removed _reconcileAfterBookChange — replaced by full reconcileHomeState in handleHideBook and handleMarkCompleted)
}

export const appState = new AppState();
