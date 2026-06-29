<script lang="ts">
  import { scale } from 'svelte/transition';
  import { cubicOut } from 'svelte/easing';
  import type { MessageKey } from '$lib/shared/i18n';
  import { HIGHLIGHT_COLORS } from '$lib/features/reader/highlight/highlightColors';

  // The full data we need to persist a highlight. The parent captures this at
  // selection time and passes it back to us as a prop so that the click on a
  // color can forward it directly to the parent's handler. This avoids a race
  // with the browser's `selectionchange` event: even if the parent's global
  // selection state is cleared before our click handler runs, the data the
  // handler needs is already in the argument we pass.
  export type SelectionData = {
    text: string;
    bounds: { left: number; top: number; right: number; bottom: number };
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    pageNumber: number;
    cfi: string | null;
  };

  type Props = {
    selectedText: string;
    selectionBounds: { left: number; top: number; right: number; bottom: number };
    containerRect: { left: number; top: number; width: number; height: number };
    selectionData: SelectionData | null;
    onCopy: () => void;
    onAddToDictionary: (text: string) => void;
    onColorSelect: (color: string, data: SelectionData) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    selectedText,
    selectionBounds,
    containerRect,
    selectionData,
    onCopy,
    onAddToDictionary,
    onColorSelect,
    t,
  }: Props = $props();

  let selectedColor = $state(HIGHLIGHT_COLORS[0].hex);
  let copyFeedback = $state<string | null>(null);
  let dictionaryFeedback = $state<string | null>(null);
  let copyFeedbackTimer: ReturnType<typeof setTimeout> | null = null;
  let dictionaryFeedbackTimer: ReturnType<typeof setTimeout> | null = null;

  const TOOLBAR_HEIGHT_ESTIMATE = 56;
  const TOOLBAR_WIDTH_ESTIMATE = 260;
  const TOOLBAR_EDGE_PADDING = 16;
  const TOOLBAR_OFFSET = 16;

  const selectionCenterX = $derived((selectionBounds.left + selectionBounds.right) / 2);
  const viewerAnchorX = $derived(
    Math.max(
      TOOLBAR_EDGE_PADDING + TOOLBAR_WIDTH_ESTIMATE / 2,
      Math.min(
        selectionCenterX,
        containerRect.width - TOOLBAR_EDGE_PADDING - TOOLBAR_WIDTH_ESTIMATE / 2,
      ),
    ),
  );
  const viewerToolbarX = $derived(Math.max(0, viewerAnchorX - TOOLBAR_WIDTH_ESTIMATE / 2));
  const viewerToolbarY = $derived(
    selectionBounds.top > TOOLBAR_HEIGHT_ESTIMATE + TOOLBAR_OFFSET
      ? selectionBounds.top - TOOLBAR_HEIGHT_ESTIMATE - TOOLBAR_OFFSET
      : selectionBounds.bottom + TOOLBAR_OFFSET,
  );

  const toolbarX = $derived(containerRect.left + viewerToolbarX);
  const toolbarY = $derived(containerRect.top + viewerToolbarY);

  function selectColor(hex: string): void {
    selectedColor = hex;
    // Forward the selection data captured at mount time so the parent's
    // handler can persist the highlight even if the browser's
    // selectionchange has already cleared its global selection state.
    if (selectionData) {
      onColorSelect(hex, selectionData);
    }
  }

  function handleCopy(): void {
    onCopy();
    copyFeedback = t('reader.copiedToClipboard');
    if (copyFeedbackTimer) clearTimeout(copyFeedbackTimer);
    copyFeedbackTimer = setTimeout(() => {
      copyFeedback = null;
      copyFeedbackTimer = null;
    }, 1500);
  }

  function handleAddToDictionary(): void {
    const word = selectedText.trim();
    if (!word) return;
    onAddToDictionary(word);
    dictionaryFeedback = t('reader.addedToDictionary');
    if (dictionaryFeedbackTimer) clearTimeout(dictionaryFeedbackTimer);
    dictionaryFeedbackTimer = setTimeout(() => {
      dictionaryFeedback = null;
      dictionaryFeedbackTimer = null;
    }, 1500);
  }
</script>

<div
  class="selection-toolbar fixed z-50"
  style="left: {toolbarX}px; top: {toolbarY}px;"
  role="presentation"
  in:scale={{ duration: 140, start: 0.92, easing: cubicOut }}
  out:scale={{ duration: 100, start: 0.95, easing: cubicOut }}
  onmouseup={(e) => e.stopPropagation()}
>
  <!-- Tip arrow pointing to selection -->
  <div
    class="mx-auto h-2.5 w-5"
    style="clip-path: polygon(50% 100%, 0 0, 100% 0); background: var(--color-highlight-menu-bg);"
  ></div>

  <!-- Main toolbar -->
  <div
    class="flex items-center gap-3 rounded-full border border-(--color-highlight-menu-border) bg-(--color-highlight-menu-bg) px-4 py-2 shadow-xl"
    role="toolbar"
    aria-label={t('highlight.menuAriaLabel')}
  >
    <!-- Color circles -->
    {#each HIGHLIGHT_COLORS as color}
      <button
        type="button"
        class="h-6 w-6 cursor-pointer rounded-full transition-transform hover:scale-110"
        class:ring-2={selectedColor === color.hex}
        class:ring-white={selectedColor === color.hex}
        style="background-color: {color.hex};"
        onclick={() => selectColor(color.hex)}
        aria-label={t('highlight.selectColor', { color: t(color.i18nKey) })}
      ></button>
    {/each}

    <!-- Separator -->
    <span class="text-base font-normal text-(--color-text-auxiliary)">|</span>

    <!-- Copy -->
    <div class="group relative">
      <button
        type="button"
        class="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-(--color-text-inverse) transition-colors hover:bg-white/10 hover:text-(--color-accent-sky) focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent-sky)"
        onclick={handleCopy}
        aria-label={t('reader.copiar')}
        title={t('reader.copiar')}
      >
        <svg
          class="h-4 w-4"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
        </svg>
      </button>
      <span
        class="pointer-events-none absolute top-full left-1/2 mt-2 -translate-x-1/2 whitespace-nowrap rounded-md bg-black/85 px-2 py-1 text-xs font-medium text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100"
        role="tooltip"
      >
        {t('reader.copiar')}
      </span>
    </div>

    <!-- Add to Dictionary -->
    <div class="group relative">
      <button
        type="button"
        class="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-(--color-text-inverse) transition-colors hover:bg-white/10 hover:text-(--color-accent-sky) focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent-sky)"
        onclick={handleAddToDictionary}
        aria-label={t('reader.addToDictionary')}
        title={t('reader.addToDictionary')}
      >
        <svg
          class="h-4 w-4"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"></path>
          <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"></path>
          <path d="M6 8h2"></path>
          <path d="M6 12h2"></path>
          <path d="M16 8h2"></path>
          <path d="M16 12h2"></path>
        </svg>
      </button>
      <span
        class="pointer-events-none absolute top-full left-1/2 mt-2 -translate-x-1/2 whitespace-nowrap rounded-md bg-black/85 px-2 py-1 text-xs font-medium text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100"
        role="tooltip"
      >
        {t('reader.addToDictionary')}
      </span>
    </div>
  </div>

  <!-- Action feedback (copy / dictionary) -->
  {#if copyFeedback || dictionaryFeedback}
    <div
      class="mt-2 rounded-lg bg-(--color-highlight-menu-bg) px-3 py-1 text-center text-xs text-(--color-text-inverse) shadow-lg"
      role="status"
      aria-live="polite"
    >
      {copyFeedback ?? dictionaryFeedback}
    </div>
  {/if}
</div>
