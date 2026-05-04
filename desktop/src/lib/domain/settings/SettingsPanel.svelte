<script lang="ts">
  import GoogleLoginButton from "$lib/domain/library/GoogleLoginButton.svelte";
  import Button from "$lib/components/ui/forms/Button.svelte";
  import Panel from "$lib/components/ui/layout/Panel.svelte";
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
  import SettingsSidebar, { type SettingsTab } from "./SettingsSidebar.svelte";
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
    books = [],
    t,
  } = $props<{
    isOpen: boolean;
    mode?: "overlay" | "page";
    onRequestClose?: () => void;
    locale: UiLocale;
    onLocaleChange?: (locale: UiLocale) => void;
    onReaderSettingsChange?: (settings: ReaderSettings) => void;
    books?: { id: string; title: string }[];
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  }>();

  let activeTab = $state<SettingsTab>("general");

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
  let pendingResetTab = $state<SettingsTab | null>(null);
  let isProfileLoading = $state(false);
  let profileError = $state<string | null>(null);
  let profileAvatarBroken = $state(false);
  let profile = $state<ProfileSessionViewModel>(normalizeProfileSession(null));

  // Datos y privacidad
  let isClearingCache = $state(false);
  let cacheCleared = $state(false);
  let selectedExportBook = $state<string>("all");
  let selectedExportFormat = $state<"json" | "markdown">("json");
  let isExportingHighlights = $state(false);

  async function handleClearCache() {
    isClearingCache = true;
    try {
      // Limpiar cache del localStorage
      const keysToRemove = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('nextpage_cache_')) {
          keysToRemove.push(key);
        }
      }
      keysToRemove.forEach(key => localStorage.removeItem(key));
      cacheCleared = true;
      setTimeout(() => cacheCleared = false, 3000);
    } catch (e) {
      console.error('Error clearing cache:', e);
    } finally {
      isClearingCache = false;
    }
  }

  function handleExportLibrary() {
    // Simulación - en implementación real would export books as JSON
    alert('Exportando biblioteca...');
  }

  async function handleExportHighlights() {
    isExportingHighlights = true;
    try {
      // Simulación de highlights (en implementación real vendría de la API)
      const mockHighlights = [
        { text: "The only way to do great work is to love what you do.", note: "Inspiring quote", page: 42, createdAt: "2024-01-15T10:30:00Z" },
        { text: "Stay hungry, stay foolish.", note: "", page: 128, createdAt: "2024-01-16T14:22:00Z" },
      ];
      
      const highlightsToExport = mockHighlights;
      const bookTitle = selectedExportBook === "all" ? "todos-los-libros" : (books.find(b => b.id === selectedExportBook)?.title || "desconocido").replace(/[^a-zA-Z0-9]/g, "-");
      const date = new Date().toISOString().split('T')[0];
      let content: string;
      let mimeType: string;
      let extension: string;

      if (selectedExportFormat === "markdown") {
        // Generar Markdown
        const bookFilter = selectedExportBook === "all" ? "Todos los libros" : (books.find(b => b.id === selectedExportBook)?.title || "Desconocido");
        let md = `# Resaltados Exportados\n\n`;
        md += `- **Fecha de exportación:** ${new Date().toLocaleDateString()}\n`;
        md += `- **Libro:** ${bookFilter}\n`;
        md += `- **Total de resaltados:** ${highlightsToExport.length}\n\n---\n\n`;
        
        highlightsToExport.forEach((h, i) => {
          md += `## Resaltado ${i + 1}\n\n`;
          md += `> ${h.text}\n\n`;
          if (h.note) {
            md += `**Nota:** ${h.note}\n\n`;
          }
          md += `- Página ${h.page} | Fecha: ${new Date(h.createdAt).toLocaleDateString()}\n\n`;
        });
        
        content = md;
        mimeType = "text/markdown";
        extension = "md";
      } else {
        // Generar JSON
        const exportData = {
          exportDate: new Date().toISOString(),
          totalHighlights: highlightsToExport.length,
          bookFilter: selectedExportBook === "all" ? "all" : books.find(b => b.id === selectedExportBook)?.title || "unknown",
          highlights: highlightsToExport
        };
        content = JSON.stringify(exportData, null, 2);
        mimeType = "application/json";
        extension = "json";
      }

      const blob = new Blob([content], { type: mimeType });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `highlights-${bookTitle}-${date}.${extension}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error("Error exporting highlights:", e);
      alert("Error al exportar resaltados");
    } finally {
      isExportingHighlights = false;
    }
  }

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

  async function handleTabChange(tab: SettingsTab) {
    activeTab = tab;
    if (tab === "general") {
      await loadProfileData();
    }

    if (tab === "appearance") {
      await loadAppSettings();
    }
  }

  function openResetModal(tab: SettingsTab) {
    pendingResetTab = tab;
    showResetModal = true;
  }

  function closeResetModal() {
    showResetModal = false;
    pendingResetTab = null;
  }

  async function confirmReset() {
    if (pendingResetTab === "general") {
      preferredTheme = DEFAULT_VALUES.preferredTheme;
      preferredFontScale = DEFAULT_VALUES.preferredFontScale;
    } else if (pendingResetTab === "appearance") {
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
    : "settings-panel-wrapper rounded-xl border border-[color:var(--color-border)] bg-background shadow-sm flex overflow-hidden"}>
    <div class="flex items-center justify-between p-4 border-b border-zinc-200">
      <h2 class="m-0 text-lg font-semibold text-emerald-50">{t("settings.title")}</h2>
      <button class="bg-transparent border-none text-xl cursor-pointer text-zinc-600 p-1 flex items-center justify-center hover:text-zinc-900" onclick={closePanel} aria-label={t("settings.close")}><Icon name="close" size="md" /></button>
    </div>

    <!-- Mobile tabs (hidden on desktop) -->
    <div class="tabs md:hidden">
      <button
        type="button"
        class="tab"
        class:active={activeTab === "general"}
        onclick={() => handleTabChange("general")}
      >
        <Icon name="settings" size="sm" />
        {t("settings.tab.general")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "appearance"}
        onclick={() => handleTabChange("appearance")}
      >
        <Icon name="sun" size="sm" />
        {t("settings.tab.appearance")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "data"}
        onclick={() => handleTabChange("data")}
      >
        <Icon name="book" size="sm" />
        {t("settings.tab.data")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "about"}
        onclick={() => handleTabChange("about")}
      >
        <Icon name="library" size="sm" />
        {t("settings.tab.about")}
      </button>
    </div>
    
    <div class="settings-content flex flex-1 overflow-hidden">
      <!-- Desktop sidebar (hidden on mobile) -->
      <div class="hidden md:block">
        <SettingsSidebar bind:activeTab {t} />
      </div>

      <div class="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
        {#if activeTab === "general"}
          <!-- Authentication Section -->
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
          </Panel>

          <!-- Profile Section -->
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
              <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">
                <Icon name="clock" size="sm" />
                {t("settings.shortcuts.title")}
              </h4>
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

          <!-- Language & Theme Preferences -->
          <Panel title={t("settings.localPreferences")} subtitle={t("settings.localPreferencesDescription")}>
            <!-- Diseño de dos columnas -->
            <div class="grid grid-cols-2 gap-4 mb-4">
              <!-- Language selector -->
              <div class="preference-card">
                <label class="preference-label">
                  <Icon name="library" size="sm" />
                  {t("settings.language")}
                </label>
                <div class="language-options">
                  <button
                    type="button"
                    class="language-option"
                    class:selected={locale === "es"}
                    onclick={() => void handleLocaleSelect("es")}
                  >
                    <span class="flag">🇪🇸</span>
                    <span class="lang-name">{t("settings.languageSpanish")}</span>
                  </button>
                  <button
                    type="button"
                    class="language-option"
                    class:selected={locale === "en"}
                    onclick={() => void handleLocaleSelect("en")}
                  >
                    <span class="flag">🇺🇸</span>
                    <span class="lang-name">{t("settings.languageEnglish")}</span>
                  </button>
                </div>
              </div>

              <!-- Theme selector -->
              <div class="preference-card">
                <label class="preference-label">
                  <Icon name="sun" size="sm" />
                  {t("settings.theme")}
                </label>
                <div class="theme-mini-selector">
                  <button
                    type="button"
                    class="theme-mini-option"
                    class:selected={preferredTheme === "light"}
                    onclick={() => preferredTheme = "light"}
                    title={t("settings.theme.light")}
                  >
                    <Icon name="sun" size="sm" />
                  </button>
                  <button
                    type="button"
                    class="theme-mini-option"
                    class:selected={preferredTheme === "dark"}
                    onclick={() => preferredTheme = "dark"}
                    title={t("settings.theme.dark")}
                  >
                    <Icon name="moon" size="sm" />
                  </button>
                  <button
                    type="button"
                    class="theme-mini-option"
                    class:selected={preferredTheme === "sepia"}
                    onclick={() => preferredTheme = "sepia"}
                    title={t("settings.theme.sepia")}
                  >
                    <Icon name="book" size="sm" />
                  </button>
                  <button
                    type="button"
                    class="theme-mini-option"
                    class:selected={preferredTheme === "system"}
                    onclick={() => preferredTheme = "system"}
                    title={t("settings.theme.system")}
                  >
                    <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                      <line x1="8" y1="21" x2="16" y2="21"></line>
                      <line x1="12" y1="17" x2="12" y2="21"></line>
                    </svg>
                  </button>
                </div>
              </div>
            </div>

            <!-- Font scale con preview -->
            <div class="preference-card mb-4">
              <label class="preference-label">{t("settings.fontScale")}: {preferredFontScale}%</label>
              <input
                type="range"
                id="font-scale"
                min="80"
                max="140"
                bind:value={preferredFontScale}
                class="font-scale-slider"
              />
              <div class="font-preview" style="font-size: {preferredFontScale * 0.14}px">
                Aa
              </div>
            </div>

            <div class="flex gap-2 mt-4">
              <Button onclick={() => void saveAppSettings()} disabled={isSavingSettings} size="sm">
                {isSavingSettings ? t("settings.saving") : t("settings.savePreferences")}
              </Button>
              <Button onclick={() => openResetModal("general")} variant="danger" size="sm">
                {t("settings.resetDefaults")}
              </Button>
            </div>
          </Panel>
        {:else if activeTab === "appearance"}
          <!-- App Theme Preview & Selector -->
          <Panel 
            title={t("settings.appearance.appTheme")} 
            subtitle={t("settings.appearance.appThemeDescription")}
          >
            <div 
              class="app-theme-preview mb-4"
              style="
                --preview-bg: {preferredTheme === 'light' ? '#ffffff' : preferredTheme === 'dark' ? '#1a1a1a' : '#f4ecd8'};
                --preview-text: {preferredTheme === 'light' ? '#1a1a1a' : preferredTheme === 'dark' ? '#e8e8e8' : '#5b4636'};
                --preview-border: {preferredTheme === 'light' ? '#e0e0e0' : preferredTheme === 'dark' ? '#333333' : '#d4c4a8'};
              "
            >
              <div class="preview-header">
                <span class="preview-icon"><Icon name="menu" size="sm" /></span>
                <span class="preview-title">NextPage</span>
              </div>
              <div class="preview-content">
                <p style="font-size: 12px; margin: 4px 0;">Sample text preview</p>
                <p style="font-size: 10px; opacity: 0.7;">Secondary text</p>
              </div>
            </div>

            <div class="theme-selector mb-4">
              <p class="text-xs text-zinc-500 mb-2">{t("settings.theme")}</p>
              <div class="grid grid-cols-4 gap-2">
                <button
                  type="button"
                  class="theme-option"
                  class:selected={preferredTheme === "light"}
                  onclick={() => preferredTheme = "light"}
                  title={t("settings.theme.light")}
                >
                  <div class="theme-icon-wrap light">
                    <Icon name="sun" size="md" />
                  </div>
                  <span class="theme-label">{t("settings.theme.light")}</span>
                </button>
                <button
                  type="button"
                  class="theme-option"
                  class:selected={preferredTheme === "dark"}
                  onclick={() => preferredTheme = "dark"}
                  title={t("settings.theme.dark")}
                >
                  <div class="theme-icon-wrap dark">
                    <Icon name="moon" size="md" />
                  </div>
                  <span class="theme-label">{t("settings.theme.dark")}</span>
                </button>
                <button
                  type="button"
                  class="theme-option"
                  class:selected={preferredTheme === "sepia"}
                  onclick={() => preferredTheme = "sepia"}
                  title={t("settings.theme.sepia")}
                >
                  <div class="theme-icon-wrap sepia">
                    <Icon name="book" size="md" />
                  </div>
                  <span class="theme-label">{t("settings.theme.sepia")}</span>
                </button>
                <button
                  type="button"
                  class="theme-option"
                  class:selected={preferredTheme === "system"}
                  onclick={() => preferredTheme = "system"}
                  title={t("settings.theme.system")}
                >
                  <div class="theme-icon-wrap system">
                    <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                      <line x1="8" y1="21" x2="16" y2="21"></line>
                      <line x1="12" y1="17" x2="12" y2="21"></line>
                    </svg>
                  </div>
                  <span class="theme-label">{t("settings.theme.system")}</span>
                </button>
              </div>
            </div>

            <div class="mb-4">
              <label class="preference-label">{t("settings.fontScale")}: {preferredFontScale}%</label>
              <input
                type="range"
                id="app-font-scale"
                min="80"
                max="140"
                bind:value={preferredFontScale}
                class="font-scale-slider"
              />
              <div class="font-preview" style="font-size: {preferredFontScale * 0.14}px">
                Aa
              </div>
            </div>
          </Panel>

          <!-- Reader Settings -->
          <Panel 
            title={t("settings.appearance.reader")} 
            subtitle={t("settings.appearance.readerDescription")}
          >
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
                <Button onclick={() => openResetModal("appearance")} variant="danger" size="sm">
                  {t("settings.resetDefaults")}
                </Button>
              </div>
            </div>
          </Panel>
      {:else if activeTab === "data"}
        <!-- Export Library -->
        <Panel 
          title={t("settings.data.exportLibrary")} 
          subtitle={t("settings.data.exportLibraryDescription")}
        >
          <button type="button" class="privacy-action" onclick={handleExportLibrary}>
            <Icon name="book" size="sm" />
            <span>Exportar biblioteca</span>
          </button>
        </Panel>

        <!-- Export Highlights -->
        <Panel 
          title={t("settings.data.exportHighlights")} 
          subtitle={t("settings.data.exportHighlightsDescription")}
        >
          <div class="export-highlights-section">
            <div class="export-controls">
              <select 
                bind:value={selectedExportBook}
                class="export-select"
              >
                <option value="all">{t("settings.data.allBooks")}</option>
                {#if books && books.length > 0}
                  {#each books as book (book.id)}
                    <option value={book.id}>{book.title}</option>
                  {/each}
                {/if}
              </select>
              <select 
                bind:value={selectedExportFormat}
                class="export-select format-select"
              >
                <option value="json">JSON</option>
                <option value="markdown">{t("settings.data.markdown")}</option>
              </select>
              <button 
                type="button" 
                class="privacy-action-btn"
                onclick={handleExportHighlights}
                disabled={isExportingHighlights}
              >
                <Icon name="note" size="sm" />
                <span>{isExportingHighlights ? t("settings.data.exporting") : t("settings.data.download")}</span>
              </button>
            </div>
          </div>
        </Panel>

        <!-- Clear Cache -->
        <Panel 
          title={t("settings.data.clearCache")} 
          subtitle={t("settings.data.clearCacheDescription")}
        >
          <button type="button" class="privacy-action danger" onclick={handleClearCache} disabled={isClearingCache}>
            <Icon name="trash" size="sm" />
            <span>{isClearingCache ? t("settings.data.clearing") : cacheCleared ? t("settings.data.cleared") : t("settings.data.clearCache")}</span>
          </button>
        </Panel>
      {:else if activeTab === "about"}
        <Panel title={t("settings.about")}>
          <div class="about-card">
            <div class="about-logo">
              <span class="logo-icon"><Icon name="library" size="lg" /></span>
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

  .theme-option {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 12px 8px;
    border-radius: 12px;
    border: 2px solid var(--color-border);
    background: var(--color-surface);
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .theme-option:hover {
    border-color: var(--color-text-muted);
    transform: translateY(-2px);
  }

  .theme-option.selected {
    border-color: var(--color-primary);
    background: rgba(78, 140, 255, 0.1);
  }

  .theme-icon-wrap {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .theme-icon-wrap.light {
    background: linear-gradient(135deg, #fff 0%, #f3f4f6 100%);
    color: #f59e0b;
  }

  .theme-icon-wrap.dark {
    background: linear-gradient(135deg, #3f3f46 0%, #18181b 100%);
    color: #a1a1aa;
  }

  .theme-icon-wrap.sepia {
    background: linear-gradient(135deg, #f4ecd8 0%, #d4c4a8 100%);
    color: #92400e;
  }

  .theme-icon-wrap.system {
    background: linear-gradient(135deg, #e0e7ff 0%, #c7d2fe 100%);
    color: #4f46e5;
  }

  .theme-label {
    font-size: 11px;
    font-weight: 500;
    color: var(--color-text-muted);
  }

  .theme-option.selected .theme-label {
    color: var(--color-primary);
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

  /* Preference cards */
  .preference-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 12px;
    padding: 16px;
  }

  .preference-label {
    display: block;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-text-muted);
    margin-bottom: 12px;
  }

  /* Language options */
  .language-options {
    display: flex;
    gap: 8px;
  }

  .language-option {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 10px 12px;
    border: 2px solid var(--color-border);
    border-radius: 8px;
    background: var(--color-background);
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .language-option:hover {
    border-color: var(--color-text-muted);
  }

  .language-option.selected {
    border-color: var(--color-primary);
    background: rgba(78, 140, 255, 0.1);
  }

  .language-option .flag {
    font-size: 20px;
  }

  .language-option .lang-name {
    font-size: 13px;
    color: var(--color-primary);
    font-weight: 500;
  }

  /* Theme mini selector */
  .theme-mini-selector {
    display: flex;
    gap: 8px;
  }

  .theme-mini-option {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 10px;
    border: 2px solid var(--color-border);
    border-radius: 8px;
    background: var(--color-background);
    cursor: pointer;
    transition: all 0.2s ease;
    color: var(--color-text-muted);
  }

  .theme-mini-option:hover {
    border-color: var(--color-text-muted);
    color: var(--color-primary);
  }

  .theme-mini-option.selected {
    border-color: var(--color-primary);
    background: rgba(78, 140, 255, 0.1);
    color: var(--color-primary);
  }

  /* Font scale slider */
  .font-scale-slider {
    width: 100%;
    height: 6px;
    -webkit-appearance: none;
    appearance: none;
    background: var(--color-border);
    border-radius: 3px;
    outline: none;
    margin-bottom: 8px;
  }

  .font-scale-slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--color-primary);
    cursor: pointer;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  }

  .font-preview {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 48px;
    background: var(--color-background);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    color: var(--color-primary);
    font-weight: 500;
  }

  /* Datos y privacidad */
  .privacy-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    color: var(--color-primary);
    margin: 0 0 12px 0;
  }

  .privacy-actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .privacy-action {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border: 1px solid var(--color-border);
    border-radius: 8px;
    background: var(--color-background);
    cursor: pointer;
    transition: all 0.2s ease;
    color: var(--color-primary);
    font-size: 13px;
  }

  .privacy-action:hover {
    background: var(--color-surface);
    border-color: var(--color-text-muted);
  }

  .privacy-action.danger {
    color: #ef4444;
    border-color: #fca5a5;
  }

  .privacy-action.danger:hover {
    background: #fef2f2;
    border-color: #ef4444;
  }

  .privacy-action:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .export-highlights-section {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px;
    background: var(--color-background);
    border: 1px solid var(--color-border);
    border-radius: 8px;
  }

  .export-label {
    font-size: 12px;
    font-weight: 500;
    color: var(--color-text-muted);
  }

  .export-controls {
    display: flex;
    gap: 8px;
  }

  .export-select {
    flex: 1;
    padding: 8px 12px;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    background: var(--color-surface);
    color: var(--color-primary);
    font-size: 13px;
    cursor: pointer;
  }

  .export-select.format-select {
    flex: 0 0 90px;
  }

  .export-select:focus {
    outline: none;
    border-color: var(--color-primary);
  }

  .privacy-action-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    border: 1px solid var(--color-primary);
    border-radius: 6px;
    background: var(--color-primary);
    color: var(--color-background);
    cursor: pointer;
    transition: all 0.2s ease;
    font-size: 13px;
    font-weight: 500;
  }

  .privacy-action-btn:hover {
    opacity: 0.9;
    transform: translateY(-1px);
  }

  .privacy-action-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
  }
</style>
