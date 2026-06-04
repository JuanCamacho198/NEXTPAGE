<script lang="ts">
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
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
  import SettingsGeneralTab from "./SettingsGeneralTab.svelte";
  import SettingsAppearanceTab from "./SettingsAppearanceTab.svelte";
  import SettingsDataTab from "./SettingsDataTab.svelte";
  import SettingsAboutTab from "./SettingsAboutTab.svelte";
  import SettingsResetModal from "./SettingsResetModal.svelte";

  type SettingsTab = "general" | "appearance" | "data" | "about";

  type ShortcutDescriptor = {
    id: string;
    combo: string;
    descriptionKey: MessageKey;
  };

  type Props = {
    isOpen?: boolean;
    mode?: "overlay" | "page";
    onRequestClose?: () => void;
    locale: UiLocale;
    onLocaleChange?: (locale: UiLocale) => void;
    onReaderSettingsChange?: (settings: ReaderSettings) => void;
    books?: { id: string; title: string }[];
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    isOpen = $bindable(false),
    mode = "overlay",
    onRequestClose,
    locale,
    onLocaleChange,
    onReaderSettingsChange,
    books = [],
    t,
  }: Props = $props();

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

  let isProfileLoading = $state(false);
  let profileError = $state<string | null>(null);
  let profileAvatarBroken = $state(false);
  let profile = $state<ProfileSessionViewModel>(normalizeProfileSession(null));

  let isClearingCache = $state(false);
  let cacheCleared = $state(false);
  let selectedExportBook = $state<string>("all");
  let selectedExportFormat = $state<"json" | "markdown">("json");
  let isExportingHighlights = $state(false);

  const keyboardShortcuts: ShortcutDescriptor[] = [
    { id: "reader-prev", combo: "ArrowLeft", descriptionKey: "settings.shortcuts.readerPrev" },
    { id: "reader-next", combo: "ArrowRight", descriptionKey: "settings.shortcuts.readerNext" },
    { id: "reader-scroll-up", combo: "ArrowUp", descriptionKey: "settings.shortcuts.readerScrollUp" },
    { id: "reader-scroll-down", combo: "ArrowDown", descriptionKey: "settings.shortcuts.readerScrollDown" },
    { id: "dialog-close", combo: "Escape", descriptionKey: "settings.shortcuts.closeDialog" },
  ];

  const DEFAULT_VALUES = {
    preferredTheme: "light", preferredFontScale: 100,
    readerThemeMode: "paper" as ReaderThemeMode,
    readerBrightness: 100, readerContrast: 100,
    readerEpubFontSize: 100, readerEpubFontFamily: "sans",
  };

  const SETTINGS_KEY = { THEME: "ui.theme", FONT_SCALE: "reader.fontScale" } as const;

  const clampInteger = (value: number, min: number, max: number) =>
    Math.min(max, Math.max(min, Math.round(value)));

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

  const applyReaderSettingsToState = (settings: ReaderSettings) => {
    readerThemeMode = settings.themeMode;
    readerBrightness = settings.brightness;
    readerContrast = settings.contrast;
    readerEpubFontSize = settings.epub.fontSize;
    readerEpubFontFamily = settings.epub.fontFamily;
  };

  const parseSettingValue = (settings: AppSettingDto[], key: string) => {
    const item = settings.find((entry) => entry.key === key);
    if (!item) return null;
    try { return JSON.parse(item.valueJson) as unknown; } catch { return null; }
  };

  const mapCommandErrorMessage = (error: unknown) => {
    const err = error as Error & { commandError?: CommandErrorDto };
    const fallback = error instanceof Error ? error.message : "Settings command failed.";
    if (err.commandError) return { message: err.commandError.message, recoverable: err.commandError.recoverable };
    return { message: fallback, recoverable: false };
  };

  function closePanel() {
    if (mode === "page") { onRequestClose?.(); return; }
    isOpen = false;
  }

  async function loadAppSettings() {
    settingsError = null; settingsUnavailable = null;
    try {
      const response = await getSettings();
      const nextTheme = parseSettingValue(response, SETTINGS_KEY.THEME);
      const nextFontScale = parseSettingValue(response, SETTINGS_KEY.FONT_SCALE);
      if (typeof nextTheme === "string" && nextTheme.length > 0) preferredTheme = nextTheme;
      if (typeof nextFontScale === "number" && Number.isFinite(nextFontScale))
        preferredFontScale = Math.max(80, Math.min(140, Math.round(nextFontScale)));
      const persistedLocale = i18n.toSupportedLocale(await getLocaleSetting());
      if (persistedLocale) { locale = persistedLocale; onLocaleChange?.(persistedLocale); }
      const readerSettings = await getReaderSettings();
      applyReaderSettingsToState(readerSettings);
      onReaderSettingsChange?.(readerSettings);
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) settingsUnavailable = details.message;
      else settingsError = details.message;
    }
  }

  async function loadProfileData() {
    isProfileLoading = true; profileError = null;
    try {
      const session = await AuthService.getSession();
      profile = normalizeProfileSession(session);
      profileAvatarBroken = false;
    } catch (error) {
      profile = normalizeProfileSession(null);
      profileError = error instanceof Error ? error.message : t("errors.commandFailure");
      profileAvatarBroken = false;
    } finally { isProfileLoading = false; }
  }

  async function saveAppSettings() {
    isSavingSettings = true; settingsError = null; settingsUnavailable = null;
    try {
      await upsertSettings([
        { key: SETTINGS_KEY.THEME, valueJson: JSON.stringify(preferredTheme), updatedAt: new Date().toISOString() },
        { key: SETTINGS_KEY.FONT_SCALE, valueJson: JSON.stringify(preferredFontScale), updatedAt: new Date().toISOString() },
      ]);
      const persistedReaderSettings = await upsertReaderSettings(buildReaderSettingsDraft());
      applyReaderSettingsToState(persistedReaderSettings);
      onReaderSettingsChange?.(persistedReaderSettings);
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) settingsUnavailable = details.message;
      else settingsError = details.message;
    } finally { isSavingSettings = false; }
  }

  async function handleLocaleSelect(value: string) {
    const safeLocale = i18n.toSupportedLocale(value) ?? i18n.FALLBACK_LOCALE;
    locale = safeLocale; onLocaleChange?.(safeLocale);
    settingsError = null; settingsUnavailable = null;
    try { await i18n.setLocale(safeLocale); } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) settingsUnavailable = details.message;
      else settingsError = details.message;
    }
  }

  async function handleTabChange(tab: SettingsTab) {
    activeTab = tab;
    if (tab === "general") await loadProfileData();
    if (tab === "appearance") await loadAppSettings();
  }

  function openResetModal() { showResetModal = true; }
  function closeResetModal() { showResetModal = false; }
  async function confirmReset() {
    if (activeTab === "general" || activeTab === "appearance") {
      preferredTheme = DEFAULT_VALUES.preferredTheme;
      preferredFontScale = DEFAULT_VALUES.preferredFontScale;
    }
    if (activeTab === "appearance") {
      readerThemeMode = DEFAULT_VALUES.readerThemeMode;
      readerBrightness = DEFAULT_VALUES.readerBrightness;
      readerContrast = DEFAULT_VALUES.readerContrast;
      readerEpubFontSize = DEFAULT_VALUES.readerEpubFontSize;
      readerEpubFontFamily = DEFAULT_VALUES.readerEpubFontFamily;
    }
    closeResetModal();
    await saveAppSettings();
  }

  async function handleClearCache() {
    isClearingCache = true;
    try {
      const keysToRemove = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('nextpage_cache_')) keysToRemove.push(key);
      }
      keysToRemove.forEach(key => localStorage.removeItem(key));
      cacheCleared = true;
      setTimeout(() => cacheCleared = false, 3000);
    } catch (e) { console.error('Error clearing cache:', e); }
    finally { isClearingCache = false; }
  }

  function handleExportLibrary() { alert('Exportando biblioteca...'); }

  async function handleExportHighlights() {
    isExportingHighlights = true;
    try {
      const mockHighlights = [
        { text: "The only way to do great work is to love what you do.", note: "Inspiring quote", page: 42, createdAt: "2024-01-15T10:30:00Z" },
        { text: "Stay hungry, stay foolish.", note: "", page: 128, createdAt: "2024-01-16T14:22:00Z" },
      ];
      const bookTitle = selectedExportBook === "all" ? "todos-los-libros"
        : (books.find((b) => b.id === selectedExportBook)?.title || "desconocido").replace(/[^a-zA-Z0-9]/g, "-");
      const date = new Date().toISOString().split('T')[0];
      let content: string, mimeType: string, extension: string;
      if (selectedExportFormat === "markdown") {
        const bookFilter = selectedExportBook === "all" ? "Todos los libros" : (books.find((b) => b.id === selectedExportBook)?.title || "Desconocido");
        let md = `# Resaltados Exportados\n\n- **Fecha:** ${new Date().toLocaleDateString()}\n- **Libro:** ${bookFilter}\n- **Total:** ${mockHighlights.length}\n\n---\n\n`;
        mockHighlights.forEach((h, i) => {
          md += `## Resaltado ${i + 1}\n\n> ${h.text}\n\n${h.note ? `**Nota:** ${h.note}\n\n` : ""}- Pagina ${h.page} | ${new Date(h.createdAt).toLocaleDateString()}\n\n`;
        });
        content = md; mimeType = "text/markdown"; extension = "md";
      } else {
        content = JSON.stringify({
          exportDate: new Date().toISOString(), totalHighlights: mockHighlights.length,
          bookFilter: selectedExportBook === "all" ? "all" : books.find((b) => b.id === selectedExportBook)?.title || "unknown",
          highlights: mockHighlights
        }, null, 2);
        mimeType = "application/json"; extension = "json";
      }
      const blob = new Blob([content], { type: mimeType });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url; a.download = `highlights-${bookTitle}-${date}.${extension}`;
      a.click(); URL.revokeObjectURL(url);
    } catch (e) { console.error("Error exporting highlights:", e); alert("Error al exportar resaltados"); }
    finally { isExportingHighlights = false; }
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
    : "settings-panel-wrapper rounded-xl border border-(--color-border) bg-background shadow-sm flex flex-col overflow-hidden"}>
    <div class="flex items-center justify-between p-4 border-b border-zinc-200">
      <h2 class="m-0 text-lg font-semibold text-emerald-50">{t("settings.title")}</h2>
      <button class="bg-transparent border-none text-xl cursor-pointer text-zinc-600 p-1 flex items-center justify-center hover:text-zinc-900" onclick={closePanel} aria-label={t("settings.close")}><Icon name="close" size="md" /></button>
    </div>

    <!-- Tabs -->
    <div class="flex border-b border-(--color-border) overflow-x-auto">
      <button type="button" onclick={() => handleTabChange("general")} style="color: {activeTab === 'general' ? 'var(--color-primary)' : 'var(--color-text-muted)'}; border-bottom: {activeTab === 'general' ? '2px solid var(--color-primary)' : '2px solid transparent'};" class="flex-1 min-w-fit px-1.5 py-2.5 border-none bg-transparent cursor-pointer text-xs whitespace-nowrap hover:text-(--color-primary)">
        {t("settings.tab.general")}
      </button>
      <button type="button" onclick={() => handleTabChange("appearance")} style="color: {activeTab === 'appearance' ? 'var(--color-primary)' : 'var(--color-text-muted)'}; border-bottom: {activeTab === 'appearance' ? '2px solid var(--color-primary)' : '2px solid transparent'};" class="flex-1 min-w-fit px-1.5 py-2.5 border-none bg-transparent cursor-pointer text-xs whitespace-nowrap hover:text-(--color-primary)">
        {t("settings.tab.appearance")}
      </button>
      <button type="button" onclick={() => handleTabChange("data")} style="color: {activeTab === 'data' ? 'var(--color-primary)' : 'var(--color-text-muted)'}; border-bottom: {activeTab === 'data' ? '2px solid var(--color-primary)' : '2px solid transparent'};" class="flex-1 min-w-fit px-1.5 py-2.5 border-none bg-transparent cursor-pointer text-xs whitespace-nowrap hover:text-(--color-primary)">
        {t("settings.tab.data")}
      </button>
      <button type="button" onclick={() => handleTabChange("about")} style="color: {activeTab === 'about' ? 'var(--color-primary)' : 'var(--color-text-muted)'}; border-bottom: {activeTab === 'about' ? '2px solid var(--color-primary)' : '2px solid transparent'};" class="flex-1 min-w-fit px-1.5 py-2.5 border-none bg-transparent cursor-pointer text-xs whitespace-nowrap hover:text-(--color-primary)">
        {t("settings.tab.about")}
      </button>
    </div>

    <div class="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
      {#if activeTab === "general"}
        <SettingsGeneralTab
          {t} {locale}
          {preferredTheme} {preferredFontScale}
          {settingsError} {settingsUnavailable} {isSavingSettings}
          {isProfileLoading} {profileError} {profileAvatarBroken} {profile}
          keyboardShortcuts={keyboardShortcuts}
          onLocaleChange={(v: string) => void handleLocaleSelect(v)}
          onSaveSettings={() => void saveAppSettings()}
          onOpenResetModal={openResetModal}
          onPreferredThemeChange={(v: string) => preferredTheme = v}
          onPreferredFontScaleChange={(v: number) => preferredFontScale = v}
        />
      {:else if activeTab === "appearance"}
        <SettingsAppearanceTab
          {t} {preferredTheme} {preferredFontScale}
          {readerThemeMode} {readerBrightness} {readerContrast}
          {readerEpubFontSize} {readerEpubFontFamily} {isSavingSettings}
          onSaveSettings={() => void saveAppSettings()}
          onOpenResetModal={openResetModal}
          onPreferredThemeChange={(v: string) => preferredTheme = v}
          onPreferredFontScaleChange={(v: number) => preferredFontScale = v}
          onReaderThemeModeChange={(v: ReaderThemeMode | string) => { readerThemeMode = v as ReaderThemeMode; }}
          onReaderBrightnessChange={(v: number) => readerBrightness = v}
          onReaderContrastChange={(v: number) => readerContrast = v}
          onReaderEpubFontSizeChange={(v: number) => readerEpubFontSize = v}
          onReaderEpubFontFamilyChange={(v: string) => readerEpubFontFamily = v}
        />
      {:else if activeTab === "data"}
        <SettingsDataTab
          {t} {books}
          {isClearingCache} {cacheCleared}
          {selectedExportBook} {selectedExportFormat} {isExportingHighlights}
          onClearCache={handleClearCache}
          onExportLibrary={handleExportLibrary}
          onExportHighlights={handleExportHighlights}
          onSelectedExportBookChange={(v) => selectedExportBook = v}
          onSelectedExportFormatChange={(v) => selectedExportFormat = v}
        />
      {:else if activeTab === "about"}
        <SettingsAboutTab {t} />
      {/if}

      <SettingsResetModal
        show={showResetModal}
        {t}
        onClose={closeResetModal}
        onConfirm={() => void confirmReset()}
      />
    </div>
  </aside>
{/if}
