<script lang="ts">
  import GoogleLoginButton from "./GoogleLoginButton.svelte";
  import Button from "./ui/Button.svelte";
  import { listHighlights, deleteHighlight } from "../tauriClient";
  import { listBookmarks, deleteBookmark, listBooks } from "../tauriClient";
  import { getSettings, upsertSettings, getLocaleSetting } from "../tauriClient";
  import { i18n, type MessageKey } from "../i18n";
  import type { AppSettingDto, HighlightDto, BookmarkDto, BookDto, CommandErrorDto, UiLocale } from "../types";

  let {
    isOpen = $bindable(false),
    locale,
    onLocaleChange,
    t,
  } = $props<{
    isOpen: boolean;
    locale: UiLocale;
    onLocaleChange?: (locale: UiLocale) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  }>();

  let activeTab = $state<"auth" | "highlights" | "bookmarks" | "about">("auth");

  let highlights = $state<HighlightDto[]>([]);
  let bookmarks = $state<BookmarkDto[]>([]);
  let books = $state<BookDto[]>([]);
  let isLoading = $state(false);

  let filterColor = $state<string>("");
  let filterBook = $state<string>("");
  let preferredTheme = $state("light");
  let preferredFontScale = $state(100);
  let settingsError = $state<string | null>(null);
  let settingsUnavailable = $state<string | null>(null);
  let isSavingSettings = $state(false);

  type MaybeCommandError = Error & { commandError?: CommandErrorDto };

  const SETTINGS_KEY = {
    THEME: "ui.theme",
    FONT_SCALE: "reader.fontScale",
  } as const;

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
    } catch (error) {
      const details = mapCommandErrorMessage(error);
      if (details.recoverable) {
        settingsUnavailable = details.message;
      } else {
        settingsError = details.message;
      }
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

  async function loadHighlights() {
    isLoading = true;
    try {
      highlights = await listHighlights(filterBook || undefined);
    } catch (err) {
      console.error("Failed to load highlights:", err);
      highlights = [];
    } finally {
      isLoading = false;
    }
  }

  async function loadBookmarks() {
    isLoading = true;
    try {
      bookmarks = await listBookmarks(filterBook || undefined);
    } catch (err) {
      console.error("Failed to load bookmarks:", err);
      bookmarks = [];
    } finally {
      isLoading = false;
    }
  }

  async function loadBooks() {
    try {
      books = await listBooks();
    } catch (err) {
      console.error("Failed to load books:", err);
    }
  }

  async function handleTabChange(tab: "auth" | "highlights" | "bookmarks" | "about") {
    activeTab = tab;
    if (tab === "highlights" || tab === "bookmarks") {
      await loadBooks();
      if (tab === "highlights") {
        await loadHighlights();
      } else {
        await loadBookmarks();
      }
      filterColor = "";
      filterBook = "";
    }

    if (tab === "auth") {
      await loadAppSettings();
    }
  }

  $effect(() => {
    if (isOpen) {
      void loadAppSettings();
    }
  });

  async function handleDeleteHighlight(id: string) {
    try {
      await deleteHighlight(id);
      await loadHighlights();
    } catch (err) {
      console.error("Failed to delete highlight:", err);
    }
  }

  async function handleDeleteBookmark(id: string) {
    try {
      await deleteBookmark(id);
      await loadBookmarks();
    } catch (err) {
      console.error("Failed to delete bookmark:", err);
    }
  }

  function getBookTitle(bookId: string): string {
    return books.find((b) => b.id === bookId)?.title || t("settings.unknownBook");
  }

  function handleFilterChange() {
    if (activeTab === "highlights") {
      loadHighlights();
    } else if (activeTab === "bookmarks") {
      loadBookmarks();
    }
  }
</script>

{#if isOpen}
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
  <div class="fixed inset-0 w-screen h-screen bg-black/40 z-[999]" onclick={closePanel}></div>
  <aside class="fixed top-0 right-0 w-[350px] h-screen bg-white border-l border-gray-200 shadow-xl z-[1000] flex flex-col animate-[slide-in_0.3s_ease-out]">
    <div class="flex items-center justify-between p-4 border-b border-gray-200">
      <h2 class="m-0 text-lg font-semibold text-gray-900">{t("settings.title")}</h2>
      <button class="bg-transparent border-none text-xl cursor-pointer text-gray-600 p-1 flex items-center justify-center hover:text-gray-900" onclick={closePanel} aria-label={t("settings.close")}>✕</button>
    </div>

    <div class="tabs">
      <button
        type="button"
        class="tab"
        class:active={activeTab === "auth"}
        onclick={() => handleTabChange("auth")}
      >
        {t("settings.tab.auth")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "highlights"}
        onclick={() => handleTabChange("highlights")}
      >
        {t("settings.tab.highlights")}
      </button>
      <button
        type="button"
        class="tab"
        class:active={activeTab === "bookmarks"}
        onclick={() => handleTabChange("bookmarks")}
      >
        {t("settings.tab.bookmarks")}
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
      {#if activeTab === "auth"}
        <div class="auth-section">
          <h3 class="mt-0 mb-2 text-base font-semibold text-gray-900">{t("settings.authentication")}</h3>
          <p class="text-sm text-gray-600 mb-4">{t("settings.authDescription")}</p>
          <GoogleLoginButton />

          <div class="mt-6 border-t border-gray-200 pt-4">
            <h3 class="mt-0 mb-2 text-base font-semibold text-gray-900">{t("settings.localPreferences")}</h3>

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

            <div class="mb-2">
              <label class="mb-1 block text-xs text-gray-600" for="locale-select">{t("settings.language")}</label>
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
              <label class="mb-1 block text-xs text-gray-600" for="theme-select">{t("settings.theme")}</label>
              <select id="theme-select" bind:value={preferredTheme} class="filter-select">
                <option value="light">{t("settings.theme.light")}</option>
                <option value="sepia">{t("settings.theme.sepia")}</option>
                <option value="dark">{t("settings.theme.dark")}</option>
              </select>
            </div>

            <div class="mb-3">
              <label class="mb-1 block text-xs text-gray-600" for="font-scale">{t("settings.readerFontScale")} ({preferredFontScale}%)</label>
              <input
                id="font-scale"
                type="range"
                min="80"
                max="140"
                step="5"
                bind:value={preferredFontScale}
                class="w-full"
              />
            </div>

            <Button onclick={() => void saveAppSettings()} disabled={isSavingSettings} size="sm">
              {isSavingSettings ? t("settings.saving") : t("settings.savePreferences")}
            </Button>
          </div>
        </div>
      {:else if activeTab === "highlights"}
        <div class="highlights-section">
          <h3 class="mt-0 mb-2 text-base font-semibold text-gray-900">{t("settings.highlights")}</h3>

          <div class="filters">
            <select
              bind:value={filterBook}
              onchange={handleFilterChange}
              class="filter-select"
            >
              <option value="">{t("settings.allBooks")}</option>
              {#each books as book}
                <option value={book.id}>{book.title}</option>
              {/each}
            </select>
            <select
              bind:value={filterColor}
              onchange={handleFilterChange}
              class="filter-select"
            >
              <option value="">{t("settings.allColors")}</option>
              <option value="#fef08a">{t("settings.color.yellow")}</option>
              <option value="#bbf7d0">{t("settings.color.green")}</option>
              <option value="#bfdbfe">{t("settings.color.blue")}</option>
              <option value="#fbcfe8">{t("settings.color.pink")}</option>
              <option value="#fed7aa">{t("settings.color.orange")}</option>
            </select>
          </div>

          {#if isLoading}
            <div class="loading">{t("settings.loadingHighlights")}</div>
          {:else if highlights.length === 0}
            <div class="empty">{t("settings.noHighlights")}</div>
          {:else}
            <ul class="item-list">
              {#each highlights as highlight}
                <li class="item highlight-item">
                  <div class="color-indicator" style="background-color: {highlight.color};"></div>
                  <div class="item-content">
                    <p class="item-text">{highlight.text}</p>
                    <p class="item-meta">
                      {getBookTitle(highlight.bookId)} - {t("settings.page")} {highlight.pageNumber}
                    </p>
                  </div>
                  <button
                    type="button"
                    class="delete-btn"
                    onclick={() => handleDeleteHighlight(highlight.id)}
                    title={t("settings.deleteHighlight")}
                  >
                    ×
                  </button>
                </li>
              {/each}
            </ul>
          {/if}
        </div>
      {:else if activeTab === "bookmarks"}
        <div class="bookmarks-section">
          <h3 class="mt-0 mb-2 text-base font-semibold text-gray-900">{t("settings.bookmarks")}</h3>

          <div class="filters">
            <select
              bind:value={filterBook}
              onchange={handleFilterChange}
              class="filter-select"
            >
              <option value="">{t("settings.allBooks")}</option>
              {#each books as book}
                <option value={book.id}>{book.title}</option>
              {/each}
            </select>
          </div>

          {#if isLoading}
            <div class="loading">{t("settings.loadingBookmarks")}</div>
          {:else if bookmarks.length === 0}
            <div class="empty">{t("settings.noBookmarks")}</div>
          {:else}
            <ul class="item-list">
              {#each bookmarks as bookmark}
                <li class="item bookmark-item">
                  <div class="item-content">
                    <p class="item-text">{getBookTitle(bookmark.bookId)}</p>
                    <p class="item-meta">{t("settings.page")} {bookmark.pageNumber}</p>
                  </div>
                  <button
                    type="button"
                    class="delete-btn"
                    onclick={() => handleDeleteBookmark(bookmark.id)}
                    title={t("settings.deleteBookmark")}
                  >
                    ×
                  </button>
                </li>
              {/each}
            </ul>
          {/if}
        </div>
      {:else if activeTab === "about"}
        <div class="about-section">
          <h3 class="mt-0 mb-2 text-base font-semibold text-gray-900">{t("settings.about")}</h3>
          <p class="text-sm text-gray-600">
            Version {typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'}
          </p>
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
    border-bottom: 1px solid #e5e7eb;
  }

  .tab {
    flex: 1;
    padding: 12px 8px;
    border: none;
    background: transparent;
    cursor: pointer;
    font-size: 13px;
    color: #6b7280;
    border-bottom: 2px solid transparent;
  }

  .tab:hover {
    color: #374151;
  }

  .tab.active {
    color: #374151;
    border-bottom-color: #374151;
  }

  .filters {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }

  .filter-select {
    flex: 1;
    padding: 6px 8px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    font-size: 13px;
    background: #fff;
  }

  .loading,
  .empty {
    padding: 24px;
    text-align: center;
    font-size: 13px;
    color: #6b7280;
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
    background: #f9fafb;
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

  .item-text {
    margin: 0;
    font-size: 13px;
    color: #374151;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .item-meta {
    margin: 4px 0 0;
    font-size: 12px;
    color: #6b7280;
  }

  .delete-btn {
    width: 20px;
    height: 20px;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: #9ca3af;
    cursor: pointer;
    font-size: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .delete-btn:hover {
    background: #fee2e2;
    color: #dc2626;
  }
</style>
