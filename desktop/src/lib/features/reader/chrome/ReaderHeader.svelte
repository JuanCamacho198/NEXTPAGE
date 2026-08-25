<script lang="ts">
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import ZoomDropdown from './ZoomDropdown.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

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
    // Reading controls (for immersive unified header)
    currentPage?: number;
    totalPages?: number;
    currentPercentage?: number;
    fontSizePercent?: number;
    onPrev?: () => void;
    onNext?: () => void;
    onGoToPage?: (page: number) => Promise<boolean>;
    onFontSizeChange?: (size: number) => void;
  };

  type HeaderVisibleProps = {
    headerVisible?: boolean;
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
    headerVisible = true,
    currentPage,
    totalPages,
    currentPercentage,
    fontSizePercent,
    onPrev,
    onNext,
    onGoToPage,
    onFontSizeChange,
  }: Props & HeaderVisibleProps = $props();

  const headerTransform = $derived(headerVisible ? 'translate-y-0' : '-translate-y-full');
  const showReadingControls = $derived(
    isFullscreen &&
      onPrev !== undefined &&
      onNext !== undefined &&
      currentPage !== undefined &&
      totalPages !== undefined &&
      totalPages > 0,
  );

  let pageInputValue = $state(1);
  $effect(() => {
    if (currentPage !== undefined) pageInputValue = currentPage;
  });

  async function handlePageInput(e: Event): Promise<void> {
    const target = e.target as HTMLInputElement;
    const page = Number.parseInt(target.value, 10);
    if (Number.isFinite(page) && totalPages !== undefined && page >= 1 && page <= totalPages) {
      const ok = await onGoToPage?.(page);
      if (!ok) target.value = String(currentPage);
    } else {
      target.value = String(currentPage);
    }
  }
</script>

<header
  class="flex flex-col border-b border-(--color-surface-strong) bg-(--color-bg-deep) fixed inset-x-0 top-0 z-50 transition-transform duration-300 {headerTransform}"
  class:shadow-md={isFullscreen}
>
  <!-- Top row: Biblioteca + title + tools -->
  <div class="flex h-16 shrink-0 items-center justify-between px-8">
    <!-- Left: back + biblioteca -->
    <div class="flex items-center gap-2">
      <button
        type="button"
        onclick={onBackToHome}
        class="flex cursor-pointer items-center gap-2 text-(--color-text-auxiliary) hover:text-(--color-text-inverse)"
      >
        <Icon name="chevron-left" size="sm" />
        <span class="font-inter text-sm font-medium text-(--color-text-auxiliary)"
          >{t('reader.biblioteca')}</span
        >
      </button>
    </div>

    <!-- Center: book title -->
    <span class="font-inter text-sm font-medium text-(--color-text-auxiliary)">
      {title}
    </span>

    <!-- Right: tools -->
    <div class="flex items-center gap-6 text-(--color-text-auxiliary)">
      <button
        type="button"
        onclick={onToggleToc}
        class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors"
        class:text-(--color-accent-blue)={showTocPanel}
        class:text-(--color-text-auxiliary)={!showTocPanel}
        class:hover:text-(--color-text-inverse)={!showTocPanel}
        class:hover:brightness-125={showTocPanel}
        aria-label={showTocPanel ? t('settings.close') : t('reader.tabla_contenidos')}
      >
        <Icon name={showTocPanel ? 'close' : 'menu'} size="sm" />
      </button>
      <button
        type="button"
        onclick={onToggleSearch}
        class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors"
        class:text-(--color-accent-blue)={searchPanelOpen}
        class:text-(--color-text-auxiliary)={!searchPanelOpen}
        class:hover:text-(--color-text-inverse)={!searchPanelOpen}
        class:hover:brightness-125={searchPanelOpen}
        aria-label={searchPanelOpen ? t('settings.close') : t('epub.search')}
      >
        <Icon name={searchPanelOpen ? 'close' : 'search'} size="sm" />
      </button>
      <button
        type="button"
        onclick={onToggleTextSettings}
        class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors"
        class:text-(--color-accent-blue)={showTextSettings}
        class:text-(--color-text-auxiliary)={!showTextSettings}
        class:hover:text-(--color-text-inverse)={!showTextSettings}
        class:hover:brightness-125={showTextSettings}
        aria-label={showTextSettings ? t('settings.close') : t('reader.ajustes_texto')}
      >
        <Icon name={showTextSettings ? 'close' : 'settings'} size="sm" />
      </button>
      <button
        type="button"
        onclick={onToggleBookmarks}
        class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors"
        class:text-(--color-accent-blue)={showBookmarks}
        class:text-(--color-text-auxiliary)={!showBookmarks}
        class:hover:text-(--color-text-inverse)={!showBookmarks}
        class:hover:brightness-125={showBookmarks}
        aria-label={showBookmarks ? t('settings.close') : t('reader.bookmark')}
      >
        <Icon name={showBookmarks ? 'close' : 'bookmark'} size="sm" />
      </button>
      <button
        type="button"
        onclick={onToggleFullscreen}
        class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors text-(--color-text-auxiliary) hover:text-(--color-text-inverse)"
        aria-label={isFullscreen ? t('pdf.fullscreenExit') : t('pdf.fullscreenEnter')}
      >
        <Icon name={isFullscreen ? 'fullscreen-exit' : 'fullscreen-enter'} size="sm" />
      </button>

    </div>
  </div>

  {#if showReadingControls}
    <!-- Bottom row: reading controls (unified immersive bar) -->
    <div class="flex h-12 shrink-0 items-center justify-center gap-3 border-t border-(--color-surface-strong)/20 px-4 bg-(--color-bg-deep)">
      <button
        type="button"
        onclick={onPrev}
        disabled={currentPage !== undefined && currentPage <= 1}
        class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-surface-strong) rounded bg-transparent text-(--color-text-auxiliary) hover:text-(--color-text-inverse) hover:bg-(--color-surface-strong)/20 cursor-pointer text-xs min-w-8 min-h-8 disabled:opacity-50 disabled:cursor-not-allowed"
        aria-label={t('reader.prev_page')}
      >
        <Icon name="chevron-left" size="sm" />
      </button>
      <button
        type="button"
        onclick={onNext}
        disabled={totalPages !== undefined && currentPage !== undefined && currentPage >= totalPages}
        class="inline-flex items-center justify-center px-2.5 py-1.5 border border-(--color-surface-strong) rounded bg-transparent text-(--color-text-auxiliary) hover:text-(--color-text-inverse) hover:bg-(--color-surface-strong)/20 cursor-pointer text-xs min-w-8 min-h-8 disabled:opacity-50 disabled:cursor-not-allowed"
        aria-label={t('reader.next_page')}
      >
        <Icon name="arrow-right" size="sm" />
      </button>
      <span class="flex items-center gap-1 text-xs text-(--color-text-auxiliary)">
        <input
          type="number"
          min="1"
          max={totalPages}
          value={pageInputValue}
          onchange={handlePageInput}
          class="w-[50px] p-1 border border-(--color-surface-strong) rounded text-center bg-(--color-bg-deep) text-(--color-text-auxiliary)"
          aria-label={t('reader.page_input')}
        />
        <span class="text-xs text-(--color-text-auxiliary) opacity-70">/ {totalPages}</span>
      </span>
      {#if currentPercentage !== undefined}
        <span class="text-xs text-(--color-text-auxiliary) min-w-10 text-center">{Math.round(currentPercentage)}%</span>
      {/if}
      {#if fontSizePercent !== undefined && onFontSizeChange}
        <ZoomDropdown value={fontSizePercent} onSelect={onFontSizeChange} />
      {/if}
    </div>
  {/if}
</header>
