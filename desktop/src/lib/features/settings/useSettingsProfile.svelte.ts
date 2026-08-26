import { authState as defaultAuthState } from '$lib/stores/authState.svelte';
import { createDevicesState as defaultCreateDevicesState } from '$lib/stores/devicesState.svelte';
import { appState as defaultAppState } from '$lib/shared/stores/AppState.svelte';
import { settingsState as defaultSettingsState } from '$lib/shared/stores/SettingsDomainState.svelte';
import { normalizeProfileSession, profileSessionFromAuthState, type ProfileSessionViewModel } from './profileSession';
import { DEFAULT_DAILY_GOAL, type DailyGoalOption } from '$lib/shared/types/settings';
import type { MessageKey } from '$lib/shared/i18n';

export type ProfileDeps = {
  authState?: typeof defaultAuthState;
  createDevicesState?: typeof defaultCreateDevicesState;
  appState?: typeof defaultAppState;
  settingsState?: typeof defaultSettingsState;
  t?: (key: MessageKey, params?: Record<string, string | number>) => string;
};

export function createSettingsProfile(deps: ProfileDeps = {}): {
  isProfileLoading: boolean;
  profileError: string | null;
  profile: ProfileSessionViewModel;
  devicesState: ReturnType<typeof defaultCreateDevicesState>;
  selectedDailyGoal: number;
  isSavingDailyGoal: boolean;
  isSigningOut: boolean;
  dailyGoalCards: { value: DailyGoalOption; labelKey: MessageKey; shortLabel: string; icon: 'hand' | 'book' | 'chart' | 'flame'; minutesLabel: string }[];
  isDirty: boolean;
  isSaving: boolean;
  handleSaveDailyGoal: () => Promise<void>;
  handleSignOut: () => Promise<void>;
  loadProfileData: () => Promise<void>;
  handleSelectDailyGoal: (value: number) => void;
  loadDevices: (userId: string) => Promise<void>;
  syncFromStore: () => void;
  destroy: () => void;
  stopHeartbeat: () => void;
} {
  const createDevices = deps.createDevicesState ?? defaultCreateDevicesState;
  const app = deps.appState ?? defaultAppState;
  const sState = deps.settingsState ?? defaultSettingsState;
  const t = deps.t ?? ((k: MessageKey) => k as string);
  void deps.authState;

  let isProfileLoading = $state(false);
  let profileError = $state<string | null>(null);
  let profile = $state<ProfileSessionViewModel>(profileSessionFromAuthState());
  let devicesState = $state(createDevices());
  let selectedDailyGoal = $state<number>((sState.dailyGoalMinutes as number) ?? (DEFAULT_DAILY_GOAL as number));
  let isSavingDailyGoal = $state(false);
  let isSigningOut = $state(false);

  const dailyGoalCards = $derived<
    { value: DailyGoalOption; labelKey: MessageKey; shortLabel: string; icon: 'hand' | 'book' | 'chart' | 'flame'; minutesLabel: string }[]
  >([
    { value: 10, labelKey: 'settings.daily_goal_relaxed', shortLabel: 'Relajado', icon: 'hand', minutesLabel: '10 min' },
    { value: 20, labelKey: 'settings.daily_goal_regular', shortLabel: 'Regular', icon: 'book', minutesLabel: '20 min' },
    { value: 30, labelKey: 'settings.daily_goal_serious', shortLabel: 'Serio', icon: 'chart', minutesLabel: '30 min' },
    { value: 45, labelKey: 'settings.daily_goal_intense', shortLabel: 'Intenso', icon: 'flame', minutesLabel: '45 min' },
  ]);

  const isDirty = $derived(selectedDailyGoal !== (sState.dailyGoalMinutes as number));
  const isSaving = $derived(isSavingDailyGoal || isSigningOut || isProfileLoading);

  async function handleSaveDailyGoal(): Promise<void> {
    if (isSavingDailyGoal) return;
    isSavingDailyGoal = true;
    try {
      await app.saveDailyGoalMinutes(selectedDailyGoal);
      // optimistic toast handled by caller; compose here if needed
    } catch (error) {
      const msg = error instanceof Error ? error.message : String(error);
      const { pushToast } = await import('$lib/stores/toastQueue.svelte');
      pushToast('error', msg);
      throw error;
    } finally {
      isSavingDailyGoal = false;
    }
  }

  async function loadProfileData(): Promise<void> {
    isProfileLoading = true;
    profileError = null;
    try {
      profile = profileSessionFromAuthState();
    } catch (error) {
      profile = normalizeProfileSession(null);
      profileError = error instanceof Error ? error.message : t('errors.commandFailure');
    } finally {
      isProfileLoading = false;
    }
  }

  function handleSelectDailyGoal(value: number): void {
    selectedDailyGoal = value;
  }

  async function handleSignOut(): Promise<void> {
    if (isSigningOut) return;
    isSigningOut = true;
    try {
      await app.signOutAndReturnToWelcome();
    } catch (error) {
      console.error('Sign out failed:', error);
    } finally {
      isSigningOut = false;
    }
  }

  function loadDevices(userId: string): Promise<void> {
    return devicesState.loadDevices(userId);
  }

  function syncFromStore(): void {
    const persisted = sState.dailyGoalMinutes;
    if (typeof persisted === 'number' && persisted > 0) {
      selectedDailyGoal = persisted as DailyGoalOption;
    }
  }

  function destroy(): void {
    devicesState.destroy();
  }

  function stopHeartbeat(): void {
    devicesState.stopHeartbeat();
  }

  return {
    get isProfileLoading() { return isProfileLoading; },
    set isProfileLoading(v: boolean) { isProfileLoading = v; },
    get profileError() { return profileError; },
    set profileError(v: string | null) { profileError = v; },
    get profile() { return profile; },
    set profile(v: ProfileSessionViewModel) { profile = v; },
    get devicesState() { return devicesState; },
    set devicesState(v: ReturnType<typeof defaultCreateDevicesState>) { devicesState = v; },
    get selectedDailyGoal() { return selectedDailyGoal; },
    set selectedDailyGoal(v: number) { selectedDailyGoal = v; },
    get isSavingDailyGoal() { return isSavingDailyGoal; },
    set isSavingDailyGoal(v: boolean) { isSavingDailyGoal = v; },
    get isSigningOut() { return isSigningOut; },
    set isSigningOut(v: boolean) { isSigningOut = v; },
    get dailyGoalCards() { return dailyGoalCards; },
    get isDirty() { return isDirty; },
    get isSaving() { return isSaving; },
    handleSaveDailyGoal,
    handleSignOut,
    loadProfileData,
    handleSelectDailyGoal,
    loadDevices,
    syncFromStore,
    destroy,
    stopHeartbeat,
  };
}
