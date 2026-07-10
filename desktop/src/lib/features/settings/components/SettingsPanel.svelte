<script lang="ts">
  import SettingsResetModal from './SettingsResetModal.svelte';
  import { GoogleLoginButton } from '$lib/features/library';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import { Button } from '$lib/shared/ui';
  import {
    getSettings,
    upsertSettings,
    getLocaleSetting,
    getReaderSettings,
    upsertReaderSettings,
  } from '$lib/shared/api/tauriClient';

  import { i18n, type MessageKey } from '$lib/shared/i18n';
  import {
    normalizeProfileSession,
    profileSessionFromAuthState,
    type ProfileSessionViewModel,
  } from '../profileSession';
  import { authState } from '$lib/stores/authState.svelte';
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import type {
    AppSettingDto,
    CommandErrorDto,
    UiLocale,
    ReaderSettings,
    ReaderThemeMode,
  } from '$lib/shared/types';
  import ProfileCard from './ProfileCard.svelte';
  import { createDevicesState } from '$lib/stores/devicesState.svelte';
  import ConnectedDevices from './ConnectedDevices.svelte';

  let {
    isOpen = $bindable(false),
    mode = 'overlay',
    onRequestClose,
    locale,
    onLocaleChange,
    onReaderSettingsChange,
    t,
    books = [],
  } = $props<{
    isOpen: boolean;
    mode?: 'overlay' | 'page';
    onRequestClose?: () => void;
    locale: UiLocale;
    onLocaleChange?: (locale: UiLocale) => void;
    onReaderSettingsChange?: (settings: ReaderSettings) => void;
    books?: { id: string; title: string }[];
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  }>();

  let activeTab = $state<'cuenta' | 'apariencia' | 'reader' | 'datos' | 'atajos' | 'acerca'>('cuenta');

  let preferredTheme = $state('light');
  let preferredFontScale = $state(100);
  let readerThemeMode = $state<ReaderThemeMode>('paper');
  let readerBrightness = $state(100);
  let readerContrast = $state(100);
  let readerEpubFontSize = $state(100);
  let readerEpubFontFamily = $state('sans');
  let settingsError = $state<string | null>(null);
  let settingsUnavailable = $state<string | null>(null);
  let isSavingSettings = $state(false);
  let showResetModal = $state(false);
  let pendingResetTab = $state<'cuenta' | 'apariencia' | 'reader' | null>(null);
  let isProfileLoading = $state(false);
  let profileError = $state<string | null>(null);
  let profileAvatarBroken = $state(false);
  let profile = $state<ProfileSessionViewModel>(profileSessionFromAuthState());
  let devicesState = $state(createDevicesState());

  // Data tab state
  let isClearingCache = $state(false);
  let cacheCleared = $state(false);
  let selectedExportBook = $state('all');
  let selectedExportFormat = $state<'json' | 'markdown'>('json');
  let isExportingHighlights = $state(false);

  type ShortcutDescriptor = {
    id: string;
    combo: string;
    descriptionKey: MessageKey;
  };

  const keyboardShortcuts: ShortcutDescriptor[] = [
    {
      id: 'reader-prev',
      combo: 'ArrowLeft',
      descriptionKey: 'settings.shortcuts.readerPrev',
    },
    {
      id: 'reader-next',
      combo: 'ArrowRight',
      descriptionKey: 'settings.shortcuts.readerNext',
    },
    {
      id: 'reader-scroll-up',
      combo: 'ArrowUp',
      descriptionKey: 'settings.shortcuts.readerScrollUp',
    },
    {
      id: 'reader-scroll-down',
      combo: 'ArrowDown',
      descriptionKey: 'settings.shortcuts.readerScrollDown',
    },
    {
      id: 'dialog-close',
      combo: 'Escape',
      descriptionKey: 'settings.shortcuts.closeDialog',
    },
  ];

  const DEFAULT_VALUES = {
    preferredTheme: 'light',
    preferredFontScale: 100,
    readerThemeMode: 'paper' as ReaderThemeMode,
    readerBrightness: 100,
    readerContrast: 100,
    readerEpubFontSize: 100,
    readerEpubFontFamily: 'sans',
  };

  type MaybeCommandError = Error & { commandError?: CommandErrorDto };

  const SETTINGS_KEY = {
    THEME: 'ui.theme',
    FONT_SCALE: 'reader.fontScale',
  } as const;

  const clampInteger = (value: number, min: number, max: number): number => {
    return Math.min(max, Math.max(min, Math.round(value)));
  };

  const normalizeFontFamily = (value: string): string => {
    const normalized = value.trim();
    return normalized.length > 0 ? normalized : 'sans';
  };

  const localeOptions = $derived([
    { value: 'es', label: t('settings.languageSpanish') },
    { value: 'en', label: t('settings.languageEnglish') },
  ]);

  const panelFontFamilyOptions = [
    { value: 'serif', label: 'Serif' },
    { value: 'sans-serif', label: 'Sans Serif' },
    { value: 'monospace', label: 'Monospace' },
  ];

  const buildReaderSettingsDraft = (): ReaderSettings => ({
    themeMode: readerThemeMode,
    brightness: clampInteger(readerBrightness, 50, 150),
    contrast: clampInteger(readerContrast, 50, 150),
    epub: {
      fontSize: clampInteger(readerEpubFontSize, 80, 200),
      fontFamily: normalizeFontFamily(readerEpubFontFamily),
    },
    selectionColor: '#33bbff',
    lineHeight: 1.8,
    letterSpacing: 0,
    paragraphSpacing: 1,
    textAlign: 'left',
    direction: 'ltr',
    hyphenation: false,
    verticalScrolling: false,
    margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
    showHeader: true,
    showFooter: true,
    showPageNumbers: true,
    progressIndicator: 'percentage',
  });

  const applyReaderSettingsToState = (settings: ReaderSettings): void => {
    readerThemeMode = settings.themeMode;
    readerBrightness = settings.brightness;
    readerContrast = settings.contrast;
    readerEpubFontSize = settings.epub.fontSize;
    readerEpubFontFamily = settings.epub.fontFamily;
  };

  const parseSettingValue = (settings: AppSettingDto[], key: string): unknown => {
    const item = settings.find((entry) => entry.key === key);
    if (!item) {
      return null;
    }

    try {
      return JSON.parse(item.valueJson) as unknown;
    } catch {
      return null;
    }
  };

  const mapCommandErrorMessage = (error: unknown): { message: string; recoverable: boolean } => {
    const err = error as MaybeCommandError;
    const fallback = error instanceof Error ? error.message : 'Settings command failed.';
    if (err.commandError) {
      return {
        message: err.commandError.message,
        recoverable: err.commandError.recoverable,
      };
    }

    return {
      message: fallback,
      recoverable: false,
    };
  };

  function closePanel(): void {
    if (mode === 'page') {
      onRequestClose?.();
      return;
    }

    isOpen = false;
  }

  let isSigningOut = $state(false);

  async function handleSignOut(): Promise<void> {
    if (isSigningOut) return;
    isSigningOut = true;
    try {
      await appState.signOutAndReturnToWelcome();
    } catch (error) {
      console.error('Sign out failed:', error);
    } finally {
      isSigningOut = false;
    }
  }

  async function loadAppSettings(): Promise<void> {
    settingsError = null;
    settingsUnavailable = null;

    try {
      const response = await getSettings();
      const nextTheme = parseSettingValue(response, SETTINGS_KEY.THEME);
      const nextFontScale = parseSettingValue(response, SETTINGS_KEY.FONT_SCALE);

      if (typeof nextTheme === 'string' && nextTheme.length > 0) {
        preferredTheme = nextTheme;
      }

      if (typeof nextFontScale === 'number' && Number.isFinite(nextFontScale)) {
        preferredFontScale = Math.max(80, Math.min(140, Math.round(nextFontScale)));
      }

      const persistedLocale = i18n.toSupportedLocale(await getLocaleSetting());
      if (persistedLocale) {
        locale = persistedLocale;
        onLocaleChange?.(persistedLocale);
      }

      const readerSettings = await getReaderSettings();
      applyReaderSettingsToState(readerSettings);
      onReaderSettingsChange?.(readerSettings);
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) {
        settingsUnavailable = details.message;
      } else {
        settingsError = details.message;
      }
    }
  }

  async function loadProfileData(): Promise<void> {
    isProfileLoading = true;
    profileError = null;

    try {
      profile = profileSessionFromAuthState();
      profileAvatarBroken = false;
    } catch (error) {
      profile = normalizeProfileSession(null);
      profileError = error instanceof Error ? error.message : t('errors.commandFailure');
      profileAvatarBroken = false;
    } finally {
      isProfileLoading = false;
    }
  }

  async function saveAppSettings(): Promise<void> {
    isSavingSettings = true;
    settingsError = null;
    settingsUnavailable = null;

    try {
      await upsertSettings([
        {
          key: SETTINGS_KEY.THEME,
          valueJson: JSON.stringify(preferredTheme),
          updatedAt: new Date().toISOString(),
        },
        {
          key: SETTINGS_KEY.FONT_SCALE,
          valueJson: JSON.stringify(preferredFontScale),
          updatedAt: new Date().toISOString(),
        },
      ]);

      const persistedReaderSettings = await upsertReaderSettings(buildReaderSettingsDraft());
      applyReaderSettingsToState(persistedReaderSettings);
      onReaderSettingsChange?.(persistedReaderSettings);
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) {
        settingsUnavailable = details.message;
      } else {
        settingsError = details.message;
      }
    } finally {
      isSavingSettings = false;
    }
  }

  async function handleLocaleSelect(value: string): Promise<void> {
    const safeLocale = i18n.toSupportedLocale(value) ?? i18n.FALLBACK_LOCALE;
    locale = safeLocale;
    onLocaleChange?.(safeLocale);
    settingsError = null;
    settingsUnavailable = null;

    try {
      await i18n.setLocale(safeLocale);
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) {
        settingsUnavailable = details.message;
      } else {
        settingsError = details.message;
      }
    }
  }

  async function handleTabChange(
    tab: 'cuenta' | 'apariencia' | 'reader' | 'datos' | 'atajos' | 'acerca',
  ): Promise<void> {
    activeTab = tab;
    if (tab === 'cuenta') {
      await loadProfileData();
      if (authState.isSignedIn && authState.userId) {
        devicesState.loadDevices(authState.userId);
      }
    } else {
      devicesState.stopHeartbeat();
    }

    if (tab === 'apariencia' || tab === 'reader') {
      await loadAppSettings();
    }
  }

  function handleTabKeydown(e: KeyboardEvent): void {
    const tabs = ['cuenta', 'apariencia', 'reader', 'datos', 'atajos', 'acerca'] as const;
    const idx = tabs.indexOf(activeTab);
    let next: number | null = null;

    if (e.key === 'ArrowRight') {
      e.preventDefault();
      next = (idx + 1) % tabs.length;
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      next = (idx - 1 + tabs.length) % tabs.length;
    } else if (e.key === 'Home') {
      e.preventDefault();
      next = 0;
    } else if (e.key === 'End') {
      e.preventDefault();
      next = tabs.length - 1;
    }

    if (next !== null) {
      handleTabChange(tabs[next]);
      document.getElementById(`tab-${tabs[next]}`)?.focus();
    }
  }

  function openResetModal(tab: 'cuenta' | 'apariencia' | 'reader'): void {
    pendingResetTab = tab;
    showResetModal = true;
  }

  function closeResetModal(): void {
    showResetModal = false;
    pendingResetTab = null;
  }

  async function confirmReset(): Promise<void> {
    if (pendingResetTab === 'cuenta') {
      preferredTheme = DEFAULT_VALUES.preferredTheme;
      preferredFontScale = DEFAULT_VALUES.preferredFontScale;
    } else if (pendingResetTab === 'reader') {
      readerThemeMode = DEFAULT_VALUES.readerThemeMode;
      readerBrightness = DEFAULT_VALUES.readerBrightness;
      readerContrast = DEFAULT_VALUES.readerContrast;
      readerEpubFontSize = DEFAULT_VALUES.readerEpubFontSize;
      readerEpubFontFamily = DEFAULT_VALUES.readerEpubFontFamily;
    } else if (pendingResetTab === 'apariencia') {
      preferredTheme = DEFAULT_VALUES.preferredTheme;
      preferredFontScale = DEFAULT_VALUES.preferredFontScale;
    }
    closeResetModal();
    await saveAppSettings();
  }

  // Data tab handlers
  async function handleClearCache(): Promise<void> {
    isClearingCache = true;
    try {
      await new Promise((resolve) => setTimeout(resolve, 500));
      cacheCleared = true;
    } finally {
      isClearingCache = false;
    }
  }

  function handleExportLibrary(): void {
    // Placeholder — wired via props in a future iteration
  }

  async function handleExportHighlights(): Promise<void> {
    isExportingHighlights = true;
    try {
      await new Promise((resolve) => setTimeout(resolve, 500));
    } finally {
      isExportingHighlights = false;
    }
  }

  const exportBookOptions = $derived([
    { value: 'all', label: t('settings.data.allBooks') },
    ...books.map((b: { id: string; title: string }) => ({ value: b.id, label: b.title })),
  ]);

  const exportFormatOptions = $derived([
    { value: 'json', label: 'JSON' },
    { value: 'markdown', label: t('settings.data.markdown') },
  ]);

  $effect(() => {
    if (isOpen) {
      void loadAppSettings();
      void loadProfileData();
    }
  });

  // Cleanup heartbeat on window close
  $effect(() => {
    const handleBeforeUnload = () => devicesState.stopHeartbeat()
    window.addEventListener('beforeunload', handleBeforeUnload)

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
    }
  });
</script>

{#if mode === 'page' || isOpen}
  {#if mode === 'overlay'}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <div class="fixed inset-0 w-screen h-screen bg-black/40 z-[999]" onclick={closePanel}></div>
  {/if}
  <aside
    class={mode === 'overlay'
      ? 'fixed top-0 right-0 w-[350px] h-screen bg-(--color-surface) border-l border-(--color-border) shadow-xl z-[1000] flex flex-col animate-[slide-in_0.3s_ease-out]'
      : 'w-full rounded-xl border border-(--color-border) bg-(--color-background) shadow-sm flex flex-col overflow-hidden'}
  >
    <div class="flex items-center p-3 border-b border-(--color-border)">
      <button
        class="inline-flex items-center justify-center size-8 rounded-lg bg-(--color-surface) border border-(--color-border) text-(--color-text-muted) cursor-pointer hover:text-(--color-primary) hover:border-(--color-primary) transition-all duration-200"
        onclick={closePanel}
        aria-label={t('settings.backToHome')}
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5m7-7l-7 7 7 7"/>
        </svg>
      </button>
    </div>

    <div
      role="tablist"
      aria-label={t('settings.title')}
      onkeydown={handleTabKeydown}
      tabindex="0"
      class="flex border-b border-(--color-border)"
    >
      <button
        type="button"
        role="tab"
        aria-selected={activeTab === 'cuenta'}
        aria-controls="tabpanel-cuenta"
        id="tab-cuenta"
        tabindex={activeTab === 'cuenta' ? 0 : -1}
        class="flex-1 px-2 py-3 border-none cursor-pointer text-(--text-2sm) text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary) transition-all duration-200 flex items-center justify-center gap-1.5"
        class:bg-(--color-accent-soft)={activeTab === 'cuenta'}
        class:text-(--color-accent-start)={activeTab === 'cuenta'}
        class:border-(--color-accent-start)={activeTab === 'cuenta'}
        class:font-semibold={activeTab === 'cuenta'}
        onclick={() => void handleTabChange('cuenta')}
      >
        <Icon name="user" size="sm" />
        <span>{t('settings.tab.account')}</span>
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={activeTab === 'apariencia'}
        aria-controls="tabpanel-apariencia"
        id="tab-apariencia"
        tabindex={activeTab === 'apariencia' ? 0 : -1}
        class="flex-1 px-2 py-3 border-none cursor-pointer text-(--text-2sm) text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary) transition-all duration-200 flex items-center justify-center gap-1.5"
        class:bg-(--color-accent-soft)={activeTab === 'apariencia'}
        class:text-(--color-accent-start)={activeTab === 'apariencia'}
        class:border-(--color-accent-start)={activeTab === 'apariencia'}
        class:font-semibold={activeTab === 'apariencia'}
        onclick={() => void handleTabChange('apariencia')}
      >
        <Icon name="sun" size="sm" />
        <span>{t('settings.tab.appearance')}</span>
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={activeTab === 'reader'}
        aria-controls="tabpanel-reader"
        id="tab-reader"
        tabindex={activeTab === 'reader' ? 0 : -1}
        class="flex-1 px-2 py-3 border-none cursor-pointer text-(--text-2sm) text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary) transition-all duration-200 flex items-center justify-center gap-1.5"
        class:bg-(--color-accent-soft)={activeTab === 'reader'}
        class:text-(--color-accent-start)={activeTab === 'reader'}
        class:border-(--color-accent-start)={activeTab === 'reader'}
        class:font-semibold={activeTab === 'reader'}
        onclick={() => void handleTabChange('reader')}
      >
        <Icon name="book" size="sm" />
        <span>{t('settings.tab.reader')}</span>
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={activeTab === 'datos'}
        aria-controls="tabpanel-datos"
        id="tab-datos"
        tabindex={activeTab === 'datos' ? 0 : -1}
        class="flex-1 px-2 py-3 border-none cursor-pointer text-(--text-2sm) text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary) transition-all duration-200 flex items-center justify-center gap-1.5"
        class:bg-(--color-accent-soft)={activeTab === 'datos'}
        class:text-(--color-accent-start)={activeTab === 'datos'}
        class:border-(--color-accent-start)={activeTab === 'datos'}
        class:font-semibold={activeTab === 'datos'}
        onclick={() => void handleTabChange('datos')}
      >
        <Icon name="database" size="sm" />
        <span>{t('settings.tab.data')}</span>
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={activeTab === 'atajos'}
        aria-controls="tabpanel-atajos"
        id="tab-atajos"
        tabindex={activeTab === 'atajos' ? 0 : -1}
        class="flex-1 px-2 py-3 border-none cursor-pointer text-(--text-2sm) text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary) transition-all duration-200 flex items-center justify-center gap-1.5"
        class:bg-(--color-accent-soft)={activeTab === 'atajos'}
        class:text-(--color-accent-start)={activeTab === 'atajos'}
        class:border-(--color-accent-start)={activeTab === 'atajos'}
        class:font-semibold={activeTab === 'atajos'}
        onclick={() => void handleTabChange('atajos')}
      >
        <Icon name="bookmark" size="sm" />
        <span>{t('settings.shortcuts.title')}</span>
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={activeTab === 'acerca'}
        aria-controls="tabpanel-acerca"
        id="tab-acerca"
        tabindex={activeTab === 'acerca' ? 0 : -1}
        class="flex-1 px-2 py-3 border-none cursor-pointer text-(--text-2sm) text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary) transition-all duration-200 flex items-center justify-center gap-1.5"
        class:bg-(--color-accent-soft)={activeTab === 'acerca'}
        class:text-(--color-accent-start)={activeTab === 'acerca'}
        class:border-(--color-accent-start)={activeTab === 'acerca'}
        class:font-semibold={activeTab === 'acerca'}
        onclick={() => void handleTabChange('acerca')}
      >
        <Icon name="info" size="sm" />
        <span>{t('settings.tab.about')}</span>
      </button>
    </div>

    <form novalidate onsubmit={(e) => e.preventDefault()} class="flex-1 flex flex-col min-h-0">
        {#if activeTab === 'cuenta'}
          <div
            role="tabpanel"
            id="tabpanel-cuenta"
            aria-labelledby="tab-cuenta"
            class="flex-1 overflow-y-auto p-4 flex flex-col gap-4"
          >
            <section class="rounded-xl border border-(--color-border) bg-(--color-surface) overflow-visible">
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                  {t('settings.authentication')}
                </h3>
                <p class="text-(--text-2xs) text-(--color-text-muted) mb-3">
                  {t('settings.authDescription')}
                </p>
                <GoogleLoginButton {t} />
                {#if authState.isSignedIn}
                  <Button variant="danger" disabled={isSigningOut} onclick={() => void handleSignOut()} class="w-full flex items-center justify-center gap-2 px-3 py-2 text-sm mt-4">
                    {isSigningOut ? t('welcome.signingOut') : t('welcome.signOut')}
                  </Button>
                {/if}
                {#if settingsUnavailable}
                  <p class="mb-2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">{settingsUnavailable}</p>
                {/if}
                {#if settingsError}
                  <p class="mb-2 rounded border border-red-300 bg-red-50 px-2 py-1 text-xs text-red-900">{settingsError}</p>
                {/if}
              </div>
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <ProfileCard {profile} {isProfileLoading} {profileError} {profileAvatarBroken} {t} />
              </div>
              {#if authState.isSignedIn && authState.userId}
                <div class="p-4 border-b border-(--color-border) last:border-b-0">
                  <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                    {t('settings.connectedDevices.title')}
                  </h3>
                  <ConnectedDevices
                    devices={devicesState.devices}
                    error={devicesState.error}
                    isLoading={devicesState.isLoading}
                    currentDeviceId={devicesState.currentDeviceId}
                    onremove={(id: string) => void devicesState.remove(id, authState.userId!)}
                    {t}
                  />
                </div>
              {/if}
              <div class="p-4">
                <span class="mb-1 block text-xs text-(--color-text-muted)">{t('settings.language')}</span>
                <Dropdown
                  options={localeOptions}
                  value={locale}
                  class="w-full"
                  onchange={({ value }) => void handleLocaleSelect(value)}
                />
              </div>
            </section>
          </div>
        {:else if activeTab === 'apariencia'}
          <div
            role="tabpanel"
            id="tabpanel-apariencia"
            aria-labelledby="tab-apariencia"
            class="flex-1 overflow-y-auto p-4 flex flex-col gap-4"
          >
            <section class="rounded-xl border border-(--color-border) bg-(--color-surface) overflow-hidden">
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                  {t('settings.appearance.appTheme')}
                </h3>

              <div class="mb-4">
                <div class="grid grid-cols-4 gap-2">
                  <button
                    type="button"
                    class="relative flex flex-col items-center gap-2 py-3 px-2 rounded-xl border-2 transition-all duration-200 cursor-pointer bg-(--color-surface) theme-icon-btn"
                    class:border-(--color-primary)={preferredTheme === 'light'}
                    class:border-(--color-border)={preferredTheme !== 'light'}
                    style="--icon-bg: linear-gradient(to bottom right, white, #f3f4f6);"
                    onclick={() => (preferredTheme = 'light')}
                    title={t('settings.theme.light')}
                  >
                    {#if preferredTheme === 'light'}
                      <div class="absolute top-1 right-1 text-(--color-primary)">
                        <Icon name="check" size="sm" />
                      </div>
                    {/if}
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="20"
                      height="20"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      aria-hidden="true"
                      class="theme-icon"
                      ><circle cx="12" cy="12" r="5" /><line x1="12" y1="1" x2="12" y2="3" /><line
                        x1="12"
                        y1="21"
                        x2="12"
                        y2="23"
                      /><line x1="4.22" y1="4.22" x2="5.64" y2="5.64" /><line
                        x1="18.36"
                        y1="18.36"
                        x2="19.78"
                        y2="19.78"
                      /><line x1="1" y1="12" x2="3" y2="12" /><line
                        x1="21"
                        y1="12"
                        x2="23"
                        y2="12"
                      /><line x1="4.22" y1="19.78" x2="5.64" y2="18.36" /><line
                        x1="18.36"
                        y1="5.64"
                        x2="19.78"
                        y2="4.22"
                      /></svg
                    >
                    <span class="text-(--text-2xs) font-medium text-(--color-text-muted)"
                      >{t('settings.theme.light')}</span
                    >
                  </button>
                  <button
                    type="button"
                    class="relative flex flex-col items-center gap-2 py-3 px-2 rounded-xl border-2 transition-all duration-200 cursor-pointer bg-(--color-surface) theme-icon-btn"
                    class:border-(--color-primary)={preferredTheme === 'dark'}
                    class:border-(--color-border)={preferredTheme !== 'dark'}
                    style="--icon-bg: linear-gradient(to bottom right, #3f3f46, #18181b);"
                    onclick={() => (preferredTheme = 'dark')}
                    title={t('settings.theme.dark')}
                  >
                    {#if preferredTheme === 'dark'}
                      <div class="absolute top-1 right-1 text-(--color-primary)">
                        <Icon name="check" size="sm" />
                      </div>
                    {/if}
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="20"
                      height="20"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      aria-hidden="true"
                      class="theme-icon"
                      ><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" /></svg
                    >
                    <span class="text-(--text-2xs) font-medium text-(--color-text-muted)"
                      >{t('settings.theme.dark')}</span
                    >
                  </button>
                  <button
                    type="button"
                    class="relative flex flex-col items-center gap-2 py-3 px-2 rounded-xl border-2 transition-all duration-200 cursor-pointer bg-(--color-surface) theme-icon-btn"
                    class:border-(--color-primary)={preferredTheme === 'sepia'}
                    class:border-(--color-border)={preferredTheme !== 'sepia'}
                    style="--icon-bg: linear-gradient(to bottom right, #fef3c7, #fde68a);"
                    onclick={() => (preferredTheme = 'sepia')}
                    title={t('settings.theme.sepia')}
                  >
                    {#if preferredTheme === 'sepia'}
                      <div class="absolute top-1 right-1 text-(--color-primary)">
                        <Icon name="check" size="sm" />
                      </div>
                    {/if}
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="20"
                      height="20"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      aria-hidden="true"
                      class="theme-icon"
                      ><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path
                        d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
                      /></svg
                    >
                    <span class="text-(--text-2xs) font-medium text-(--color-text-muted)"
                      >{t('settings.theme.sepia')}</span
                    >
                  </button>
                  <button
                    type="button"
                    class="relative flex flex-col items-center gap-2 py-3 px-2 rounded-xl border-2 transition-all duration-200 cursor-pointer bg-(--color-surface) theme-icon-btn"
                    class:border-(--color-primary)={preferredTheme === 'system'}
                    class:border-(--color-border)={preferredTheme !== 'system'}
                    style="--icon-bg: linear-gradient(to bottom right, #e0e7ff, #c7d2fe);"
                    onclick={() => (preferredTheme = 'system')}
                    title={t('settings.theme.system')}
                  >
                    {#if preferredTheme === 'system'}
                      <div class="absolute top-1 right-1 text-(--color-primary)">
                        <Icon name="check" size="sm" />
                      </div>
                    {/if}
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="20"
                      height="20"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                      aria-hidden="true"
                      class="theme-icon"
                      ><rect x="2" y="3" width="20" height="14" rx="2" ry="2" /><line
                        x1="8"
                        y1="21"
                        x2="16"
                        y2="21"
                      /><line x1="12" y1="17" x2="12" y2="21" /></svg
                    >
                    <span class="text-(--text-2xs) font-medium text-(--color-text-muted)"
                      >{t('settings.theme.system')}</span
                    >
                  </button>
                </div>
              </div>

              <div class="mb-2">
                <label class="mb-1 block text-xs text-(--color-text-muted)" for="app-font-scale"
                  >{t('settings.fontScale')}: {preferredFontScale}%</label
                >
                <input
                  type="range"
                  id="app-font-scale"
                  min="80"
                  max="140"
                  bind:value={preferredFontScale}
                  class="w-full h-1.5 appearance-none bg-(--color-border) rounded-full outline-none slider-thumb"
                />
                <div
                  class="flex items-center justify-center h-12 bg-(--color-background) border border-(--color-border) rounded-lg text-(--color-primary) font-medium mt-2"
                  style="font-size: {preferredFontScale * 0.14}px"
                >
                  Aa
                </div>
              </div>
              </div>
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <div class="flex gap-2 mt-4">
                  <Button
                    onclick={() => void saveAppSettings()}
                    disabled={isSavingSettings}
                    size="sm"
                  >
                    {isSavingSettings ? t('settings.saving') : t('settings.savePreferences')}
                  </Button>
                  <Button onclick={() => openResetModal('apariencia')} variant="danger" size="sm">
                    {t('settings.resetDefaults')}
                  </Button>
                </div>
              </div>
            </section>
          </div>
        {:else if activeTab === 'reader'}
          <div
            role="tabpanel"
            id="tabpanel-reader"
            aria-labelledby="tab-reader"
            class="flex-1 overflow-y-auto p-4 flex flex-col gap-4"
          >
            <section class="rounded-xl border border-(--color-border) bg-(--color-surface) overflow-hidden">
              <div class="p-4 space-y-4">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                  {t('settings.appearance.reader')}
                </h3>
              <div class="flex gap-2 justify-stretch mb-4">
                <button
                  type="button"
                  class="flex-1 py-3 px-2 rounded-lg border-2 transition-all duration-200 flex items-center justify-center text-(--text-2xs) font-medium cursor-pointer"
                  class:border-(--color-primary)={readerThemeMode === 'paper'}
                  class:border-(--color-border)={readerThemeMode !== 'paper'}
                  style="--preview-bg: #fafafa; --preview-text: #1a1a1a; --preview-border: #e0e0e0; background: var(--preview-bg); color: var(--preview-text);"
                  onclick={() => (readerThemeMode = 'paper')}
                >
                  {t('settings.reader.themeMode.paper')}
                </button>
                <button
                  type="button"
                  class="flex-1 py-3 px-2 rounded-lg border-2 transition-all duration-200 flex items-center justify-center text-(--text-2xs) font-medium cursor-pointer"
                  class:border-(--color-primary)={readerThemeMode === 'sepia'}
                  class:border-(--color-border)={readerThemeMode !== 'sepia'}
                  style="--preview-bg: #f4ecd8; --preview-text: #5b4636; --preview-border: #d4c4a8; background: var(--preview-bg); color: var(--preview-text);"
                  onclick={() => (readerThemeMode = 'sepia')}
                >
                  {t('settings.reader.themeMode.sepia')}
                </button>
                <button
                  type="button"
                  class="flex-1 py-3 px-2 rounded-lg border-2 transition-all duration-200 flex items-center justify-center text-(--text-2xs) font-medium cursor-pointer"
                  class:border-(--color-primary)={readerThemeMode === 'night'}
                  class:border-(--color-border)={readerThemeMode !== 'night'}
                  style="--preview-bg: #1a1a1a; --preview-text: #e8e8e8; --preview-border: #333333; background: var(--preview-bg); color: var(--preview-text);"
                  onclick={() => (readerThemeMode = 'night')}
                >
                  {t('settings.reader.themeMode.night')}
                </button>
              </div>

              <div class="space-y-4">
                <div class="mb-2">
                  <label
                    class="mb-1 block text-xs text-(--color-text-muted)"
                    for="reader-brightness"
                    >{t('settings.reader.brightness')}: {readerBrightness}%</label
                  >
                  <input
                    type="range"
                    id="reader-brightness"
                    min="50"
                    max="150"
                    bind:value={readerBrightness}
                    class="w-full h-1.5 appearance-none bg-(--color-border) rounded-full outline-none slider-thumb"
                  />
                </div>

                <div class="mb-2">
                  <label class="mb-1 block text-xs text-(--color-text-muted)" for="reader-contrast"
                    >{t('settings.reader.contrast')}: {readerContrast}%</label
                  >
                  <input
                    type="range"
                    id="reader-contrast"
                    min="50"
                    max="150"
                    bind:value={readerContrast}
                    class="w-full h-1.5 appearance-none bg-(--color-border) rounded-full outline-none slider-thumb"
                  />
                </div>

                <div class="mb-2">
                  <label class="mb-1 block text-xs text-(--color-text-muted)" for="reader-font-size"
                    >{t('settings.reader.epub.fontSize')}: {readerEpubFontSize}%</label
                  >
                  <input
                    type="range"
                    id="reader-font-size"
                    min="80"
                    max="200"
                    bind:value={readerEpubFontSize}
                    class="w-full h-1.5 appearance-none bg-(--color-border) rounded-full outline-none slider-thumb"
                  />
                </div>

                <div class="mb-2">
                  <label
                    class="mb-1 block text-xs text-(--color-text-muted)"
                    for="reader-font-family">{t('settings.reader.epub.fontFamily')}</label
                  >
                  <Dropdown
                    options={panelFontFamilyOptions}
                    bind:value={readerEpubFontFamily}
                    class="w-full"
                  />
                </div>

                <div class="flex gap-2 mt-4">
                  <Button
                    onclick={() => void saveAppSettings()}
                    disabled={isSavingSettings}
                    size="sm"
                  >
                    {isSavingSettings ? t('settings.saving') : t('settings.savePreferences')}
                  </Button>
                  <Button onclick={() => openResetModal('reader')} variant="danger" size="sm">
                    {t('settings.resetDefaults')}
                  </Button>
                </div>
              </div>
              </div>
            </section>
          </div>
        {:else if activeTab === 'datos'}
          <div
            role="tabpanel"
            id="tabpanel-datos"
            aria-labelledby="tab-datos"
            class="flex-1 overflow-y-auto p-4 flex flex-col gap-4"
          >
            <section class="rounded-xl border border-(--color-border) bg-(--color-surface) overflow-hidden">
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                  {t('settings.data.exportLibrary')}
                </h3>
                <p class="text-(--text-2xs) text-(--color-text-muted) mb-3">
                  {t('settings.data.exportLibraryDescription')}
                </p>
                <button
                  type="button"
                  class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg border border-(--color-border) bg-(--color-background) cursor-pointer transition-all duration-200 text-(--color-primary) text-xs hover:bg-(--color-surface) hover:border-(--color-text-muted)"
                  onclick={handleExportLibrary}
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    ><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path
                      d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
                    /></svg
                  >
                  <span>{t('settings.data.exportLibraryAction') || 'Exportar biblioteca'}</span>
                </button>
              </div>
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                  {t('settings.data.exportHighlights')}
                </h3>
                <p class="text-(--text-2xs) text-(--color-text-muted) mb-3">
                  {t('settings.data.exportHighlightsDescription')}
                </p>
                <section
                  class="flex flex-col gap-2 p-3 bg-(--color-background) border border-(--color-border) rounded-lg"
                >
                  <div class="flex gap-2">
                    <Dropdown
                      options={exportBookOptions}
                      value={selectedExportBook}
                      class="flex-1"
                      onchange={({ value }) => (selectedExportBook = value)}
                    />
                    <Dropdown
                      options={exportFormatOptions}
                      value={selectedExportFormat}
                      class="w-[90px] shrink-0"
                      onchange={({ value }) => (selectedExportFormat = value as 'json' | 'markdown')}
                    />
                    <button
                      type="button"
                      class="flex items-center gap-2 px-4 py-2 rounded-md border border-(--color-primary) bg-(--color-primary) text-(--color-background) cursor-pointer transition-all duration-200 text-xs font-medium hover:opacity-90 disabled:opacity-60 disabled:cursor-not-allowed"
                      onclick={() => void handleExportHighlights()}
                      disabled={isExportingHighlights}
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="14"
                        height="14"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        ><path
                          d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"
                        /><polyline points="15 3 21 3 21 9" /><line
                          x1="10"
                          y1="14"
                          x2="21"
                          y2="3"
                        /></svg
                      >
                      <span
                        >{isExportingHighlights
                          ? t('settings.data.exporting')
                          : t('settings.data.download')}</span
                      >
                    </button>
                  </div>
                </section>
              </div>
              <div class="p-4">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                  {t('settings.data.clearCache')}
                </h3>
                <p class="text-(--text-2xs) text-(--color-text-muted) mb-3">
                  {t('settings.data.clearCacheDescription')}
                </p>
                <button
                  type="button"
                  class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg border border-red-300 bg-(--color-background) cursor-pointer transition-all duration-200 text-red-500 text-xs hover:bg-red-50 hover:border-red-500 disabled:opacity-60 disabled:cursor-not-allowed"
                  onclick={() => void handleClearCache()}
                  disabled={isClearingCache}
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    ><polyline points="3 6 5 6 21 6" /><path
                      d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
                    /></svg
                  >
                  <span
                    >{isClearingCache
                      ? t('settings.data.clearing')
                      : cacheCleared
                        ? t('settings.data.cleared')
                        : t('settings.data.clearCache')}</span
                  >
                </button>
              </div>
            </section>
          </div>
        {:else if activeTab === 'atajos'}
          <div
            role="tabpanel"
            id="tabpanel-atajos"
            aria-labelledby="tab-atajos"
            class="flex-1 overflow-y-auto p-4 flex flex-col gap-4"
          >
            <section class="rounded-xl border border-(--color-border) bg-(--color-surface) p-4">
              <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
                {t('settings.shortcuts.title')}
              </h3>
              <p class="text-(--text-2xs) text-(--color-text-muted) mb-3">
                {t('settings.shortcuts.description')}
              </p>
              <ul class="m-0 p-0 list-none grid gap-2">
                {#each keyboardShortcuts as shortcut (shortcut.id)}
                  <li class="flex items-center gap-2">
                    <span
                      class="inline-flex items-center justify-center min-w-[86px] px-2 py-1 rounded-md border border-(--color-border) font-mono text-(--text-2xs) text-(--color-primary) bg-(--color-background)"
                      >{shortcut.combo}</span
                    >
                    <span class="text-xs text-(--color-primary)">{t(shortcut.descriptionKey)}</span>
                  </li>
                {/each}
              </ul>
            </section>
          </div>
        {:else if activeTab === 'acerca'}
          <div
            role="tabpanel"
            id="tabpanel-acerca"
            aria-labelledby="tab-acerca"
            class="flex-1 overflow-y-auto p-4 flex flex-col gap-4"
          >
            <section class="rounded-xl border border-(--color-border) bg-(--color-surface) overflow-hidden">
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <div class="flex items-center gap-3">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="32"
                    height="32"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    class="text-(--color-primary)"
                  >
                    <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path
                      d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
                    />
                  </svg>
                  <div class="flex flex-col">
                    <span class="text-lg font-semibold text-(--color-primary)">NextPage</span>
                    <span class="text-xs text-(--color-text-muted)"
                      >Version {typeof __APP_VERSION__ !== 'undefined'
                        ? __APP_VERSION__
                        : '0.1.0'}</span
                    >
                  </div>
                </div>
                <p class="text-sm text-(--color-text-muted) mt-3">
                  A modern e-reader application for enjoying your EPUB collection with a clean,
                  customizable reading experience.
                </p>
              </div>
              <div class="p-4 border-b border-(--color-border) last:border-b-0">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">Credits</h3>
                <ul class="m-0 p-0 list-none">
                  <li
                    class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0"
                  >
                    <span class="text-xs text-(--color-text-muted)">Core Team</span>
                    <span class="text-xs text-(--color-primary) font-medium"
                      >NextPage Contributors</span
                    >
                  </li>
                  <li
                    class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0"
                  >
                    <span class="text-xs text-(--color-text-muted)">EPUB Parsing</span>
                    <span class="text-xs text-(--color-primary) font-medium">epub.js</span>
                  </li>
                  <li
                    class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0"
                  >
                    <span class="text-xs text-(--color-text-muted)">Framework</span>
                    <span class="text-xs text-(--color-primary) font-medium">Svelte / Tauri</span>
                  </li>
                </ul>
              </div>
              <div class="p-4">
                <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">Links</h3>
                <div class="flex gap-2">
                  <Button
                    onclick={() => window.open('https://github.com/anomalyco/nextpage', '_blank')}
                    variant="ghost"
                    size="sm"
                  >
                    GitHub
                  </Button>
                  <Button
                    onclick={() =>
                      window.open('https://github.com/anomalyco/nextpage/issues', '_blank')}
                    variant="ghost"
                    size="sm"
                  >
                    Report Issue
                  </Button>
                </div>
              </div>
            </section>
          </div>
        {/if}
    </form>

    <SettingsResetModal
      show={showResetModal}
      {t}
      onClose={closeResetModal}
      onConfirm={confirmReset}
    />
  </aside>
{/if}
