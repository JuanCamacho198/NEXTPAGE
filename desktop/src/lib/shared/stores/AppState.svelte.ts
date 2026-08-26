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
import { clearPersistedAuth, loadDriveRefreshToken, loadPersistedAuth, type LocalUserProfile } from '$lib/stores/authPersistence';
import { getSessionClient, setLiveSession, clearLiveSession, getLiveSession } from '$lib/services/supabase';
import type { Session } from '@supabase/supabase-js';
import { signInAnonymously, restoreSession, signOut } from '$lib/shared/services/SupabaseAuthService';
import { SyncService } from '$lib/shared/services/SyncService';
import { dictionaryState } from '$lib/shared/stores/dictionaryState.svelte';
import { syncHealthState } from '$lib/shared/stores/syncHealthState.svelte';
import type { BulkImportSummary, CollectionDto, CommandErrorDto, LibraryBookDto, ReaderBook, ReaderSettings, ReadingStatsSummaryDto, ScanFolderResult, SearchBookTextResponse, SearchNavigationTarget, UiLocale } from '$lib/shared/types';
import { createShelfQueryState, type AppRoute, type ShelfQueryState } from '$lib/shared/stores/homeState';
import type { BulkImportProgress } from '$lib/shared/services/BulkImportService';
import type { ImportProgress } from '$lib/shared/services/BookImportService';

type MaybeCommandError = Error & { commandError?: CommandErrorDto };

function warn(key: string): void {
  console.warn(`[deprecated] appState.${key} -> use domain state directly`);
}

export class AppState {
  navigation = navigationState;
  library = libraryState;
  reader = readerState;
  search = searchState;
  bulkImport = bulkImportState;
  settings = settingsState;
  statsDomain = statsState;

  constructor() {
    this.bulkImport.onLibraryRefreshNeeded = () => this.loadLibrary();
    this.reader.onStatsRefreshNeeded = async (bookId) => {
      await this.statsDomain.loadStats(bookId);
      await this.statsDomain.loadStreak(bookId, authState.userId ?? '');
    };
  }

  isInitialized = $state(false);

  // ─── i18n helper (kept) ───
  t = (key: MessageKey, params?: Record<string, string | number>): string => i18n.t(this.settings.locale, key, params);

  mapCommandError(error: unknown): CommandErrorDto {
    const typed = error as MaybeCommandError;
    if (typed.commandError) return typed.commandError;
    const fallback = error instanceof Error ? error.message : this.t('errors.commandFailure');
    return { code: 'INTERNAL_ERROR', message: fallback, recoverable: false };
  }

  // ─── Cross-domain coordinators (kept) ───
  async loadLibrary(): Promise<void> {
    const snapshot = {
      route: this.navigation.route,
      previewBookId: this.navigation.previewBookId,
      activeReadingBookId: this.reader.activeReadingBookId,
      shelfDetailsBookId: this.navigation.shelfDetailsBookId,
    };
    await this.library.loadLibrary();
    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route;
      this.navigation.previewBookId = reconciled.previewBookId;
      this.reader.activeReadingBookId = reconciled.activeReadingBookId;
      this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }
    const error = this.library.consumeLastRecoverableError();
    if (error) this.navigation.setDomainUnavailable('library', error.message);
  }

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

  async handleEpubLocationChange(nextLocation: string, nextPercentage: number): Promise<void> {
    if (!this.reader.activeReadingBookId) return;
    await this.reader.handleEpubLocationChange(this.reader.activeReadingBookId, nextLocation, nextPercentage);
  }

  async handlePdfPageChange(page: number, total: number): Promise<void> {
    if (!this.reader.activeReadingBookId) return;
    const activeId = this.reader.activeReadingBookId;
    this.library.updateBookPage(activeId, page, total);
    await this.reader.handlePdfPageChange(activeId, page, total);
  }

  async handlePdfSessionProgress(event: { startedAt: string; endedAt?: string; durationSeconds: number; startPercentage?: number; endPercentage?: number }): Promise<void> {
    if (!this.reader.activeReadingBookId) return;
    await this.reader.handlePdfSessionProgress(this.reader.activeReadingBookId, event);
  }

  // ─── Init (kept, trimmed comments) ───
  async init(): Promise<void> {
    initTheme();
    this.navigation.shelfDetailsBookId = null;
    this.library.shelfQueryState = createShelfQueryState();
    this.navigation.previewBookId = null;
    this.library.readerError = null;
    this.bulkImport.isImporting = false;
    this.bulkImport.importProgress = null;
    let initialRoute: AppRoute = 'welcome';
    let restoredAuthenticatedSession = false;
    try {
      const supabaseSession = await restoreSession();
      if (supabaseSession) {
        restoredAuthenticatedSession = true;
        const driveRefreshToken = await loadDriveRefreshToken();
        setLiveSession(supabaseSession);
        this.hydrateAuthState(supabaseSession, driveRefreshToken);
        this.startAuthenticatedSync();
        initialRoute = 'home';
      } else {
        const cached = await loadPersistedAuth();
        if (cached) {
          if (cached.kind === 'local') {
            authState.setLocalUser(cached.profile satisfies LocalUserProfile);
            initialRoute = 'home';
          }
        } else {
          await signInAnonymously();
        }
      }
    } catch (error) {
      console.error('Failed to read auth cache during init:', error);
      if (getLiveSession() === null && authState.userId === null) {
        try { await signInAnonymously(); } catch {}
      }
    }
    this.navigation.route = initialRoute;
    SyncService.setupOutboxProcessor();
    if (authState.userId && !restoredAuthenticatedSession) SyncService.syncBookCatalog();
    getSessionClient().auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_IN' && session) {
        setLiveSession(session);
        SyncService.resetOutboxBreaker();
        this.startAuthenticatedSync();
        try { SyncService.setupAutoSync(); void syncHealthState.refresh(); syncHealthState.startPoll(); } catch {}
        try { dictionaryState.subscribeToRemoteChanges(); } catch {}
        if (this.navigation.route === 'welcome') { this.navigation.route = 'home'; this.loadLibrary(); this.statsDomain.loadStats(undefined); }
        void this.loadDailyGoalForCurrentUser();
        return;
      }
      if (event === 'SIGNED_OUT') {
        clearLiveSession(); authState.clearSupabaseSession();
        try { SyncService.teardownAutoSync(); syncHealthState.stopPoll(); } catch {}
        this.reader.unsubscribeFromAllRemoteChanges();
        try { dictionaryState.unsubscribe(); } catch {}
        this.settings.clearDailyGoal(); this.statsDomain.clearTodayMinutes(); this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
        this.navigateToWelcome(); return;
      }
      if (event === 'TOKEN_REFRESHED' && session) {
        setLiveSession(session); SyncService.resetOutboxBreaker();
        this.hydrateAuthState(session, authState.driveRefreshToken ?? session.provider_refresh_token ?? null); return;
      }
      if (event === 'INITIAL_SESSION') {
        if (session) { setLiveSession(session); if (authState.userId === null) this.hydrateAuthState(session, session.provider_refresh_token ?? null); }
        else clearLiveSession();
      }
    });
    try {
      const [nextLocale] = await Promise.all([i18n.initializeLocale(), this.settings.loadReaderSettings(), this.loadLibrary(), this.statsDomain.loadStats(undefined)]);
      this.settings.locale = nextLocale;
    } catch (error) {
      console.error('Initialization error:', error);
      try { this.settings.locale = await i18n.initializeLocale(); } catch {}
      this.settings.loadReaderSettings(); this.loadLibrary(); this.statsDomain.loadStats(undefined);
    } finally {
      void this.loadDailyGoalForCurrentUser();
      this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
      try { const uid = authState.userId; if (uid) void this.statsDomain.loadTodayMinutes(uid); } catch {}
      this.isInitialized = true;
    }
  }

  signOutAndReturnToWelcome = async (): Promise<void> => {
    authState.clearLocalUser(); authState.clearSupabaseSession();
    await clearPersistedAuth(); await signOut();
    try { SyncService.teardownAutoSync(); syncHealthState.stopPoll(); } catch {}
    this.reader.unsubscribeFromAllRemoteChanges();
    try { dictionaryState.unsubscribe(); } catch {}
    this.settings.clearDailyGoal(); this.statsDomain.clearTodayMinutes(); this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
    this.navigateToWelcome(); pushToast('success', this.t('welcome.signedOutToast'));
  };

  private startAuthenticatedSync(): void {
    SyncService.setupOutboxProcessor();
    try { SyncService.setupAutoSync(); void syncHealthState.refresh(); syncHealthState.startPoll(); } catch {}
    this.reader.subscribeToAllRemoteChanges();
    try { dictionaryState.subscribeToRemoteChanges(); } catch {}
    void SyncService.syncMetadata().catch((error: unknown) => { console.error('Startup sync failed; continuing offline:', error); });
  }

  private async loadDailyGoalForCurrentUser(): Promise<void> {
    const uid = authState.userId;
    if (!uid || uid.trim().length === 0) { this.settings.clearDailyGoal(); this.statsDomain.clearTodayMinutes(); this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes); return; }
    try { await this.settings.loadDailyGoalMinutes(uid); } catch {}
    this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
    try { await this.statsDomain.loadTodayMinutes(uid); } catch {}
  }

  async saveDailyGoalMinutes(minutes: number): Promise<void> {
    const uid = authState.userId; if (!uid || uid.trim().length === 0) return;
    await this.settings.saveDailyGoalMinutes(minutes, uid);
    this.statsDomain.syncDailyGoal(this.settings.dailyGoalMinutes);
    try { await this.statsDomain.loadTodayMinutes(uid); } catch {}
  }

  async refreshTodayMinutes(): Promise<void> {
    const uid = authState.userId; if (!uid) { this.statsDomain.clearTodayMinutes(); return; }
    await this.statsDomain.loadTodayMinutes(uid);
  }

  private hydrateAuthState(session: Session, driveRefreshToken: string | null): void {
    authState.setSupabaseSession({
      accessToken: session.access_token, refreshToken: session.refresh_token,
      expiresAt: session.expires_at ? session.expires_at * 1000 : null,
      userId: session.user.id, email: session.user.email ?? null,
      displayName: session.user.user_metadata?.full_name ?? session.user.user_metadata?.name ?? null,
      photoUrl: session.user.user_metadata?.avatar_url ?? session.user.user_metadata?.picture ?? null,
      providerToken: session.provider_token ?? null, driveRefreshToken,
    });
  }

  // ─── Deprecated shims (one PR, with warn) ───
  /** @deprecated use navigationState.route */ get route(): AppRoute { warn('route'); return this.navigation.route; }
  /** @deprecated */ set route(v: AppRoute) { warn('route'); this.navigation.route = v; }
  /** @deprecated use navigationState.previewBookId */ get previewBookId(): string | null { warn('previewBookId'); return this.navigation.previewBookId; }
  /** @deprecated */ set previewBookId(v: string | null) { warn('previewBookId'); this.navigation.previewBookId = v; }
  /** @deprecated */ get shelfDetailsBookId(): string | null { warn('shelfDetailsBookId'); return this.navigation.shelfDetailsBookId; }
  /** @deprecated */ set shelfDetailsBookId(v: string | null) { warn('shelfDetailsBookId'); this.navigation.shelfDetailsBookId = v; }
  /** @deprecated */ get libraryUnavailableReason(): string | null { warn('libraryUnavailableReason'); return this.navigation.libraryUnavailableReason; }
  /** @deprecated */ set libraryUnavailableReason(v: string | null) { warn('libraryUnavailableReason'); this.navigation.libraryUnavailableReason = v; }
  /** @deprecated */ get statsUnavailableReason(): string | null { warn('statsUnavailableReason'); return this.navigation.statsUnavailableReason; }
  /** @deprecated */ set statsUnavailableReason(v: string | null) { warn('statsUnavailableReason'); this.navigation.statsUnavailableReason = v; }
  /** @deprecated */ get searchUnavailableReason(): string | null { warn('searchUnavailableReason'); return this.navigation.searchUnavailableReason; }
  /** @deprecated */ set searchUnavailableReason(v: string | null) { warn('searchUnavailableReason'); this.navigation.searchUnavailableReason = v; }
  /** @deprecated use libraryState.books */ get books(): ReaderBook[] { warn('books'); return this.library.books; }
  /** @deprecated */ set books(v: ReaderBook[]) { warn('books'); this.library.books = v; }
  /** @deprecated */ get shelfQueryState(): ShelfQueryState { warn('shelfQueryState'); return this.library.shelfQueryState; }
  /** @deprecated */ set shelfQueryState(v: ShelfQueryState) { warn('shelfQueryState'); this.library.shelfQueryState = v; }
  /** @deprecated */ get collections(): CollectionDto[] { warn('collections'); return this.library.collections; }
  /** @deprecated */ set collections(v: CollectionDto[]) { warn('collections'); this.library.collections = v; }
  /** @deprecated */ get isLoadingLibrary(): boolean { warn('isLoadingLibrary'); return this.library.isLoadingLibrary; }
  /** @deprecated */ set isLoadingLibrary(v: boolean) { warn('isLoadingLibrary'); this.library.isLoadingLibrary = v; }
  /** @deprecated */ get readerError(): string | null { warn('readerError'); return this.library.readerError; }
  /** @deprecated */ set readerError(v: string | null) { warn('readerError'); this.library.readerError = v; }
  /** @deprecated */ get editingBook(): ReaderBook | null { warn('editingBook'); return this.library.editingBook; }
  /** @deprecated */ set editingBook(v: ReaderBook | null) { warn('editingBook'); this.library.editingBook = v; }
  /** @deprecated */ get pendingRemoveBook(): ReaderBook | null { warn('pendingRemoveBook'); return this.library.pendingRemoveBook; }
  /** @deprecated */ set pendingRemoveBook(v: ReaderBook | null) { warn('pendingRemoveBook'); this.library.pendingRemoveBook = v; }
  /** @deprecated */ get isCollectionManagerOpen(): boolean { warn('isCollectionManagerOpen'); return this.library.isCollectionManagerOpen; }
  /** @deprecated */ set isCollectionManagerOpen(v: boolean) { warn('isCollectionManagerOpen'); this.library.isCollectionManagerOpen = v; }
  /** @deprecated */ get continueReadingBooks(): ReaderBook[] { warn('continueReadingBooks'); return this.library.continueReadingBooks; }
  /** @deprecated */ get myShelfBooks(): ReaderBook[] { warn('myShelfBooks'); return this.library.myShelfBooks; }
  /** @deprecated */ get shelfBooks(): ReaderBook[] { warn('shelfBooks'); return this.library.shelfBooks; }
  /** @deprecated */ get shelfWarnings(): string[] { warn('shelfWarnings'); return this.library.shelfWarnings; }
  /** @deprecated */ get shelfSortToken(): string | null { warn('shelfSortToken'); return this.library.shelfSortToken; }
  /** @deprecated */ get selectedShelfBook(): ReaderBook | null { warn('selectedShelfBook'); if (!this.navigation.shelfDetailsBookId) return null; return this.books.find((book) => book.id === this.navigation.shelfDetailsBookId) ?? null; }
  /** @deprecated */ get activeReadingBookId(): string | null { warn('activeReadingBookId'); return this.reader.activeReadingBookId; }
  /** @deprecated */ set activeReadingBookId(v: string | null) { warn('activeReadingBookId'); this.reader.activeReadingBookId = v; }
  /** @deprecated */ get cfiLocation(): string { warn('cfiLocation'); return this.reader.cfiLocation; }
  /** @deprecated */ set cfiLocation(v: string) { warn('cfiLocation'); this.reader.cfiLocation = v; }
  /** @deprecated */ get percentage(): number { warn('percentage'); return this.reader.percentage; }
  /** @deprecated */ set percentage(v: number) { warn('percentage'); this.reader.percentage = v; }
  /** @deprecated */ get preloadedBytes(): { filePath: string; data: Uint8Array } | null { warn('preloadedBytes'); return this.reader.preloadedBytes; }
  /** @deprecated */ set preloadedBytes(v: { filePath: string; data: Uint8Array } | null) { warn('preloadedBytes'); this.reader.preloadedBytes = v; }
  /** @deprecated */ get searchResponse(): SearchBookTextResponse | null { warn('searchResponse'); return this.search.searchResponse; }
  /** @deprecated */ set searchResponse(v: SearchBookTextResponse | null) { warn('searchResponse'); this.search.searchResponse = v; }
  /** @deprecated */ get searchTargetLocator(): string | null { warn('searchTargetLocator'); return this.search.searchTargetLocator; }
  /** @deprecated */ set searchTargetLocator(v: string | null) { warn('searchTargetLocator'); this.search.searchTargetLocator = v; }
  /** @deprecated */ get isSearching(): boolean { warn('isSearching'); return this.search.isSearching; }
  /** @deprecated */ set isSearching(v: boolean) { warn('isSearching'); this.search.isSearching = v; }
  /** @deprecated */ get isBulkImportOpen(): boolean { warn('isBulkImportOpen'); return this.bulkImport.isBulkImportOpen; }
  /** @deprecated */ set isBulkImportOpen(v: boolean) { warn('isBulkImportOpen'); this.bulkImport.isBulkImportOpen = v; }
  /** @deprecated */ get isBulkScanning(): boolean { warn('isBulkScanning'); return this.bulkImport.isBulkScanning; }
  /** @deprecated */ set isBulkScanning(v: boolean) { warn('isBulkScanning'); this.bulkImport.isBulkScanning = v; }
  /** @deprecated */ get isBulkImporting(): boolean { warn('isBulkImporting'); return this.bulkImport.isBulkImporting; }
  /** @deprecated */ set isBulkImporting(v: boolean) { warn('isBulkImporting'); this.bulkImport.isBulkImporting = v; }
  /** @deprecated */ get bulkImportFolderPath(): string | null { warn('bulkImportFolderPath'); return this.bulkImport.bulkImportFolderPath; }
  /** @deprecated */ set bulkImportFolderPath(v: string | null) { warn('bulkImportFolderPath'); this.bulkImport.bulkImportFolderPath = v; }
  /** @deprecated */ get bulkImportFolderName(): string | null { warn('bulkImportFolderName'); return this.bulkImport.bulkImportFolderName; }
  /** @deprecated */ set bulkImportFolderName(v: string | null) { warn('bulkImportFolderName'); this.bulkImport.bulkImportFolderName = v; }
  /** @deprecated */ get bulkScanResult(): ScanFolderResult | null { warn('bulkScanResult'); return this.bulkImport.bulkScanResult; }
  /** @deprecated */ set bulkScanResult(v: ScanFolderResult | null) { warn('bulkScanResult'); this.bulkImport.bulkScanResult = v; }
  /** @deprecated */ get bulkScanError(): string | null { warn('bulkScanError'); return this.bulkImport.bulkScanError; }
  /** @deprecated */ set bulkScanError(v: string | null) { warn('bulkScanError'); this.bulkImport.bulkScanError = v; }
  /** @deprecated */ get bulkImportProgress(): BulkImportProgress | null { warn('bulkImportProgress'); return this.bulkImport.bulkImportProgress; }
  /** @deprecated */ set bulkImportProgress(v: BulkImportProgress | null) { warn('bulkImportProgress'); this.bulkImport.bulkImportProgress = v; }
  /** @deprecated */ get bulkImportSummary(): BulkImportSummary | null { warn('bulkImportSummary'); return this.bulkImport.bulkImportSummary; }
  /** @deprecated */ set bulkImportSummary(v: BulkImportSummary | null) { warn('bulkImportSummary'); this.bulkImport.bulkImportSummary = v; }
  /** @deprecated */ get isImporting(): boolean { warn('isImporting'); return this.bulkImport.isImporting; }
  /** @deprecated */ set isImporting(v: boolean) { warn('isImporting'); this.bulkImport.isImporting = v; }
  /** @deprecated */ get importProgress(): ImportProgress | null { warn('importProgress'); return this.bulkImport.importProgress; }
  /** @deprecated */ set importProgress(v: ImportProgress | null) { warn('importProgress'); this.bulkImport.importProgress = v; }
  /** @deprecated */ get stats(): ReadingStatsSummaryDto | null { warn('stats'); return this.statsDomain.stats; }
  /** @deprecated */ set stats(v: ReadingStatsSummaryDto | null) { warn('stats'); this.statsDomain.stats = v; }
  /** @deprecated */ get isLoadingStats(): boolean { warn('isLoadingStats'); return this.statsDomain.isLoadingStats; }
  /** @deprecated */ set isLoadingStats(v: boolean) { warn('isLoadingStats'); this.statsDomain.isLoadingStats = v; }
  /** @deprecated */ get locale(): UiLocale { warn('locale'); return this.settings.locale; }
  /** @deprecated */ set locale(v: UiLocale) { warn('locale'); this.settings.locale = v; }
  /** @deprecated */ get readerSettings(): ReaderSettings { warn('readerSettings'); return this.settings.readerSettings; }
  /** @deprecated */ set readerSettings(v: ReaderSettings) { warn('readerSettings'); this.settings.readerSettings = v; }
  readonly SHELF_TAB_OPTIONS = this.library.SHELF_TAB_OPTIONS;
  readonly SHELF_SORT_OPTIONS = this.library.SHELF_SORT_OPTIONS;
  readonly DOMAIN = navigationState.DOMAIN;
  isValidSessionProgressEvent(event: { startedAt: string; endedAt?: string; durationSeconds: number; startPercentage?: number; endPercentage?: number }): boolean { warn('isValidSessionProgressEvent'); return this.reader.isValidSessionProgressEvent(event); }
  getBookById(bookId: string | null): ReaderBook | null { warn('getBookById'); return this.library.getBookById(bookId); }
  hasResolvedCoverPath(book: Pick<LibraryBookDto, 'coverPath'>): boolean { warn('hasResolvedCoverPath'); return this.library.hasResolvedCoverPath(book); }
  shouldGeneratePdfCover(book: ReaderBook): boolean { warn('shouldGeneratePdfCover'); return this.library.shouldGeneratePdfCover(book); }
  shouldGenerateEpubCover(book: ReaderBook): boolean { warn('shouldGenerateEpubCover'); return this.library.shouldGenerateEpubCover(book); }
  navigateToHome = (): void => { warn('navigateToHome'); this.navigation.navigateToHome(); };
  navigateToLibrary = (): void => { warn('navigateToLibrary'); this.navigation.navigateToLibrary(); };
  navigateToStats = (): void => { warn('navigateToStats'); this.navigation.navigateToStats(); };
  navigateToHighlights = (): void => { warn('navigateToHighlights'); this.navigation.navigateToHighlights(); };
  navigateToSettings = (): void => { warn('navigateToSettings'); this.navigation.navigateToSettings(); };
  navigateToDictionary = (): void => { warn('navigateToDictionary'); this.navigation.navigateToDictionary(); };
  navigateToStorage = (): void => { warn('navigateToStorage'); this.navigation.navigateToStorage(); };
  navigateToSync = (): void => { warn('navigateToSync'); this.navigation.navigateToSync(); };
  navigateToWelcome = (): void => { warn('navigateToWelcome'); this.navigation.route = 'welcome'; };
  backToHome = (): void => { warn('backToHome'); this.navigation.backToHome(); };
  openDetails = (book: ReaderBook): void => { warn('openDetails'); this.navigation.openDetails(book.id); };
  openShelfDetails = (book: ReaderBook): void => { warn('openShelfDetails'); this.navigation.openShelfDetails(book.id); };
  closeShelfDetails = (): void => { warn('closeShelfDetails'); this.navigation.closeShelfDetails(); };
  setDomainUnavailable = (domain: 'library' | 'stats' | 'search', reason: string | null): void => { warn('setDomainUnavailable'); this.navigation.setDomainUnavailable(domain, reason); };
  setShelfTab = (tab: (typeof this.library.SHELF_TAB_OPTIONS)[number]['key']): void => { warn('setShelfTab'); this.library.setShelfTab(tab); };
  setShelfSort = (sortKey: (typeof this.library.SHELF_SORT_OPTIONS)[number]['key']): void => { warn('setShelfSort'); this.library.setShelfSort(sortKey); };
  setShelfViewMode = (viewMode: 'grid' | 'list'): void => { warn('setShelfViewMode'); this.library.setShelfViewMode(viewMode); };
  handleShelfQueryInput = (event: Event): void => { warn('handleShelfQueryInput'); this.library.handleShelfQueryInput(event); };
  clearShelfQuery = (): void => { warn('clearShelfQuery'); this.library.clearShelfQuery(); };
  handleEditBook = (book: ReaderBook): void => { warn('handleEditBook'); this.library.handleEditBook(book); };
  handleSaveEditedBook = async (updatedBook: LibraryBookDto): Promise<void> => { warn('handleSaveEditedBook'); await this.library.handleSaveEditedBook(updatedBook); };
  handleHideBook = async (book: ReaderBook): Promise<void> => {
    warn('handleHideBook');
    const snapshot = { route: this.navigation.route, previewBookId: this.navigation.previewBookId, activeReadingBookId: this.reader.activeReadingBookId, shelfDetailsBookId: this.navigation.shelfDetailsBookId };
    await this.library.handleHideBook(book);
    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route; this.navigation.previewBookId = reconciled.previewBookId; this.reader.activeReadingBookId = reconciled.activeReadingBookId; this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }
    const error = this.library.consumeLastRecoverableError();
    if (error) this.navigation.setDomainUnavailable('library', error.message);
  };
  requestRemoveBook = (book: ReaderBook): void => { warn('requestRemoveBook'); this.library.pendingRemoveBook = book; };
  handleRemoveBookFromDrive = async (book: ReaderBook): Promise<void> => {
    warn('handleRemoveBookFromDrive');
    const snapshot = { route: this.navigation.route, previewBookId: this.navigation.previewBookId, activeReadingBookId: this.reader.activeReadingBookId, shelfDetailsBookId: this.navigation.shelfDetailsBookId };
    try { await this.library.handleRemoveBookFromDrive(book); } catch {}
    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route; this.navigation.previewBookId = reconciled.previewBookId; this.reader.activeReadingBookId = reconciled.activeReadingBookId; this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }
    const error = this.library.consumeLastRecoverableError();
    if (error) this.navigation.setDomainUnavailable('library', error.message);
    this.library.pendingRemoveBook = null;
  };
  handleToggleFavorite = async (book: ReaderBook): Promise<void> => { warn('handleToggleFavorite'); await this.library.handleToggleFavorite(book); };
  handleDeleteCover = async (book: ReaderBook): Promise<void> => { warn('handleDeleteCover'); await this.library.handleDeleteCover(book); };
  handleStatusChange = async (book: ReaderBook, status: string): Promise<void> => {
    warn('handleStatusChange');
    const snapshot = { route: this.navigation.route, previewBookId: this.navigation.previewBookId, activeReadingBookId: this.reader.activeReadingBookId, shelfDetailsBookId: this.navigation.shelfDetailsBookId };
    await this.library.handleStatusChange(book, status as 'to_read' | 'reading' | 'completed');
    if (this.library.consumeBooksJustChanged()) {
      const reconciled = reconcileHomeState(this.library.books, snapshot);
      this.navigation.route = reconciled.route; this.navigation.previewBookId = reconciled.previewBookId; this.reader.activeReadingBookId = reconciled.activeReadingBookId; this.navigation.shelfDetailsBookId = reconciled.shelfDetailsBookId;
    }
    void this.statsDomain.loadStats(undefined);
  };
  handleReaderSettingsChange = (nextSettings: ReaderSettings): void => { warn('handleReaderSettingsChange'); this.settings.handleReaderSettingsChange(nextSettings); };
  handleLocaleChange = (nextLocale: UiLocale): void => { warn('handleLocaleChange'); this.settings.handleLocaleChange(nextLocale); };
  handleReaderLocationContext = (ctx?: unknown): void => { warn('handleReaderLocationContext'); this.reader.handleReaderLocationContext(ctx); };
  handleSearch = async (query: string, page: number): Promise<void> => {
    warn('handleSearch');
    if (!this.reader.activeReadingBookId) return;
    await this.search.handleSearch(this.reader.activeReadingBookId, query, page);
    if (this.search.searchUnavailableReason) this.navigation.setDomainUnavailable('search', this.search.searchUnavailableReason);
  };
  handleSearchJump = (target: SearchNavigationTarget): void => { warn('handleSearchJump'); this.search.handleSearchJump(target); };
  openBulkImportModal = (): void => { warn('openBulkImportModal'); this.bulkImport.openBulkImportModal(); };
  closeBulkImportModal = (): void => { warn('closeBulkImportModal'); this.bulkImport.closeBulkImportModal(); };
  handlePickBulkImportFolder = async (): Promise<void> => { warn('handlePickBulkImportFolder'); await this.bulkImport.handlePickBulkImportFolder(this.t('library.bulkImport.selectFolderTitle')); };
  handleScanBulkImportFolder = async (): Promise<void> => { warn('handleScanBulkImportFolder'); await this.bulkImport.handleScanBulkImportFolder(); };
  handleStartBulkImport = async (): Promise<void> => { warn('handleStartBulkImport'); await this.bulkImport.handleStartBulkImport(); };
  handleCancelBulkImport = (): void => { warn('handleCancelBulkImport'); this.bulkImport.handleCancelBulkImport(); };
  handleImportFile = async (): Promise<void> => {
    warn('handleImportFile');
    try { await this.bulkImport.handleImportFile(); } catch (error) { this.library.readerError = error instanceof Error ? error.message : this.t('import.failed'); }
  };
  loadStats = async (bookId?: string): Promise<void> => {
    warn('loadStats'); await this.statsDomain.loadStats(bookId);
    if (this.statsDomain.statsUnavailableReason) this.navigation.setDomainUnavailable('stats', this.statsDomain.statsUnavailableReason);
  };
  loadStatsActivity = async (period: string, granularity: string, bookId?: string): Promise<void> => { warn('loadStatsActivity'); return this.statsDomain.loadActivity(period, granularity, bookId); };
  loadStatsRange = async (from: string, to: string, bookId?: string, target?: 'current' | 'previous'): Promise<void> => { warn('loadStatsRange'); return this.statsDomain.loadRangeStats(from, to, bookId, target); };
  loadStatsStreak = async (bookId?: string): Promise<void> => { warn('loadStatsStreak'); return this.statsDomain.loadStreak(bookId, authState.userId ?? ''); };
}

export const appState = new AppState();
