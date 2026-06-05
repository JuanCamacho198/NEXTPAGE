<script lang="ts">
  import Panel from "$lib/components/ui/layout/Panel.svelte";
  import type { MessageKey } from "$lib/shared/i18n";

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    books: { id: string; title: string }[];
    isClearingCache: boolean;
    cacheCleared: boolean;
    selectedExportBook: string;
    selectedExportFormat: "json" | "markdown";
    isExportingHighlights: boolean;
    onClearCache: () => void;
    onExportLibrary: () => void;
    onExportHighlights: () => void;
    onSelectedExportBookChange: (value: string) => void;
    onSelectedExportFormatChange: (value: "json" | "markdown") => void;
  };

  let {
    t,
    books,
    isClearingCache,
    cacheCleared,
    selectedExportBook,
    selectedExportFormat,
    isExportingHighlights,
    onClearCache,
    onExportLibrary,
    onExportHighlights,
    onSelectedExportBookChange,
    onSelectedExportFormatChange,
  }: Props = $props();
</script>

<Panel title={t("settings.data.exportLibrary")} subtitle={t("settings.data.exportLibraryDescription")}>
  <button
    type="button"
    class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg border border-(--color-border) bg-(--color-background) cursor-pointer transition-all duration-200 text-(--color-primary) text-xs hover:bg-(--color-surface) hover:border-(--color-text-muted)"
    onclick={onExportLibrary}
  >
    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
    <span>Exportar biblioteca</span>
  </button>
</Panel>

<Panel title={t("settings.data.exportHighlights")} subtitle={t("settings.data.exportHighlightsDescription")}>
  <div class="flex flex-col gap-2 p-3 bg-(--color-background) border border-(--color-border) rounded-lg">
    <div class="flex gap-2">
      <select
        value={selectedExportBook}
        onchange={(e) => onSelectedExportBookChange((e.target as HTMLSelectElement).value)}
        class="flex-1 rounded-md border border-(--color-border) bg-(--color-surface) px-3 py-2 text-xs text-(--color-primary) cursor-pointer outline-none focus:border-(--color-primary)"
      >
        <option value="all">{t("settings.data.allBooks")}</option>
        {#if books && books.length > 0}
          {#each books as book (book.id)}
            <option value={book.id}>{book.title}</option>
          {/each}
        {/if}
      </select>
      <select
        value={selectedExportFormat}
        onchange={(e) => onSelectedExportFormatChange((e.target as HTMLSelectElement).value as "json" | "markdown")}
        class="w-[90px] shrink-0 rounded-md border border-(--color-border) bg-(--color-surface) px-3 py-2 text-xs text-(--color-primary) cursor-pointer outline-none focus:border-(--color-primary)"
      >
        <option value="json">JSON</option>
        <option value="markdown">{t("settings.data.markdown")}</option>
      </select>
      <button
        type="button"
        class="flex items-center gap-2 px-4 py-2 rounded-md border border-(--color-primary) bg-(--color-primary) text-(--color-background) cursor-pointer transition-all duration-200 text-xs font-medium hover:opacity-90 disabled:opacity-60 disabled:cursor-not-allowed"
        onclick={onExportHighlights}
        disabled={isExportingHighlights}
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
        <span>{isExportingHighlights ? t("settings.data.exporting") : t("settings.data.download")}</span>
      </button>
    </div>
  </div>
</Panel>

<Panel title={t("settings.data.clearCache")} subtitle={t("settings.data.clearCacheDescription")}>
  <button
    type="button"
    class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg border border-red-300 bg-(--color-background) cursor-pointer transition-all duration-200 text-red-500 text-xs hover:bg-red-50 hover:border-red-500 disabled:opacity-60 disabled:cursor-not-allowed"
    onclick={onClearCache}
    disabled={isClearingCache}
  >
    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
    <span>{isClearingCache ? t("settings.data.clearing") : cacheCleared ? t("settings.data.cleared") : t("settings.data.clearCache")}</span>
  </button>
</Panel>
