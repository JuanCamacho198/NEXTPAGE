import { i18n, type MessageKey } from '$lib/shared/i18n';
import { initTheme } from '$lib/shared/stores/theme';
import { reconcileHomeState } from '$lib/shared/stores/HomeState';
import { navigationState } from '$lib/shared/stores/NavigationDomainState.svelte';
import { libraryState } from '$lib/shared/stores/LibraryDomainState.svelte';
import { readerState } from '$lib/shared/stores/ReaderDomainState.svelte';
import { searchState } from '$lib/shared/stores/SearchDomainState.svelte';
import { bulkImportState } from '$lib/shared/stores/BulkImportDomainState.svelte';
import { statsState } from '$lib/shared/stores/StatsDomainState.svelte';
import { settingsState } from '$lib/shared/stores/SettingsDomainState.svelte';
import { authState } from '$lib/shared/stores/AuthState.svelte';
import { pushToast } from '$lib/shared/stores/ToastQueue.svelte';
import { clearPersistedAuth, loadDriveRefreshToken, loadPersistedAuth, type LocalUserProfile } from '$lib/shared/stores/authPersistence';
import { getSessionClient, setLiveSession, clearLiveSession, getLiveSession } from '$lib/services/supabase';
import type { Session } from '@supabase/supabase-js';
import { signInAnonymously, restoreSession, signOut } from '$lib/shared/services';
import { SyncService } from '$lib/shared/services/SyncService';
import { dictionaryState } from '$lib/shared/stores/DictionaryState.svelte';
import { syncHealthState } from '$lib/shared/stores/SyncHealthState.svelte';
import type { CommandErrorDto, ReaderBook } from '$lib/shared/types';
import { createShelfQueryState, type AppRoute } from '$lib/shared/stores/HomeState';

type MaybeCommandError = Error & { commandError?: CommandErrorDto };

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

  t = (key: MessageKey, params?: Record<string, string | number>): string => i18n.t(this.settings.locale, key, params);

  mapCommandError(error: unknown): CommandErrorDto {
    const typed = error as MaybeCommandError;
    if (typed.commandError) return typed.commandError;
    const fallback = error instanceof Error ? error.message : this.t('errors.commandFailure');
    return { code: 'INTERNAL_ERROR', message: fallback, recoverable: false };
  }

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

  handleReaderLocationContext = (ctx?: unknown): void => {
    this.reader.handleReaderLocationContext(ctx);
  };

  async handleSearch(query: string, page: number): Promise<void> {
    if (!this.reader.activeReadingBookId) return;
    await this.search.handleSearch(this.reader.activeReadingBookId, query, page);
    if (this.search.searchUnavailableReason) this.navigation.setDomainUnavailable('search', this.search.searchUnavailableReason);
  }

  handleSearchJump = (target: import('$lib/shared/types').SearchNavigationTarget): void => {
    this.search.handleSearchJump(target);
  };

  navigateToWelcome = (): void => {
    this.navigation.route = 'welcome';
  };

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
}

export const appState = new AppState();
