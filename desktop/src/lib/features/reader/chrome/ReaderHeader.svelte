<script lang="ts">
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    title: string;
    showTocPanel: boolean;
    searchPanelOpen: boolean;
    showTextSettings: boolean;
    showBookmarks: boolean;
    isFullscreen: boolean;
    isRotated?: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onBackToHome: () => void;
    onToggleToc: () => void;
    onToggleSearch: () => void;
    onToggleTextSettings: () => void;
    onToggleBookmarks: () => void;
    onToggleFullscreen: () => void;
    onToggleRotate?: () => void;
  };

  let {
    title,
    showTocPanel,
    searchPanelOpen,
    showTextSettings,
    showBookmarks,
    isFullscreen,
    isRotated = false,
    t,
    onBackToHome,
    onToggleToc,
    onToggleSearch,
    onToggleTextSettings,
    onToggleBookmarks,
    onToggleFullscreen,
    onToggleRotate,
  }: Props = $props();
</script>

<header
  class="flex h-16 shrink-0 items-center justify-between border-b border-(--color-surface-strong) px-8"
  class:hidden={isFullscreen}
>
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
    <button
      type="button"
      onclick={onToggleRotate}
      class="flex items-center justify-center min-w-7 min-h-7 cursor-pointer transition-colors {isRotated ? 'text-(--color-accent-blue)' : 'text-(--color-text-auxiliary) hover:text-(--color-text-inverse)'}"
      aria-label={t('reader.rotateHint')}
      title={t('reader.rotateHint')}
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a9 9 0 1 1-9-9c2.5 0 4.7 1 6.3 2.7L21 8V3h-5l2.3 2.3A7 7 0 1 0 21 12z"/><path d="M12 8v4l3 3"/></svg>
    </button>
  </div>
</header>
