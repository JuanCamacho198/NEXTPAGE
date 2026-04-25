<script lang="ts">
  import GoogleLoginButton from "$lib/domain/library/GoogleLoginButton.svelte";
  import Button from "$lib/components/ui/Button.svelte";
  import Panel from "$lib/components/ui/Panel.svelte";
  import {
    getSettings,
    upsertSettings,
    getLocaleSetting,
    getReaderSettings,
    upsertReaderSettings,
  } from "$lib/api/tauriClient";
  import { AuthService } from "$lib/services/AuthService";
  import { i18n, type MessageKey } from "$lib/i18n";
  import {
    getProfileInitials,
    normalizeProfileSession,
    type ProfileSessionViewModel,
  } from "./profileSession";
  import type {
    AppSettingDto,
    CommandErrorDto,
    UiLocale,
    ReaderSettings,
    ReaderThemeMode,
  } from "$lib/types";

  let {
    isOpen = $bindable(false),
    mode = "overlay",
    onRequestClose,
    locale,
    onLocaleChange,
    onReaderSettingsChange,
    t,
  } = $props<{
    isOpen: boolean;
    mode?: "overlay" | "page";
    onRequestClose?: () => void;
    locale: UiLocale;
    onLocaleChange?: (locale: UiLocale) => void;
    onReaderSettingsChange?: (settings: ReaderSettings) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  }>();

  let activeTab = $state<"account" | "profile" | "reader" | "appTheme" | "about">("account");

  let preferredTheme = $state("light");
  let preferredFontScale = $state(100);
  let readerThemeMode = $state<ReaderThemeMode>("paper");
  let readerBrightness = $state(100);
  let readerContrast = $state(100);
  let readerEpubFontSize = $state(100);
  let readerEpubFontFamily = $state("sans");
  let settingsError = $state<string | null>(null);
  let settingsUnavailable = $state<string | null>(null);
  let isSavingSettings = $state(false);
  let showResetModal = $state(false);
  let pendingResetTab = $state<"account" | "reader" | "appTheme" | null>(null);
  let isProfileLoading = $state(false);
  let profileError = $state<string | null>(null);
  let profileAvatarBroken = $state(false);
  let profile = $state<ProfileSessionViewModel>(normalizeProfileSession(null));

  type ShortcutDescriptor = {
    id: string;
    combo: string;
    descriptionKey: MessageKey;
  };

  const keyboardShortcuts: ShortcutDescriptor[] = [
    {
      id: "reader-prev",
      combo: "ArrowLeft",
      descriptionKey: "settings.shortcuts.readerPrev",
    },
    {
      id: "reader-next",
      combo: "ArrowRight",
      descriptionKey: "settings.shortcuts.readerNext",
    },
    {
      id: "dialog-close",
      combo: "Escape",
      descriptionKey: "settings.shortcuts.closeDialog",
    },
    {
      id: "menu-open",
      combo: "ArrowDown",
      descriptionKey: "settings.shortcuts.openActionsMenu",
    },
  ];

  const DEFAULT_VALUES = {
    preferredTheme: "light",
    preferredFontScale: 100,
    readerThemeMode: "paper" as ReaderThemeMode,
    readerBrightness: 100,
    readerContrast: 100,
    readerEpubFontSize: 100,
    readerEpubFontFamily: "sans",
  };

  type MaybeCommandError = Error & { commandError?: CommandErrorDto };

  const SETTINGS_KEY = {
    THEME: "ui.theme",
    FONT_SCALE: "reader.fontScale",
  } as const;

  const clampInteger = (value: number, min: number, max: number) => {
    return Math.min(max, Math.max(min, Math.round(value)));
  };

  const normalizeFontFamily = (value: string) => {
    const normalized = value.trim();
    return normalized.length > 0 ? normalized : "sans";
  };

  const buildReaderSettingsDraft = (): ReaderSettings => ({
    themeMode: readerThemeMode,
    brightness: clampInteger(readerBrightness, 50, 150),
    contrast: clampInteger(readerContrast, 50, 150),
    epub: {
      fontSize: clampInteger(readerEpubFontSize, 80, 200),
      fontFamily: normalizeFontFamily(readerEpubFontFamily),
    },
  });

  const applyReaderSettingsToState = (settings: ReaderSettings) => {
    readerThemeMode = settings.themeMode;
    readerBrightness = settings.brightness;
    readerContrast = settings.contrast;
    readerEpubFontSize = settings.epub.fontSize;
    readerEpubFontFamily = settings.epub.fontFamily;
  };

  const parseSettingValue = (settings: AppSettingDto[], key: string) => {
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

  const mapCommandErrorMessage = (error: unknown) => {
    const err = error as MaybeCommandError;
    const fallback = error instanceof Error ? error.message : "Settings command failed.";
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

  function closePanel() {
    if (mode === "page") {
      onRequestClose?.();
      return;
    }

    isOpen = false;
  }

  async function loadAppSettings() {
    settingsError = null;
    settingsUnavailable = null;

    try {
      const response = await getSettings();
      const nextTheme = parseSettingValue(response, SETTINGS_KEY.THEME);
      const nextFontScale = parseSettingValue(response, SETTINGS_KEY.FONT_SCALE);

      if (typeof nextTheme === "string" && nextTheme.length > 0) {
        preferredTheme = nextTheme;
      }

      if (typeof nextFontScale === "number" && Number.isFinite(nextFontScale)) {
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

  async function loadProfileData() {
    isProfileLoading = true;
    profileError = null;

    try {
      const session = await AuthService.getSession();
      profile = normalizeProfileSession(session);
      profileAvatarBroken = false;
    } catch (error) {
      profile = normalizeProfileSession(null);
      profileError = error instanceof Error ? error.message : t("errors.commandFailure");
      profileAvatarBroken = false;
    } finally {
      isProfileLoading = false;
    }
  }

  async function saveAppSettings() {
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

  async function handleLocaleSelect(value: string) {
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

  async function handleTabChange(tab: "account" | "profile" | "reader" | "appTheme" | "about") {
    activeTab = tab;
    if (tab === "profile") {
      await loadProfileData();
    }

    if (tab === "reader" || tab === "appTheme") {
      await loadAppSettings();
    }
  }

  function openResetModal(tab: "account" | "reader" | "appTheme") {
    pendingResetTab = tab;
    showResetModal = true;
  }

  function closeResetModal() {
    showResetModal = false;
    pendingResetTab = null;
  }

  async function confirmReset() {
    if (pendingResetTab === "account") {
      preferredTheme = DEFAULT_VALUES.preferredTheme;
      preferredFontScale = DEFAULT_VALUES.preferredFontScale;
    } else if (pendingResetTab === "reader") {
      readerThemeMode = DEFAULT_VALUES.readerThemeMode;
      readerBrightness = DEFAULT_VALUES.readerBrightness;
      readerContrast = DEFAULT_VALUES.readerContrast;
      readerEpubFontSize = DEFAULT_VALUES.readerEpubFontSize;
      readerEpubFontFamily = DEFAULT_VALUES.readerEpubFontFamily;
    } else if (pendingResetTab === "appTheme") {
      preferredTheme = DEFAULT_VALUES.preferredTheme;
      preferredFontScale = DEFAULT_VALUES.preferredFontScale;
    }
    closeResetModal();
    await saveAppSettings();
  }

  $effect(() => {
    if (isOpen) {
      void loadAppSettings();
      void loadProfileData();
    }
});

</script>

{#if mode === "page" || isOpen}
  {#if mode === "overlay"}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <div class="fixed inset-0 w-screen h-screen bg-black/40 z-[999]" onclick={closePanel}></div>
  {/if}
  <aside class={mode === "overlay"
    ? "fixed top-0 right-0 w-[350px] h-screen bg-white border-l border-zinc-200 shadow-xl z-[1000] flex flex-col animate-[slide-in_0.3s_ease-out]"
    : "w-full rounded-xl border border-[color:var(--color-border)] bg-background shadow-sm flex flex-col overflow-hidden"}>
    <div class="flex items-center justify-between p-4 border-b border-zinc-200">
      <h2 class="m-0 text-lg font-semibold text-emerald-50">{t("settings.title")}</h2>
      <button class="bg-transparent border-none text-xl cursor-pointer text-zinc-600 p-1 flex items-center justify-center hover:text-zinc-900" onclick={closePanel} aria-label={t("settings.close")}>✕</button>
    </div>

    <div class="tabs">
      <button
        type="button"
        class="tab"
        class:active={activeTab === "account"}
        onclick={() => handleTabChange("account")}
      >
        {t("settings.tab.account")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "profile"}
        onclick={() => handleTabChange("profile")}
      >
        {t("settings.tab.profile")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "reader"}
        onclick={() => handleTabChange("reader")}
      >
        {t("settings.tab.reader")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "appTheme"}
        onclick={() => handleTabChange("appTheme")}
      >
        {t("settings.tab.appTheme")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "about"}
        onclick={() => handleTabChange("about")}
      >
        {t("settings.tab.about")}
      </button>
    </div>
    
    <div class="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
      {#if activeTab === "account"}
        <Panel title={t("settings.authentication")} subtitle={t("settings.authDescription")}>
          <GoogleLoginButton />

          {#if settingsUnavailable}
            <p class="mb-2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">
              {settingsUnavailable}
            </p>
          {/if}
          {#if settingsError}
            <p class="mb-2 rounded border border-red-300 bg-red-50 px-2 py-1 text-xs text-red-900">
              {settingsError}
            </p>
          {/if}

          <div class="mt-6 border-t border-zinc-200 pt-4">
            <h3 class="mt-0 mb-2 text-base font-semibold text-zinc-900">{t("settings.localPreferences")}</h3>

            <div class="mb-2">
              <label class="mb-1 block text-xs text-zinc-600" for="locale-select">{t("settings.language")}</label>
              <select
                id="locale-select"
                value={locale}
                onchange={(event) => void handleLocaleSelect((event.currentTarget as HTMLSelectElement).value)}
                class="filter-select"
              >
                <option value="es">{t("settings.languageSpanish")}</option>
                <option value="en">{t("settings.languageEnglish")}</option>
              </select>
            </div>

            <div class="mb-2">
              <label class="mb-1 block text-xs text-zinc-600" for="theme-select">{t("settings.theme")}</label>
              <select
                id="theme-select"
                bind:value={preferredTheme}
                class="filter-select"
              >
                <option value="light">{t("settings.theme.light")}</option>
                <option value="dark">{t("settings.theme.dark")}</option>
                <option value="sepia">{t("settings.theme.sepia")}</option>
              </select>
            </div>

            <div class="mb-2">
              <label class="mb-1 block text-xs text-zinc-600" for="font-scale">{t("settings.fontScale")}: {preferredFontScale}%</label>
              <input
                type="range"
                id="font-scale"
                min="80"
                max="140"
                bind:value={preferredFontScale}
                class="w-full"
              />
            </div>

            <div class="flex gap-2 mt-4">
              <Button onclick={() => void saveAppSettings()} disabled={isSavingSettings} size="sm">
                {isSavingSettings ? t("settings.saving") : t("settings.savePreferences")}
              </Button>
              <Button onclick={() => openResetModal("account")} variant="danger" size="sm">
                {t("settings.resetDefaults")}
              </Button>
            </div>
          </div>
        </Panel>
      {:else if activeTab === "profile"}
        <Panel title={t("settings.tab.profile")} subtitle={t("settings.profile.description")}>
          {#if profileError}
            <p class="mb-2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">
              {profileError}
            </p>
          {/if}

          <div class="profile-card">
            <div class="profile-avatar-wrap">
              {#if profile.avatarUrl && !profileAvatarBroken}
                <img
                  src={profile.avatarUrl}
                  alt={t("settings.profile.avatarAlt", { name: profile.name })}
                  class="profile-avatar"
                  onerror={() => {
                    profileAvatarBroken = true;
                  }}
                />
              {:else}
                <div class="profile-avatar-fallback" aria-hidden="true">
                  {getProfileInitials(profile.name)}
                </div>
              {/if}
            </div>

            <div class="profile-fields">
              <p class="profile-row-label">{t("settings.profile.nameLabel")}</p>
              <p class="profile-row-value">{isProfileLoading ? t("settings.profile.loading") : profile.name}</p>

              <p class="profile-row-label">{t("settings.profile.emailLabel")}</p>
              <p class="profile-row-value">{isProfileLoading ? t("settings.profile.loading") : profile.email}</p>

              {#if !profile.isSignedIn}
                <p class="profile-signin-hint">{t("settings.profile.signInPrompt")}</p>
              {/if}
            </div>
          </div>

          <div class="shortcuts-card">
            <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">{t("settings.shortcuts.title")}</h4>
            <p class="text-xs text-zinc-600 mb-3">{t("settings.shortcuts.description")}</p>
            <ul class="shortcuts-list">
              {#each keyboardShortcuts as shortcut (shortcut.id)}
                <li>
                  <span class="shortcut-combo">{shortcut.combo}</span>
                  <span class="shortcut-description">{t(shortcut.descriptionKey)}</span>
                </li>
              {/each}
            </ul>
          </div>
        </Panel>
      {:else if activeTab === "reader"}
        <Panel title={t("settings.tab.reader")} subtitle="Configure your reading experience.">
          <div class="theme-preview-container mb-4">
            <button
              type="button"
              class="theme-preview-box"
              class:selected={readerThemeMode === "paper"}
              style="--preview-bg: #fafafa; --preview-text: #1a1a1a; --preview-border: #e0e0e0;"
              onclick={() => readerThemeMode = "paper"}
            >
              <span class="preview-label">{t("settings.reader.themeMode.paper")}</span>
            </button>
            <button
              type="button"
              class="theme-preview-box"
              class:selected={readerThemeMode === "sepia"}
              style="--preview-bg: #f4ecd8; --preview-text: #5b4636; --preview-border: #d4c4a8;"
              onclick={() => readerThemeMode = "sepia"}
            >
              <span class="preview-label">{t("settings.reader.themeMode.sepia")}</span>
            </button>
            <button
              type="button"
              class="theme-preview-box"
              class:selected={readerThemeMode === "night"}
              style="--preview-bg: #1a1a1a; --preview-text: #e8e8e8; --preview-border: #333333;"
              onclick={() => readerThemeMode = "night"}
            >
              <span class="preview-label">{t("settings.reader.themeMode.night")}</span>
            </button>
          </div>

          <div class="space-y-4">
            <div class="mb-2">
              <label class="mb-1 block text-xs text-zinc-600" for="reader-brightness">{t("settings.reader.brightness")}: {readerBrightness}%</label>
              <input
                type="range"
                id="reader-brightness"
                min="50"
                max="150"
                bind:value={readerBrightness}
                class="w-full"
              />
            </div>

            <div class="mb-2">
              <label class="mb-1 block text-xs text-zinc-600" for="reader-contrast">{t("settings.reader.contrast")}: {readerContrast}%</label>
              <input
                type="range"
                id="reader-contrast"
                min="50"
                max="150"
                bind:value={readerContrast}
                class="w-full"
              />
            </div>

            <div class="mb-2">
              <label class="mb-1 block text-xs text-zinc-600" for="reader-font-size">{t("settings.reader.epub.fontSize")}: {readerEpubFontSize}%</label>
              <input
                type="range"
                id="reader-font-size"
                min="80"
                max="200"
                bind:value={readerEpubFontSize}
                class="w-full"
              />
            </div>

            <div class="mb-2">
              <label class="mb-1 block text-xs text-zinc-600" for="reader-font-family">{t("settings.reader.epub.fontFamily")}</label>
              <select
                id="reader-font-family"
                bind:value={readerEpubFontFamily}
                class="filter-select"
              >
                <option value="serif">Serif</option>
                <option value="sans-serif">Sans Serif</option>
                <option value="monospace">Monospace</option>
              </select>
            </div>

            <div class="flex gap-2 mt-4">
              <Button onclick={() => void saveAppSettings()} disabled={isSavingSettings} size="sm">
                {isSavingSettings ? t("settings.saving") : t("settings.savePreferences")}
              </Button>
              <Button onclick={() => openResetModal("reader")} variant="danger" size="sm">
                {t("settings.resetDefaults")}
              </Button>
            </div>
          </div>
        </Panel>
      {:else if activeTab === "appTheme"}
        <Panel title={t("settings.tab.appTheme")}>
          <div 
            class="app-theme-preview mb-4"
            style="
              --preview-bg: {preferredTheme === 'light' ? '#ffffff' : preferredTheme === 'dark' ? '#1a1a1a' : '#f4ecd8'};
              --preview-text: {preferredTheme === 'light' ? '#1a1a1a' : preferredTheme === 'dark' ? '#e8e8e8' : '#5b4636'};
              --preview-border: {preferredTheme === 'light' ? '#e0e0e0' : preferredTheme === 'dark' ? '#333333' : '#d4c4a8'};
            "
          >
            <div class="preview-header">
              <span class="preview-icon">☰</span>
              <span class="preview-title">NextPage</span>
            </div>
            <div class="preview-content">
              <p style="font-size: 12px; margin: 4px 0;">Sample text preview</p>
              <p style="font-size: 10px; opacity: 0.7;">Secondary text</p>
            </div>
          </div>

          <div class="theme-selector mb-4">
            <div class="flex gap-2">
              <button
                type="button"
                class="flex-1 py-3 px-4 rounded-lg border-2 transition-colors"
                class:border-zinc-800={preferredTheme === "light"}
                class:border-zinc-200={preferredTheme !== "light"}
                onclick={() => preferredTheme = "light"}
              >
                <div class="h-16 rounded bg-white border border-zinc-200 mb-2"></div>
                <span class="text-xs">{t("settings.theme.light")}</span>
              </button>
              <button
                type="button"
                class="flex-1 py-3 px-4 rounded-lg border-2 transition-colors"
                class:border-zinc-800={preferredTheme === "dark"}
                class:border-zinc-200={preferredTheme !== "dark"}
                onclick={() => preferredTheme = "dark"}
              >
                <div class="h-16 rounded bg-zinc-800 border border-zinc-700 mb-2"></div>
                <span class="text-xs">{t("settings.theme.dark")}</span>
              </button>
              <button
                type="button"
                class="flex-1 py-3 px-4 rounded-lg border-2 transition-colors"
                class:border-zinc-800={preferredTheme === "sepia"}
                class:border-zinc-200={preferredTheme !== "sepia"}
                onclick={() => preferredTheme = "sepia"}
              >
                <div class="h-16 rounded bg-[#f4ecd8] border border-[#d4c4a8] mb-2"></div>
                <span class="text-xs">{t("settings.theme.sepia")}</span>
              </button>
            </div>
          </div>

          <div class="mb-2">
            <label class="mb-1 block text-xs text-zinc-600" for="app-font-scale">{t("settings.fontScale")}: {preferredFontScale}%</label>
            <input
              type="range"
              id="app-font-scale"
              min="80"
              max="140"
              bind:value={preferredFontScale}
              class="w-full"
            />
          </div>

          <div class="flex gap-2 mt-4">
            <Button onclick={() => void saveAppSettings()} disabled={isSavingSettings} size="sm">
              {isSavingSettings ? t("settings.saving") : t("settings.savePreferences")}
            </Button>
            <Button onclick={() => openResetModal("appTheme")} variant="danger" size="sm">
              {t("settings.resetDefaults")}
            </Button>
          </div>
        </Panel>
      {:else if activeTab === "about"}
        <Panel title={t("settings.about")}>
          <div class="about-card">
            <div class="about-logo">
              <span class="logo-icon">📚</span>
              <div class="logo-text">
                <span class="app-name">NextPage</span>
                <span class="app-version">Version {typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'}</span>
              </div>
            </div>
            <p class="about-description text-sm text-zinc-600 mt-3">
              A modern e-reader application for enjoying your EPUB collection with a clean, customizable reading experience.
            </p>
          </div>

          <div class="about-card mt-4">
            <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">Credits</h4>
            <ul class="credits-list">
              <li>
                <span class="credit-label">Core Team</span>
                <span class="credit-value">NextPage Contributors</span>
              </li>
              <li>
                <span class="credit-label">EPUB Parsing</span>
                <span class="credit-value">epub.js</span>
              </li>
              <li>
                <span class="credit-label">Framework</span>
                <span class="credit-value">Svelte / Tauri</span>
              </li>
            </ul>
          </div>

          <div class="about-card mt-4">
            <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">Links</h4>
            <div class="about-links">
              <Button onclick={() => window.open("https://github.com/anomalyco/nextpage", "_blank")} variant="ghost" size="sm">
                GitHub
              </Button>
              <Button onclick={() => window.open("https://github.com/anomalyco/nextpage/issues", "_blank")} variant="ghost" size="sm">
                Report Issue
              </Button>
            </div>
          </div>
        </Panel>
      {/if}

      {#if showResetModal}
        <div class="modal-overlay">
          <div class="modal-content">
            <h3 class="mt-0 mb-2 text-base font-semibold text-zinc-900">{t("settings.resetConfirmTitle")}</h3>
            <p class="text-sm text-zinc-600 mb-4">{t("settings.resetConfirmMessage")}</p>
            <div class="flex gap-2 justify-end">
              <Button onclick={closeResetModal} variant="secondary" size="sm">
                {t("settings.cancel")}
              </Button>
              <Button onclick={confirmReset} variant="danger" size="sm">
                {t("settings.reset")}
              </Button>
            </div>
          </div>
        </div>
      {/if}
    </div>
  </aside>
{/if}

<style>
  @keyframes slide-in {
    from {
      transform: translateX(100%);
    }
    to {
      transform: translateX(0);
    }
  }

  .tabs {
    display: flex;
    border-bottom: 1px solid var(--color-border);
  }

  .tab {
    flex: 1;
    padding: 12px 8px;
    border: none;
    background: transparent;
    cursor: pointer;
    font-size: 13px;
    color: var(--color-text-muted, var(--color-secondary));
    border-bottom: 2px solid transparent;
  }

  .tab:hover {
    color: var(--color-primary);
  }

  .tab.active {
    color: var(--color-primary);
    border-bottom-color: var(--color-primary);
  }

  .filters {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }

  .filter-select {
    flex: 1;
    padding: 6px 8px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    font-size: 13px;
    background: var(--color-surface);
    color: var(--color-primary);
  }

  .loading,
  .empty {
    padding: 24px;
    text-align: center;
    font-size: 13px;
    color: var(--color-text-muted, var(--color-secondary));
  }

  .item-list {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .item {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 8px;
    border-radius: 4px;
    margin-bottom: 8px;
    background: var(--color-surface-hover, var(--color-surface-dim));
  }

  .color-indicator {
    width: 4px;
    height: 100%;
    min-height: 40px;
    border-radius: 2px;
    flex-shrink: 0;
  }

  .item-content {
    flex: 1;
    min-width: 0;
  }

  .theme-preview-container {
    display: flex;
    gap: 8px;
    justify-content: stretch;
  }

  .theme-preview-box {
    flex: 1;
    padding: 12px 8px;
    border-radius: 8px;
    border: 2px solid var(--preview-border, #e0e0e0);
    background: var(--preview-bg, #fafafa);
    cursor: pointer;
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .theme-preview-box:hover {
    transform: scale(1.02);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  .theme-preview-box.selected {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px var(--color-primary);
  }

  .preview-label {
    font-size: 11px;
    color: var(--preview-text);
    font-weight: 500;
  }

  .app-theme-preview {
    border-radius: 8px;
    border: 1px solid var(--preview-border);
    background: var(--preview-bg);
    padding: 12px;
    transition: all 0.3s ease;
  }

  .preview-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--preview-border);
    margin-bottom: 8px;
  }

  .preview-icon {
    color: var(--preview-text);
    opacity: 0.7;
  }

  .preview-title {
    font-size: 12px;
    font-weight: 600;
    color: var(--preview-text);
  }

  .preview-content {
    color: var(--preview-text);
  }

  .modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1001;
  }

  .modal-content {
    background: var(--color-surface, white);
    border-radius: 8px;
    padding: 20px;
    max-width: 320px;
    width: 90%;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  }

  .about-card {
    border: 1px solid var(--color-border);
    border-radius: 8px;
    padding: 16px;
    background: var(--color-surface);
  }

  .about-logo {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .logo-icon {
    font-size: 32px;
  }

  .logo-text {
    display: flex;
    flex-direction: column;
  }

  .app-name {
    font-size: 18px;
    font-weight: 600;
    color: var(--color-primary);
  }

  .app-version {
    font-size: 12px;
    color: var(--color-text-muted, var(--color-secondary));
  }

  .credits-list {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .credits-list li {
    display: flex;
    justify-content: space-between;
    padding: 4px 0;
    border-bottom: 1px solid var(--color-border);
  }

  .credits-list li:last-child {
    border-bottom: none;
  }

  .credit-label {
    font-size: 13px;
    color: var(--color-text-muted, var(--color-secondary));
  }

  .credit-value {
    font-size: 13px;
    color: var(--color-primary);
    font-weight: 500;
  }

  .about-links {
    display: flex;
    gap: 8px;
  }

  .profile-card,
  .shortcuts-card {
    border: 1px solid var(--color-border);
    border-radius: 10px;
    background: var(--color-surface, #fff);
    padding: 12px;
  }

  .shortcuts-card {
    margin-top: 12px;
  }

  .profile-card {
    display: flex;
    gap: 12px;
    align-items: flex-start;
  }

  .profile-avatar-wrap {
    width: 56px;
    height: 56px;
    flex-shrink: 0;
  }

  .profile-avatar,
  .profile-avatar-fallback {
    width: 100%;
    height: 100%;
    border-radius: 9999px;
    border: 1px solid var(--color-border);
  }

  .profile-avatar {
    object-fit: cover;
    display: block;
  }

  .profile-avatar-fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
    color: var(--color-primary);
    background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface));
  }

  .profile-fields {
    min-width: 0;
    flex: 1;
  }

  .profile-row-label {
    margin: 0;
    font-size: 11px;
    color: var(--color-text-muted, #6b7280);
  }

  .profile-row-value {
    margin: 2px 0 8px;
    font-size: 14px;
    color: var(--color-primary);
    word-break: break-word;
  }

  .profile-signin-hint {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--color-text-muted, #6b7280);
  }

  .shortcuts-list {
    margin: 0;
    padding: 0;
    list-style: none;
    display: grid;
    gap: 8px;
  }

  .shortcuts-list li {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .shortcut-combo {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 86px;
    padding: 4px 8px;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
    font-size: 11px;
    color: var(--color-primary);
    background: var(--color-background);
  }

  .shortcut-description {
    font-size: 12px;
    color: var(--color-primary);
  }
</style>
