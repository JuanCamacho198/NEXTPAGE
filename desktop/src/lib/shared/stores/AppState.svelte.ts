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
import { authState, setSupabaseSession } from '$lib/stores/authState.svelte';
import {
  clearPersistedAuth,
  loadPersistedAuth,
  type LocalUserProfile,
} from '$lib/stores/authPersistence';
import { getSessionClient } from '$lib/services/supabase';
import { signInAnonymously, restoreSession, signOut } from '$lib/shared/services/SupabaseAuthService';
import { SyncService } from '$lib/shared/services/SyncService';

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
    return this.statsDomain.loadStreak(bookId);
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
        setSupabaseSession({
          accessToken: supabaseSession.access_token,
          refreshToken: supabaseSession.refresh_token,
          expiresAt: supabaseSession.expires_at ? supabaseSession.expires_at * 1000 : null,
          userId: supabaseSession.user.id,
          email: supabaseSession.user.email ?? null,
          displayName:
            supabaseSession.user.user_metadata?.full_name ??
            supabaseSession.user.user_metadata?.name ??
            null,
          photoUrl:
            supabaseSession.user.user_metadata?.avatar_url ??
            supabaseSession.user.user_metadata?.picture ??
            null,
          providerToken: supabaseSession.provider_token ?? null,
        });

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
          // 3. No session at all — sign in anonymously for RLS context
          await signInAnonymously();
        }
      }
    } catch (error) {
      console.error('Failed to read auth cache during init:', error);
      // Ensure we have at least an anon session
      try {
        await signInAnonymously();
      } catch {
        // silent
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
    getSessionClient().auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_IN' && session) {
        this.startAuthenticatedSync();

        // Only navigate away from welcome — if already elsewhere, stay put
        if (this.navigation.route === 'welcome') {
          this.navigation.route = 'home';
          this.loadLibrary();
          this.statsDomain.loadStats(undefined);
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
    this.reader.unsubscribeFromAllRemoteChanges();
    this.navigateToWelcome();
  };
  private startAuthenticatedSync(): void {
    SyncService.setupOutboxProcessor();
    this.reader.subscribeToAllRemoteChanges();
    void SyncService.syncMetadata().catch((error: unknown) => {
      console.error('Startup sync failed; continuing offline:', error);
    });
  }
  // ─── Internal helpers ───
  // (removed _reconcileAfterBookChange — replaced by full reconcileHomeState in handleHideBook and handleMarkCompleted)
}

export const appState = new AppState();
