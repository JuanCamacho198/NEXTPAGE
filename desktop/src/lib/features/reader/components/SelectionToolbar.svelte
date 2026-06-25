<script lang="ts">
  import type { MessageKey } from "$lib/shared/i18n";
  import { HIGHLIGHT_COLORS } from "$lib/features/reader/highlight/highlightColors";

  type Props = {
    selectedText: string;
    selectionBounds: { left: number; top: number; right: number; bottom: number };
    containerRect: { left: number; top: number; width: number; height: number };
    onCopy: () => void;
    onAddToDictionary: (text: string) => void;
    onColorSelect: (color: string) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    selectedText,
    selectionBounds,
    containerRect,
    onCopy,
    onAddToDictionary,
    onColorSelect,
    t,
  }: Props = $props();

  let selectedColor = $state(HIGHLIGHT_COLORS[0].hex);
  let dictionaryFeedback = $state<string | null>(null);

  const TOOLBAR_HEIGHT_ESTIMATE = 56;
  const TOOLBAR_WIDTH_ESTIMATE = 320;
  const TOOLBAR_EDGE_PADDING = 16;
  const TOOLBAR_OFFSET = 16;

  const selectionCenterX = $derived((selectionBounds.left + selectionBounds.right) / 2);
  const viewerAnchorX = $derived(
    Math.max(
      TOOLBAR_EDGE_PADDING + TOOLBAR_WIDTH_ESTIMATE / 2,
      Math.min(
        selectionCenterX,
        containerRect.width - TOOLBAR_EDGE_PADDING - TOOLBAR_WIDTH_ESTIMATE / 2
      )
    )
  );
  const viewerToolbarX = $derived(Math.max(0, viewerAnchorX - TOOLBAR_WIDTH_ESTIMATE / 2));
  const viewerToolbarY = $derived(
    selectionBounds.top > TOOLBAR_HEIGHT_ESTIMATE + TOOLBAR_OFFSET
      ? selectionBounds.top - TOOLBAR_HEIGHT_ESTIMATE - TOOLBAR_OFFSET
      : selectionBounds.bottom + TOOLBAR_OFFSET
  );

  const toolbarX = $derived(containerRect.left + viewerToolbarX);
  const toolbarY = $derived(containerRect.top + viewerToolbarY);

  function selectColor(hex: string): void {
    selectedColor = hex;
    onColorSelect(hex);
  }

  function handleCopy(): void {
    onCopy();
  }

  function handleAddToDictionary(): void {
    const word = selectedText.trim();
    if (!word) return;
    onAddToDictionary(word);
    dictionaryFeedback = t("reader.addedToDictionary");
    window.setTimeout(() => {
      dictionaryFeedback = null;
    }, 1500);
  }
</script>

<div
  class="selection-toolbar fixed z-50"
  style="left: {toolbarX}px; top: {toolbarY}px;"
  role="presentation"
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
    aria-label={t("highlight.menuAriaLabel")}
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
        aria-label={t("highlight.selectColor", { color: t(color.i18nKey) })}
      ></button>
    {/each}

    <!-- Separator -->
    <span class="text-base font-normal text-(--color-text-auxiliary)">|</span>

    <!-- Copy -->
    <button
      type="button"
      class="cursor-pointer text-sm font-medium text-(--color-text-inverse) hover:text-(--color-accent-sky)"
      onclick={handleCopy}
    >
      {t("reader.copiar")}
    </button>

    <!-- Add to Dictionary -->
    <button
      type="button"
      class="cursor-pointer text-sm font-medium text-(--color-text-inverse) hover:text-(--color-accent-sky)"
      onclick={handleAddToDictionary}
    >
      {t("reader.addToDictionary")}
    </button>
  </div>

  <!-- Dictionary feedback -->
  {#if dictionaryFeedback}
    <div
      class="mt-2 rounded-lg bg-(--color-highlight-menu-bg) px-3 py-1 text-center text-xs text-(--color-text-inverse) shadow-lg"
      role="status"
      aria-live="polite"
    >
      {dictionaryFeedback}
    </div>
  {/if}
</div>
