<script lang="ts">
  import { GoogleLoginButton } from "$lib/features/library";
  import { Button, Panel } from "$lib/shared/ui";
  import {
    getSettings,
    upsertSettings,
    getLocaleSetting,
    getReaderSettings,
    upsertReaderSettings,
  } from "$lib/shared/api/tauriClient";
  import { AuthService } from "$lib/shared/services/AuthService";
  import { i18n, type MessageKey } from "$lib/shared/i18n";
  import {
    getProfileInitials,
    normalizeProfileSession,
    type ProfileSessionViewModel,
  } from "../profileSession";
  import type {
    AppSettingDto,
    CommandErrorDto,
    UiLocale,
    ReaderSettings,
    ReaderThemeMode,
  } from "$lib/shared/types";

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
      id: "reader-scroll-up",
      combo: "ArrowUp",
      descriptionKey: "settings.shortcuts.readerScrollUp",
    },
    {
      id: "reader-scroll-down",
      combo: "ArrowDown",
      descriptionKey: "settings.shortcuts.readerScrollDown",
    },
    {
      id: "dialog-close",
      combo: "Escape",
      descriptionKey: "settings.shortcuts.closeDialog",
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
    selectionColor: "#33bbff",
    lineHeight: 1.8,
    letterSpacing: 0,
    paragraphSpacing: 1,
    textAlign: "left",
    direction: "ltr",
    hyphenation: false,
    verticalScrolling: false,
    margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
    showHeader: true,
    showFooter: true,
    showPageNumbers: true,
    progressIndicator: "percentage",
  });

  const applyReaderSettingsToState = (settings: ReaderSettings): void => {
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

  function closePanel(): void {
    if (mode === "page") {
      onRequestClose?.();
      return;
    }

    isOpen = false;
  }

  async function loadAppSettings(): Promise<void> {
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

  async function loadProfileData(): Promise<void> {
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

  async function handleTabChange(tab: "account" | "profile" | "reader" | "appTheme" | "about"): Promise<void> {
    activeTab = tab;
    if (tab === "profile") {
      await loadProfileData();
    }

    if (tab === "reader" || tab === "appTheme") {
      await loadAppSettings();
    }
  }

  function openResetModal(tab: "account" | "reader" | "appTheme"): void {
    pendingResetTab = tab;
    showResetModal = true;
  }

  function closeResetModal(): void {
    showResetModal = false;
    pendingResetTab = null;
  }

  async function confirmReset(): Promise<void> {
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
    : "w-full rounded-xl border border-(--color-border) bg-background shadow-sm flex flex-col overflow-hidden"}>
    <div class="flex items-center justify-between p-4 border-b border-zinc-200">
      <h2 class="m-0 text-lg font-semibold text-emerald-50">{t("settings.title")}</h2>
      <button class="bg-transparent border-none text-xl cursor-pointer text-zinc-600 p-1 flex items-center justify-center hover:text-zinc-900" onclick={closePanel} aria-label={t("settings.close")}>✕</button>
    </div>

    <div class="flex border-b border-(--color-border)">
      <button
        type="button"
        class="flex-1 px-2 py-3 border-none bg-transparent cursor-pointer text-[13px] text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary)"
        class:text-(--color-primary)={activeTab === "account"}
        class:border-(--color-primary)={activeTab === "account"}
        onclick={() => handleTabChange("account")}
      >
        {t("settings.tab.account")}
      </button>
      <button
        type="button"
        class="flex-1 px-2 py-3 border-none bg-transparent cursor-pointer text-[13px] text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary)"
        class:text-(--color-primary)={activeTab === "profile"}
        class:border-(--color-primary)={activeTab === "profile"}
        onclick={() => handleTabChange("profile")}
      >
        {t("settings.tab.profile")}
      </button>
      <button
        type="button"
        class="flex-1 px-2 py-3 border-none bg-transparent cursor-pointer text-[13px] text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary)"
        class:text-(--color-primary)={activeTab === "reader"}
        class:border-(--color-primary)={activeTab === "reader"}
        onclick={() => handleTabChange("reader")}
      >
        {t("settings.tab.reader")}
      </button>
      <button
        type="button"
        class="flex-1 px-2 py-3 border-none bg-transparent cursor-pointer text-[13px] text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary)"
        class:text-(--color-primary)={activeTab === "appTheme"}
        class:border-(--color-primary)={activeTab === "appTheme"}
        onclick={() => handleTabChange("appTheme")}
      >
        {t("settings.tab.appTheme")}
      </button>
      <button
        type="button"
        class="flex-1 px-2 py-3 border-none bg-transparent cursor-pointer text-[13px] text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary)"
        class:text-(--color-primary)={activeTab === "about"}
        class:border-(--color-primary)={activeTab === "about"}
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
                class="w-full rounded border border-(--color-border) bg-(--color-surface) px-2 py-1.5 text-sm text-(--color-text) focus:outline-none focus:ring-2 focus:ring-(--color-primary)"
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
                class="w-full rounded border border-(--color-border) bg-(--color-surface) px-2 py-1.5 text-sm text-(--color-text) focus:outline-none focus:ring-2 focus:ring-(--color-primary)"
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

          <div class="flex gap-3 items-start border border-(--color-border) rounded-xl bg-(--color-surface,#fff) p-3">
            <div class="size-14 shrink-0">
              {#if profile.avatarUrl && !profileAvatarBroken}
                <img
                  src={profile.avatarUrl}
                  alt={t("settings.profile.avatarAlt", { name: profile.name })}
                  class="block size-full rounded-full border border-(--color-border) object-cover"
                  onerror={() => {
                    profileAvatarBroken = true;
                  }}
                />
              {:else}
                <div
                  class="flex size-full items-center justify-center rounded-full border border-(--color-border) text-[14px] font-bold text-(--color-primary)"
                  style="background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface));"
                  aria-hidden="true"
                >
                  {getProfileInitials(profile.name)}
                </div>
              {/if}
            </div>

            <div class="min-w-0 flex-1">
              <p class="m-0 text-[11px] text-(--color-text-muted,#6b7280)">{t("settings.profile.nameLabel")}</p>
              <p class="my-[2px] mb-2 text-[14px] text-(--color-primary) wrap-break-word">{isProfileLoading ? t("settings.profile.loading") : profile.name}</p>

              <p class="m-0 text-[11px] text-(--color-text-muted,#6b7280)">{t("settings.profile.emailLabel")}</p>
              <p class="my-[2px] mb-2 text-[14px] text-(--color-primary) wrap-break-word">{isProfileLoading ? t("settings.profile.loading") : profile.email}</p>

              {#if !profile.isSignedIn}
                <p class="mt-1.5 text-xs text-(--color-text-muted,#6b7280)">{t("settings.profile.signInPrompt")}</p>
              {/if}
            </div>
          </div>

          <div class="border border-(--color-border) rounded-xl bg-(--color-surface,#fff) p-3 mt-3">
            <h4 class="mt-0 mb-2 text-sm font-semibold text-neutral-300">{t("settings.shortcuts.title")}</h4>
            <p class="text-xs text-zinc-600 mb-3">{t("settings.shortcuts.description")}</p>
            <ul class="m-0 p-0 list-none grid gap-2">
              {#each keyboardShortcuts as shortcut (shortcut.id)}
                <li class="flex items-center gap-2">
                  <span class="inline-flex items-center justify-center min-w-[86px] px-2 py-1 border border-(--color-border) rounded-md font-mono text-[11px] text-(--color-primary) bg-(--color-background)">{shortcut.combo}</span>
                  <span class="text-xs text-(--color-primary)">{t(shortcut.descriptionKey)}</span>
                </li>
              {/each}
            </ul>
          </div>
        </Panel>
      {:else if activeTab === "reader"}
        <Panel title={t("settings.tab.reader")} subtitle="Configure your reading experience.">
          <div class="flex gap-2 justify-stretch mb-4">
            <button
              type="button"
              class="flex-1 py-3 px-2 rounded-lg border-2 cursor-pointer transition-all duration-200 hover:scale-[1.02] hover:shadow-md flex items-center justify-center"
              class:border-(--color-primary)={readerThemeMode === "paper"}
              class:!shadow-[0_0_0_2px_var(--color-primary)]={readerThemeMode === "paper"}
              style="border-color: var(--preview-border, #e0e0e0); background: var(--preview-bg, #fafafa);"
              onclick={() => readerThemeMode = "paper"}
            >
              <span style="font-size: 11px; color: var(--preview-text); font-weight: 500;">{t("settings.reader.themeMode.paper")}</span>
            </button>
            <button
              type="button"
              class="flex-1 py-3 px-2 rounded-lg border-2 cursor-pointer transition-all duration-200 hover:scale-[1.02] hover:shadow-md flex items-center justify-center"
              class:border-(--color-primary)={readerThemeMode === "sepia"}
              class:!shadow-[0_0_0_2px_var(--color-primary)]={readerThemeMode === "sepia"}
              style="border-color: var(--preview-border, #d4c4a8); background: var(--preview-bg, #f4ecd8);"
              onclick={() => readerThemeMode = "sepia"}
            >
              <span style="font-size: 11px; color: var(--preview-text); font-weight: 500;">{t("settings.reader.themeMode.sepia")}</span>
            </button>
            <button
              type="button"
              class="flex-1 py-3 px-2 rounded-lg border-2 cursor-pointer transition-all duration-200 hover:scale-[1.02] hover:shadow-md flex items-center justify-center"
              class:border-(--color-primary)={readerThemeMode === "night"}
              class:!shadow-[0_0_0_2px_var(--color-primary)]={readerThemeMode === "night"}
              style="border-color: var(--preview-border, #333333); background: var(--preview-bg, #1a1a1a);"
              onclick={() => readerThemeMode = "night"}
            >
              <span style="font-size: 11px; color: var(--preview-text); font-weight: 500;">{t("settings.reader.themeMode.night")}</span>
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
                class="w-full rounded border border-(--color-border) bg-(--color-surface) px-2 py-1.5 text-sm text-(--color-text) focus:outline-none focus:ring-2 focus:ring-(--color-primary)"
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
            class="rounded-lg border p-3 transition-all duration-300 mb-4"
            style="
              border-color: var(--preview-border);
              background: var(--preview-bg);
              --preview-bg: {preferredTheme === 'light' ? '#ffffff' : preferredTheme === 'dark' ? '#1a1a1a' : '#f4ecd8'};
              --preview-text: {preferredTheme === 'light' ? '#1a1a1a' : preferredTheme === 'dark' ? '#e8e8e8' : '#5b4636'};
              --preview-border: {preferredTheme === 'light' ? '#e0e0e0' : preferredTheme === 'dark' ? '#333333' : '#d4c4a8'};
            "
          >
            <div class="flex items-center gap-2 pb-2 border-b mb-2" style="border-color: var(--preview-border);">
              <span style="color: var(--preview-text); opacity: 0.7;">☰</span>
              <span style="font-size: 12px; font-weight: 600; color: var(--preview-text);">NextPage</span>
            </div>
            <div style="color: var(--preview-text);">
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
          <div class="border border-(--color-border) rounded-lg p-4 bg-(--color-surface)">
            <div class="flex items-center gap-3">
              <span class="text-[32px]">📚</span>
              <div class="flex flex-col">
                <span class="text-lg font-semibold text-(--color-primary)">NextPage</span>
                <span class="text-xs text-(--color-text-muted,var(--color-secondary))">Version {typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'}</span>
              </div>
            </div>
            <p class="text-sm text-zinc-600 mt-3">
              A modern e-reader application for enjoying your EPUB collection with a clean, customizable reading experience.
            </p>
          </div>

          <div class="border border-(--color-border) rounded-lg p-4 bg-(--color-surface) mt-4">
            <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">Credits</h4>
            <ul class="list-none m-0 p-0">
              <li class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0">
                <span class="text-[13px] text-(--color-text-muted,var(--color-secondary))">Core Team</span>
                <span class="text-[13px] text-(--color-primary) font-medium">NextPage Contributors</span>
              </li>
              <li class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0">
                <span class="text-[13px] text-(--color-text-muted,var(--color-secondary))">EPUB Parsing</span>
                <span class="text-[13px] text-(--color-primary) font-medium">epub.js</span>
              </li>
              <li class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0">
                <span class="text-[13px] text-(--color-text-muted,var(--color-secondary))">Framework</span>
                <span class="text-[13px] text-(--color-primary) font-medium">Svelte / Tauri</span>
              </li>
            </ul>
          </div>

          <div class="border border-(--color-border) rounded-lg p-4 bg-(--color-surface) mt-4">
            <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">Links</h4>
            <div class="flex gap-2">
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
        <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-[1001]">
          <div class="bg-(--color-surface,white) rounded-lg p-5 max-w-[320px] w-[90%] shadow-[0_4px_20px_rgba(0,0,0,0.15)]">
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
</style>
