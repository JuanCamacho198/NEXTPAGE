import { i18n, type MessageKey } from '$lib/shared/i18n';
import { initTheme } from '$lib/shared/stores/theme';
import { reconcileHomeState } from '$lib/shared/stores/homeState';
import { navigationState } from '$lib/shared/stores/NavigationDomainState.svelte';
import { libraryState } from '$lib/shared/stores/LibraryDomainState.svelte';
import { readerState } from '$lib/shared/stores/ReaderDomainState.svelte';
import { searchState } from '$lib/shared/stores/SearchDomainState.svelte';
import { bulkImportState } from '$lib/shared/stores/BulkImportDomainState.svelte';
import { statsState } from '$lib/shared/stores/StatsDomainState.svelte';
import { settingsState } from '$lib/shared/stores/SettingsDomainState.svelte';
import { authState } from '$lib/stores/authState.svelte';
import { pushToast } from '$lib/stores/toastQueue.svelte';
import {
  clearPersistedAuth,
  loadDriveRefreshToken,
  loadPersistedAuth,
  type LocalUserProfile,
} from '$lib/stores/authPersistence';
import {
  getSessionClient,
  setLiveSession,
  clearLiveSession,
  getLiveSession,
} from '$lib/services/supabase';
import type { Session } from '@supabase/supabase-js';
import {
  signInAnonymously,
  restoreSession,
  signOut,
} from '$lib/shared/services/SupabaseAuthService';
import { SyncService } from '$lib/shared/services/SyncService';
import { dictionaryState } from '$lib/shared/stores/dictionaryState.svelte';
import { syncHealthState } from '$lib/shared/stores/syncHealthState.svelte';

import type {
  BulkImportSummary,
  CollectionDto,
  CommandErrorDto,
  LibraryBookDto,
  ReaderBook,
  ReaderSettings,
  ReadingStatsSummaryDto,
  ScanFolderResult,
  SearchBookTextResponse,
  SearchNavigationTarget,
  UiLocale,
} from '$lib/shared/types';
import {
  createShelfQueryState,
  type AppRoute,
  type ShelfQueryState,
} from '$lib/shared/stores/homeState';
import type { BulkImportProgress } from '$lib/shared/services/BulkImportService';
import type { ImportProgress } from '$lib/shared/services/BookImportService';

type MaybeCommandError = Error & { commandError?: CommandErrorDto };

export class AppState {
  // ─── Domain States ───
  navigation = navigationState;
  library = libraryState;
  reader = readerState;
  search = searchState;
  bulkImport = bulkImportState;
  settings = settingsState;
  // Stats domain: use statsDomain to avoid name collision with the stats getter
  statsDomain = statsState;

  constructor() {
    // Wire the bulk-import domain to this coordinator so single-file and
    // bulk imports refresh the in-memory library after the backend commits.
    // The callback was added in the bulk-import extraction commit but never
    // assigned here, so handleImportFile silently no-op'd the post-import
    // refresh and the user had to Ctrl+R to see the new book.
    this.bulkImport.onLibraryRefreshNeeded = () => this.loadLibrary();

    // D15 (REQ-refresh): make the reader's stats-refresh hook live — stats
    // and streak reload after a session save (236/248/274) and after a remote
    // session merge (subscribeToRemoteSessions). userId is passed through so
    // the streak is scoped to the current account (legacy rows stay '').
    this.reader.onStatsRefreshNeeded = async (bookId) => {
      await this.statsDomain.loadStats(bookId);
      await this.statsDomain.loadStreak(bookId, authState.userId ?? '');
    };
  }

  // ─── Init gate ───
  /**
   * `false` until `init()` resolves. The AppRouter shows a brief loader
   * while false so returning users do not see a flash of the welcome
   * screen during the async cache read.
   */
  isInitialized = $state(false);

  // ─── Property passthrough: Navigation ───
  get route(): AppRoute {
    return this.navigation.route;
  }
  set route(v: AppRoute) {
    this.navigation.route = v;
  }
  get previewBookId(): string | null {
    return this.navigation.previewBookId;
  }
  set previewBookId(v: string | null) {
    this.navigation.previewBookId = v;
  }
  get shelfDetailsBookId(): string | null {
    return this.navigation.shelfDetailsBookId;
  }
  set shelfDetailsBookId(v: string | null) {
    this.navigation.shelfDetailsBookId = v;
  }
  get libraryUnavailableReason(): string | null {
    return this.navigation.libraryUnavailableReason;
  }
  set libraryUnavailableReason(v: string | null) {
    this.navigation.libraryUnavailableReason = v;
  }
  get statsUnavailableReason(): string | null {
    return this.navigation.statsUnavailableReason;
  }
  set statsUnavailableReason(v: string | null) {
    this.navigation.statsUnavailableReason = v;
  }
  get searchUnavailableReason(): string | null {
    return this.navigation.searchUnavailableReason;
  }
  set searchUnavailableReason(v: string | null) {
    this.navigation.searchUnavailableReason = v;
  }

  // ─── Property passthrough: Library ───
  get books(): ReaderBook[] {
    return this.library.books;
  }
  set books(v: ReaderBook[]) {
    this.library.books = v;
  }
  get shelfQueryState(): ShelfQueryState {
    return this.library.shelfQueryState;
  }
  set shelfQueryState(v: ShelfQueryState) {
    this.library.shelfQueryState = v;
  }
  get collections(): CollectionDto[] {
    return this.library.collections;
  }
  set collections(v: CollectionDto[]) {
    this.library.collections = v;
  }
  get isLoadingLibrary(): boolean {
    return this.library.isLoadingLibrary;
  }
  set isLoadingLibrary(v: boolean) {
    this.library.isLoadingLibrary = v;
  }
  get readerError(): string | null {
    return this.library.readerError;
  }
  set readerError(v: string | null) {
    this.library.readerError = v;
  }
  get editingBook(): ReaderBook | null {
    return this.library.editingBook;
  }
  set editingBook(v: ReaderBook | null) {
    this.library.editingBook = v;
  }
  get pendingRemoveBook(): ReaderBook | null {
    return this.library.pendingRemoveBook;
  }
  set pendingRemoveBook(v: ReaderBook | null) {
    this.library.pendingRemoveBook = v;
  }
  get isCollectionManagerOpen(): boolean {
    return this.library.isCollectionManagerOpen;
  }
  set isCollectionManagerOpen(v: boolean) {
    this.library.isCollectionManagerOpen = v;
  }

  get continueReadingBooks(): ReaderBook[] {
    return this.library.continueReadingBooks;
  }
  get myShelfBooks(): ReaderBook[] {
    return this.library.myShelfBooks;
  }
  get shelfBooks(): ReaderBook[] {
    return this.library.shelfBooks;
  }
  get shelfWarnings(): string[] {
    return this.library.shelfWarnings;
  }
  get shelfSortToken(): string | null {
    return this.library.shelfSortToken;
  }
  selectedShelfBook = $derived.by(() => {
    if (!this.navigation.shelfDetailsBookId) return null;
    return this.books.find((book) => book.id === this.navigation.shelfDetailsBookId) ?? null;
  });

  // ─── Property passthrough: Reader ───
  get activeReadingBookId(): string | null {
    return this.reader.activeReadingBookId;
  }
  set activeReadingBookId(v: string | null) {
    this.reader.activeReadingBookId = v;
  }
  get cfiLocation(): string {
    return this.reader.cfiLocation;
  }
  set cfiLocation(v: string) {
    this.reader.cfiLocation = v;
  }
  get percentage(): number {
    return this.reader.percentage;
  }
  set percentage(v: number) {
    this.reader.percentage = v;
  }
  get preloadedBytes(): { filePath: string; data: Uint8Array } | null {
    return this.reader.preloadedBytes;
  }
  set preloadedBytes(v: { filePath: string; data: Uint8Array } | null) {
    this.reader.preloadedBytes = v;
  }

  // ─── Property passthrough: Search ───
  get searchResponse(): SearchBookTextResponse | null {
    return this.search.searchResponse;
  }
  set searchResponse(v: SearchBookTextResponse | null) {
    this.search.searchResponse = v;
  }
  get searchTargetLocator(): string | null {
    return this.search.searchTargetLocator;
  }
  set searchTargetLocator(v: string | null) {
    this.search.searchTargetLocator = v;
  }
  get isSearching(): boolean {
    return this.search.isSearching;
  }
  set isSearching(v: boolean) {
    this.search.isSearching = v;
  }

  // ─── Property passthrough: BulkImport ───
  get isBulkImportOpen(): boolean {
    return this.bulkImport.isBulkImportOpen;
  }
  set isBulkImportOpen(v: boolean) {
    this.bulkImport.isBulkImportOpen = v;
  }
  get isBulkScanning(): boolean {
    return this.bulkImport.isBulkScanning;
  }
  set isBulkScanning(v: boolean) {
    this.bulkImport.isBulkScanning = v;
  }
  get isBulkImporting(): boolean {
    return this.bulkImport.isBulkImporting;
  }
  set isBulkImporting(v: boolean) {
    this.bulkImport.isBulkImporting = v;
  }
  get bulkImportFolderPath(): string | null {
    return this.bulkImport.bulkImportFolderPath;
  }
  set bulkImportFolderPath(v: string | null) {
    this.bulkImport.bulkImportFolderPath = v;
  }
  get bulkImportFolderName(): string | null {
    return this.bulkImport.bulkImportFolderName;
  }
  set bulkImportFolderName(v: string | null) {
    this.bulkImport.bulkImportFolderName = v;
  }
  get bulkScanResult(): ScanFolderResult | null {
    return this.bulkImport.bulkScanResult;
  }
  set bulkScanResult(v: ScanFolderResult | null) {
    this.bulkImport.bulkScanResult = v;
  }
  get bulkScanError(): string | null {
    return this.bulkImport.bulkScanError;
  }
  set bulkScanError(v: string | null) {
    this.bulkImport.bulkScanError = v;
  }
  get bulkImportProgress(): BulkImportProgress | null {
    return this.bulkImport.bulkImportProgress;
  }
  set bulkImportProgress(v: BulkImportProgress | null) {
    this.bulkImport.bulkImportProgress = v;
  }
  get bulkImportSummary(): BulkImportSummary | null {
    return this.bulkImport.bulkImportSummary;
  }
  set bulkImportSummary(v: BulkImportSummary | null) {
    this.bulkImport.bulkImportSummary = v;
  }
  get isImporting(): boolean {
    return this.bulkImport.isImporting;
  }
  set isImporting(v: boolean) {
    this.bulkImport.isImporting = v;
  }
  get importProgress(): ImportProgress | null {
    return this.bulkImport.importProgress;
  }
  set importProgress(v: ImportProgress | null) {
    this.bulkImport.importProgress = v;
  }

  // ─── Property passthrough: Stats ───
  get stats(): ReadingStatsSummaryDto | null {
    return this.statsDomain.stats;
  }
  set stats(v: ReadingStatsSummaryDto | null) {
    this.statsDomain.stats = v;
  }
  get isLoadingStats(): boolean {
    return this.statsDomain.isLoadingStats;
  }
  set isLoadingStats(v: boolean) {
    this.statsDomain.isLoadingStats = v;
  }

  // ─── Property passthrough: Settings ───
  get locale(): UiLocale {
    return this.settings.locale;
  }
  set locale(v: UiLocale) {
    this.settings.locale = v;
  }
  get readerSettings(): ReaderSettings {
    return this.settings.readerSettings;
  }
  set readerSettings(v: ReaderSettings) {
    this.settings.readerSettings = v;
  }

  // ─── Constants ───
  readonly SHELF_TAB_OPTIONS = this.library.SHELF_TAB_OPTIONS;
  readonly SHELF_SORT_OPTIONS = this.library.SHELF_SORT_OPTIONS;
  readonly DOMAIN = navigationState.DOMAIN;

  // ─── i18n helper ───
  t = (key: MessageKey, params?: Record<string, string | number>): string =>
    i18n.t(this.settings.locale, key, params);

  // ─── Utility ───
  mapCommandError(error: unknown): CommandErrorDto {
    const typed = error as MaybeCommandError;
    if (typed.commandError) return typed.commandError;

    const fallback = error instanceof Error ? error.message : this.t('errors.commandFailure');
    return { code: 'INTERNAL_ERROR', message: fallback, recoverable: false };
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

  hasResolvedCoverPath(book: Pick<LibraryBookDto, 'coverPath'>): boolean {
    return this.library.hasResolvedCoverPath(book);
  }

  shouldGeneratePdfCover(book: ReaderBook): boolean {
    return this.library.shouldGeneratePdfCover(book);
  }

  shouldGenerateEpubCover(book: ReaderBook): boolean {
    return this.library.shouldGenerateEpubCover(book);
  }

  // ─── Navigation ───
  navigateToHome = (): void => {
    this.navigation.navigateToHome();
  };
  navigateToLibrary = (): void => {
    this.navigation.navigateToLibrary();
  };
  navigateToStats = (): void => {
    this.navigation.navigateToStats();
  };
  navigateToHighlights = (): void => {
    this.navigation.navigateToHighlights();
  };
  navigateToSettings = (): void => {
    this.navigation.navigateToSettings();
  };
  navigateToDictionary = (): void => {
    this.navigation.navigateToDictionary();
  };
  navigateToStorage = (): void => {
    this.navigation.navigateToStorage();
  };
  navigateToSync = (): void => {
    this.navigation.navigateToSync();
  };
  /**
   * Route to the welcome screen. Used after sign-out so the user lands
   * on the first-launch experience.
   */
  navigateToWelcome = (): void => {
    this.navigation.route = 'welcome';
  };
  backToHome = (): void => {
    this.navigation.backToHome();
  };
  openDetails = (book: ReaderBook): void => {
    this.navigation.openDetails(book.id);
  };
  openShelfDetails = (book: ReaderBook): void => {
    this.navigation.openShelfDetails(book.id);
  };
  closeShelfDetails = (): void => {
    this.navigation.closeShelfDetails();
  };
  setDomainUnavailable = (domain: 'library' | 'stats' | 'search', reason: string | null): void => {
    this.navigation.setDomainUnavailable(domain, reason);
  };

  // ─── Library methods ───
  setShelfTab = (tab: (typeof this.library.SHELF_TAB_OPTIONS)[number]['key']): void => {
    this.library.setShelfTab(tab);
  };
  setShelfSort = (sortKey: (typeof this.library.SHELF_SORT_OPTIONS)[number]['key']): void => {
    this.library.setShelfSort(sortKey);
  };
  setShelfViewMode = (viewMode: 'grid' | 'list'): void => {
    this.library.setShelfViewMode(viewMode);
  };
  handleShelfQueryInput = (event: Event): void => {
    this.library.handleShelfQueryInput(event);
  };
  clearShelfQuery = (): void => {
    this.library.clearShelfQuery();
  };
  handleEditBook = (book: ReaderBook): void => {
    this.library.handleEditBook(book);
  };
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
      this.navigation.setDomainUnavailable('library', error.message);
    }
  };
  /**
   * Open the 2-step removal modal for a book (REQ-09): both shelf entry points
   * (LibraryShelfScreen.onRemoveBook and ShelfActionMenu.onRemove) route here.
   * `handleHideBook` remains the "Local only" path chosen inside the modal.
   */
  requestRemoveBook = (book: ReaderBook): void => {
    this.library.pendingRemoveBook = book;
  };
  /**
   * "Local + Drive" removal (REQ-11): delegates the trash → tombstone → hide
   * flow to LibraryDomainState. A Drive failure aborts and surfaces a typed
   * error (readerError / auth banner); later failures are reported without
   * blocking. The modal closes so the user can re-decide (e.g. "Local only").
   */
  handleRemoveBookFromDrive = async (book: ReaderBook): Promise<void> => {
    const snapshot = {
      route: this.navigation.route,
      previewBookId: this.navigation.previewBookId,
      activeReadingBookId: this.reader.activeReadingBookId,
      shelfDetailsBookId: this.navigation.shelfDetailsBookId,
    };

    try {
      await this.library.handleRemoveBookFromDrive(book);
    } catch {
      // Drive abort: the error is already surfaced (readerError / auth banner).
      // Keep the flow moving so the modal closes and the user can re-decide.
    }

    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route;
      this.navigation.previewBookId = reconciled.previewBookId;
      this.reader.activeReadingBookId = reconciled.activeReadingBookId;
      this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }

    const error = this.library.consumeLastRecoverableError();
    if (error) {
      this.navigation.setDomainUnavailable('library', error.message);
    }

    this.library.pendingRemoveBook = null;
  };
  handleToggleFavorite = async (book: ReaderBook): Promise<void> => {
    await this.library.handleToggleFavorite(book);
  };
  handleDeleteCover = async (book: ReaderBook): Promise<void> => {
    await this.library.handleDeleteCover(book);
  };
  handleStatusChange = async (book: ReaderBook, status: string): Promise<void> => {
    const snapshot = {
      route: this.navigation.route,
      previewBookId: this.navigation.previewBookId,
      activeReadingBookId: this.reader.activeReadingBookId,
      shelfDetailsBookId: this.navigation.shelfDetailsBookId,
    };

    await this.library.handleStatusChange(book, status as 'to_read' | 'reading' | 'completed');

    // Consume reconciliation flags set by loadLibrary() inside handleStatusChange
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
  handleReaderLocationContext = (ctx?: unknown): void => {
    this.reader.handleReaderLocationContext(ctx);
  };

  // ─── Search methods ───
  handleSearch = async (query: string, page: number): Promise<void> => {
    if (!this.reader.activeReadingBookId) return;
    await this.search.handleSearch(this.reader.activeReadingBookId, query, page);
    // Propagate unavailable reason to navigation
    if (this.search.searchUnavailableReason) {
      this.navigation.setDomainUnavailable('search', this.search.searchUnavailableReason);
    }
  };
  handleSearchJump = (target: SearchNavigationTarget): void => {
    this.search.handleSearchJump(target);
  };

  // ─── Bulk import methods ───
  openBulkImportModal = (): void => {
    this.bulkImport.openBulkImportModal();
  };
  closeBulkImportModal = (): void => {
    this.bulkImport.closeBulkImportModal();
  };
  handlePickBulkImportFolder = async (): Promise<void> => {
    await this.bulkImport.handlePickBulkImportFolder(
      this.t('library.bulkImport.selectFolderTitle'),
    );
  };
  handleScanBulkImportFolder = async (): Promise<void> => {
    await this.bulkImport.handleScanBulkImportFolder();
  };
  handleStartBulkImport = async (): Promise<void> => {
    await this.bulkImport.handleStartBulkImport();
  };
  handleCancelBulkImport = (): void => {
    this.bulkImport.handleCancelBulkImport();
  };
  handleImportFile = async (): Promise<void> => {
    try {
      await this.bulkImport.handleImportFile();
    } catch (error) {
      this.library.readerError = error instanceof Error ? error.message : this.t('import.failed');
    }
  };

  // ─── Stats methods ───
  loadStats = async (bookId?: string): Promise<void> => {
    await this.statsDomain.loadStats(bookId);
    if (this.statsDomain.statsUnavailableReason) {
      this.navigation.setDomainUnavailable('stats', this.statsDomain.statsUnavailableReason);
    }
  };

  loadStatsActivity = async (
    period: string,
    granularity: string,
    bookId?: string,
  ): Promise<void> => {
    return this.statsDomain.loadActivity(period, granularity, bookId);
  };

  loadStatsRange = async (
    from: string,
    to: string,
    bookId?: string,
    target?: 'current' | 'previous',
  ): Promise<void> => {
    return this.statsDomain.loadRangeStats(from, to, bookId, target);
  };

  loadStatsStreak = async (bookId?: string): Promise<void> => {
    return this.statsDomain.loadStreak(bookId, authState.userId ?? '');
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
      this.navigation.setDomainUnavailable('library', error.message);
    }
  }

  // ─── Cross-domain: startReading ───
  async startReading(book: ReaderBook): Promise<void> {
    this.library.promoteBookForReading(book.id);
    this.reader.activeReadingBookId = book.id;
    this.navigation.shelfDetailsBookId = null;
    this.navigation.route = 'reader';
    this.search.resetSearch();

    this.library.recordReaderOpenMetric(book.format);

    await this.reader.startReading(book);
    void this.statsDomain.loadStats(book.id);
  }

  // ─── Cross-domain: progress handlers ───
  handleEpubLocationChange = async (
    nextLocation: string,
    nextPercentage: number,
  ): Promise<void> => {
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

    this.navigation.shelfDetailsBookId = null;
    this.library.shelfQueryState = createShelfQueryState();
    this.navigation.previewBookId = null;
    this.library.readerError = null;
    this.bulkImport.isImporting = false;
    this.bulkImport.importProgress = null;

    // Decide the initial route BEFORE setting `isInitialized = true` so the
    // router never renders a flash of the wrong screen. Cache read failures
    // are non-fatal: the app degrades to the welcome screen.
    let initialRoute: AppRoute = 'welcome';
    let restoredAuthenticatedSession = false;
    try {
      // 1. Try restoring a Supabase session (persisted via TauriStorage adapter)
      const supabaseSession = await restoreSession();
      if (supabaseSession) {
        restoredAuthenticatedSession = true;
        // auth.json is the durable store for provider_refresh_token (written at
        // sign-in; survives supabase-js auto-refresh) — restore it for Drive
        // token refresh after restart (DTL-1).
        const driveRefreshToken = await loadDriveRefreshToken();
        // Mirror the live session in the module-level cache BEFORE any gate
        // runs; the INITIAL_SESSION event after subscribe is idempotent with
        // this (D1/D2).
        setLiveSession(supabaseSession);
        this.hydrateAuthState(supabaseSession, driveRefreshToken);

        this.startAuthenticatedSync();

        initialRoute = 'home';
      } else {
        // 2. Fallback: check persisted auth cache (local user profile)
        const cached = await loadPersistedAuth();
        if (cached) {
          if (cached.kind === 'local') {
            authState.setLocalUser(cached.profile satisfies LocalUserProfile);
            initialRoute = 'home';
          }
          // Legacy 'google' kind is discarded per MG-01
        } else {
          // 3. No session at all — sign in anonymously for RLS context.
          // signInAnonymously() itself no-ops when a live session exists (DA-3).
          await signInAnonymously();
        }
      }
    } catch (error) {
      console.error('Failed to read auth cache during init:', error);
      // Ensure we have at least an anon session — but ONLY when no live
      // session or profile exists (DA-3.1/DA-3.2). Never let the catch path
      // clobber a real persisted session with an anonymous one.
      if (getLiveSession() === null && authState.userId === null) {
        try {
          await signInAnonymously();
        } catch {
          // silent
        }
      }
    }
    this.navigation.route = initialRoute;

    // Start outbox processor and catalog sync whenever a Supabase session
    // exists (restored session, anonymous sign-in, or error recovery).
    // Idempotent — safe if already started in the restored-session branch.
    SyncService.setupOutboxProcessor();
    if (authState.userId && !restoredAuthenticatedSession) {
      SyncService.syncBookCatalog(); // fire-and-forget
    }

    // Subscribe to auth state changes so OAuth sign-ins that complete during
    // runtime (via the loopback callback handler) trigger a navigation to home.
    // The live session mirror (liveSessionCache) is maintained here — this is
    // the single onAuthStateChange subscription in the app (D1).
    getSessionClient().auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_IN' && session) {
        setLiveSession(session);
        SyncService.resetOutboxBreaker();
        this.startAuthenticatedSync();
        // PR3: auto-sync wiring
        try {
          SyncService.setupAutoSync();
          void syncHealthState.refresh();
          syncHealthState.startPoll();
        } catch {}
        // PR1: opt-in dictionary realtime (default on when hasLiveSession)
        try {
          dictionaryState.subscribeToRemoteChanges();
        } catch {}
        if (this.navigation.route === 'welcome') {
          this.navigation.route = 'home';
          this.loadLibrary();
          this.statsDomain.loadStats(undefined);
        }
        // reading-daily-goal: load per-user goal + today minutes
        void this.loadDailyGoalForCurrentUser();
        return;
      }

      if (event === 'SIGNED_OUT') {
        clearLiveSession();
        authState.clearSupabaseSession();
        try {
          SyncService.teardownAutoSync();
          syncHealthState.stopPoll();
        } catch {}
        this.reader.unsubscribeFromAllRemoteChanges();
        try {
          dictionaryState.unsubscribe();
        } catch {}
        this.settings.clearDailyGoal();
        this.statsDomain.clearTodayMinutes();
        this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
        this.navigateToWelcome();
        return;
      }

      if (event === 'TOKEN_REFRESHED' && session) {
        // Access token rotated: refresh the mirror + authState tokens only;
        // isSignedIn stays true and sync continues (DA-2.2). Never re-runs
        // startAuthenticatedSync() — no double startup sync (D2).
        setLiveSession(session);
        // D4: the rotation is itself the auth recovery — clear any breaker pause.
        SyncService.resetOutboxBreaker();
        this.hydrateAuthState(
          session,
          authState.driveRefreshToken ?? session.provider_refresh_token ?? null,
        );
        return;
      }

      if (event === 'INITIAL_SESSION') {
        // Emitted on subscribe with the stored session (or null). Hydrate-only:
        // init() already restored + synced exactly once, so NEVER
        // startAuthenticatedSync() here (D2/DA-2.3) — this is what prevents the
        // double startup sync. Hydrate authState only when it has no user yet.
        if (session) {
          setLiveSession(session);
          if (authState.userId === null) {
            this.hydrateAuthState(session, session.provider_refresh_token ?? null);
          }
        } else {
          clearLiveSession();
        }
      }
    });

    try {
      const [nextLocale] = await Promise.all([
        i18n.initializeLocale(),
        this.settings.loadReaderSettings(),
        this.loadLibrary(),
        this.statsDomain.loadStats(undefined),
      ]);
      this.settings.locale = nextLocale;
    } catch (error) {
      console.error('Initialization error:', error);
      try {
        this.settings.locale = await i18n.initializeLocale();
      } catch {
        // last resort
      }
      this.settings.loadReaderSettings();
      this.loadLibrary();
      this.statsDomain.loadStats(undefined);
    } finally {
      // reading-daily-goal: load goal for restored/local user after init
      void this.loadDailyGoalForCurrentUser();
      this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
      try {
        const uid = authState.userId;
        if (uid) void this.statsDomain.loadTodayMinutes(uid);
      } catch {
        // ignore
      }
      this.isInitialized = true;
    }
  }

  /**
   * Sign-out helper: clear the in-memory auth state, drop the persisted
   * cache, and route to the welcome screen. Safe to call when no auth is
   * present (no-op for the relevant setter).
   */
  signOutAndReturnToWelcome = async (): Promise<void> => {
    authState.clearLocalUser();
    authState.clearSupabaseSession();
    await clearPersistedAuth();
    await signOut();
    try {
      SyncService.teardownAutoSync();
      syncHealthState.stopPoll();
    } catch {}
    this.reader.unsubscribeFromAllRemoteChanges();
    try {
      dictionaryState.unsubscribe();
    } catch {}
    this.settings.clearDailyGoal();
    this.statsDomain.clearTodayMinutes();
    this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
    this.navigateToWelcome();
    // Toast survives the panel unmount because ToastHost lives outside AppRouter
    // in AppModals (REQ-05).
    pushToast('success', this.t('welcome.signedOutToast'));
  };
  private startAuthenticatedSync(): void {
    SyncService.setupOutboxProcessor();
    // PR3 auto-sync wiring: setup interval + health poll
    try {
      SyncService.setupAutoSync();
      void syncHealthState.refresh();
      syncHealthState.startPoll();
    } catch {}
    this.reader.subscribeToAllRemoteChanges();
    try {
      dictionaryState.subscribeToRemoteChanges();
    } catch {}
    void SyncService.syncMetadata().catch((error: unknown) => {
      console.error('Startup sync failed; continuing offline:', error);
    });
  }
  // ─── reading-daily-goal helpers ───
  private async loadDailyGoalForCurrentUser(): Promise<void> {
    const uid = authState.userId;
    if (!uid || uid.trim().length === 0) {
      this.settings.clearDailyGoal();
      this.statsDomain.clearTodayMinutes();
      this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
      return;
    }
    try {
      await this.settings.loadDailyGoalMinutes(uid);
    } catch {
      // keep default
    }
    this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
    try {
      await this.statsDomain.loadTodayMinutes(uid);
    } catch {
      // ignore
    }
  }

  async saveDailyGoalMinutes(minutes: number): Promise<void> {
    const uid = authState.userId;
    if (!uid || uid.trim().length === 0) return;
    await this.settings.saveDailyGoalMinutes(minutes, uid);
    this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
    // refresh today minutes after save to ensure progress recalculates with new denominator
    try {
      await this.statsDomain.loadTodayMinutes(uid);
    } catch {}
  }

  async refreshTodayMinutes(): Promise<void> {
    const uid = authState.userId;
    if (!uid) {
      this.statsDomain.clearTodayMinutes();
      return;
    }
    await this.statsDomain.loadTodayMinutes(uid);
  }

  /**
   * Map a live supabase-js session into the public `authState` shape.
   * Shared by the init restore path and the auth lifecycle handler so every
   * hydration site maps user metadata identically.
   */
  private hydrateAuthState(session: Session, driveRefreshToken: string | null): void {
    authState.setSupabaseSession({
      accessToken: session.access_token,
      refreshToken: session.refresh_token,
      expiresAt: session.expires_at ? session.expires_at * 1000 : null,
      userId: session.user.id,
      email: session.user.email ?? null,
      displayName:
        session.user.user_metadata?.full_name ?? session.user.user_metadata?.name ?? null,
      photoUrl:
        session.user.user_metadata?.avatar_url ?? session.user.user_metadata?.picture ?? null,
      providerToken: session.provider_token ?? null,
      driveRefreshToken,
    });
  }
  // ─── Internal helpers ───
  // (removed _reconcileAfterBookChange — replaced by full reconcileHomeState in handleHideBook and handleMarkCompleted)
}

export const appState = new AppState();
