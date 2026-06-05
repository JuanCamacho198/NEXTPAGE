<script lang="ts">
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
  import type { MessageKey } from "$lib/shared/i18n";

  type Props = {
    title: string;
    showTocPanel: boolean;
    searchPanelOpen: boolean;
    showTextSettings: boolean;
    showBookmarks: boolean;
    isFullscreen: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onBackToHome: () => void;
    onToggleToc: () => void;
    onToggleSearch: () => void;
    onToggleTextSettings: () => void;
    onToggleBookmarks: () => void;
    onToggleFullscreen: () => void;
  };

  let {
    title,
    showTocPanel,
    searchPanelOpen,
    showTextSettings,
    showBookmarks,
    isFullscreen,
    t,
    onBackToHome,
    onToggleToc,
    onToggleSearch,
    onToggleTextSettings,
    onToggleBookmarks,
    onToggleFullscreen,
  }: Props = $props();
</script>

<header class="flex h-16 shrink-0 items-center justify-between border-b border-[#1E293B] px-8" class:hidden={isFullscreen}>
  <!-- Left: back + biblioteca -->
  <div class="flex items-center gap-2">
    <button type="button" onclick={onBackToHome} class="flex cursor-pointer items-center gap-2 text-[#94A3B8] hover:text-white">
      <Icon name="chevron-left" size="sm" />
      <span class="font-inter text-sm font-medium text-[#94A3B8]">{t("reader.biblioteca")}</span>
    </button>
  </div>

  <!-- Center: book title -->
  <span class="font-inter text-sm font-medium text-[#94A3B8]">
    {title}
  </span>

  <!-- Right: tools -->
  <div class="flex items-center gap-6 text-[#94A3B8]">
    <button type="button" onclick={onToggleToc} class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors" class:text-[#49d4ff]={showTocPanel} class:text-[#94A3B8]={!showTocPanel} class:hover:text-white={!showTocPanel} class:hover:brightness-125={showTocPanel} aria-label={showTocPanel ? t("settings.close") : t("reader.tabla_contenidos")}>
      <Icon name={showTocPanel ? "close" : "menu"} size="sm" />
    </button>
    <button type="button" onclick={onToggleSearch} class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors" class:text-[#49d4ff]={searchPanelOpen} class:text-[#94A3B8]={!searchPanelOpen} class:hover:text-white={!searchPanelOpen} class:hover:brightness-125={searchPanelOpen} aria-label={searchPanelOpen ? t("settings.close") : t("epub.search")}>
      <Icon name={searchPanelOpen ? "close" : "search"} size="sm" />
    </button>
    <button type="button" onclick={onToggleTextSettings} class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors" class:text-[#49d4ff]={showTextSettings} class:text-[#94A3B8]={!showTextSettings} class:hover:text-white={!showTextSettings} class:hover:brightness-125={showTextSettings} aria-label={showTextSettings ? t("settings.close") : t("reader.ajustes_texto")}>
      <Icon name={showTextSettings ? "close" : "settings"} size="sm" />
    </button>
    <button type="button" onclick={onToggleBookmarks} class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors" class:text-[#49d4ff]={showBookmarks} class:text-[#94A3B8]={!showBookmarks} class:hover:text-white={!showBookmarks} class:hover:brightness-125={showBookmarks} aria-label={showBookmarks ? t("settings.close") : t("reader.bookmark")}>
      <Icon name={showBookmarks ? "close" : "bookmark"} size="sm" />
    </button>
    <button type="button" onclick={onToggleFullscreen} class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors text-[#94A3B8] hover:text-white" aria-label={isFullscreen ? t("pdf.fullscreenExit") : t("pdf.fullscreenEnter")}>
      <Icon name={isFullscreen ? "fullscreen-exit" : "fullscreen-enter"} size="sm" />
    </button>
  </div>
</header>
