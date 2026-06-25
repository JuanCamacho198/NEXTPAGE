<script lang="ts">
  import type { MessageKey } from "$lib/shared/i18n";
  import type { TagDto } from "$lib/shared/types/book";
  import { HIGHLIGHT_COLORS } from "$lib/features/reader/highlight/highlightColors";

  type Props = {
    highlightId: string;
    highlightColor: string;
    position: { x: number; y: number };
    assignedTags: TagDto[];
    onColorSelect: (color: string) => void;
    onCustomColor: () => void;
    onCopy: () => void;
    onTag: () => void;
    onNote: () => void;
    onDelete: () => void;
    onClose: () => void;
    setColorPickerAnchor?: (el: HTMLElement | null) => void;
    setTagPopoverAnchor?: (el: HTMLElement | null) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    highlightColor,
    position,
    assignedTags,
    onColorSelect,
    onCustomColor,
    onCopy,
    onTag,
    onNote,
    onDelete,
    onClose,
    setColorPickerAnchor,
    setTagPopoverAnchor,
    t,
  }: Props = $props();

  let customColorBtn = $state<HTMLButtonElement | null>(null);
  let tagBtn = $state<HTMLButtonElement | null>(null);

  const MENU_WIDTH = 232;
  const MENU_HEIGHT_ESTIMATE = 120;
  const PADDING = 8;

  const menuX = $derived(
    Math.max(PADDING, Math.min(position.x, window.innerWidth - MENU_WIDTH - PADDING))
  );
  const menuY = $derived(
    position.y + MENU_HEIGHT_ESTIMATE > window.innerHeight - PADDING
      ? position.y - MENU_HEIGHT_ESTIMATE - PADDING
      : position.y + PADDING
  );

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
    }
  }

  $effect(() => {
    setColorPickerAnchor?.(customColorBtn);
  });

  $effect(() => {
    setTagPopoverAnchor?.(tagBtn);
  });
</script>

<div
  class="fixed z-[100] w-58 rounded-xl border border-(--color-highlight-menu-border) bg-(--color-highlight-menu-bg) p-2 shadow-xl"
  style="left: {menuX}px; top: {menuY}px;"
  role="menu"
  aria-label={t("highlight.contextMenuAriaLabel")}
  onclick={(e) => e.stopPropagation()}
  onkeydown={handleKeydown}
  tabindex="-1"
>
  <!-- Color palette -->
  <div class="flex items-center justify-between px-1 py-1" role="group" aria-label={t("highlight.colors")}>
    {#each HIGHLIGHT_COLORS as color}
      <button
        type="button"
        class="h-6 w-6 cursor-pointer rounded-full border-2 border-transparent transition-transform hover:scale-110"
        class:border-white={highlightColor === color.hex}
        style="background-color: {color.hex};"
        onclick={() => onColorSelect(color.hex)}
        aria-label={t("highlight.selectColor", { color: t(color.i18nKey) })}
      ></button>
    {/each}
    <button
      type="button"
      bind:this={customColorBtn}
      class="flex h-6 w-6 cursor-pointer items-center justify-center rounded-full border border-(--color-highlight-menu-border) bg-(--color-color-picker-bg) text-xs text-(--color-text-inverse) hover:opacity-90"
      onclick={onCustomColor}
      aria-label={t("highlight.customColor")}
      title={t("highlight.customColor")}
    >
      +
    </button>
  </div>

  <!-- Assigned tags chips -->
  {#if assignedTags.length > 0}
    <div class="mt-1 flex flex-wrap gap-1 px-1">
      {#each assignedTags as tag (tag.id)}
        <span
          class="max-w-full truncate rounded-full px-2 py-0.5 text-[10px] font-medium text-(--color-text-inverse)"
          style={tag.color ? `background-color: ${tag.color}33; border: 1px solid ${tag.color}66;` : "background-color: rgba(255,255,255,0.1);"}
        >
          {tag.name}
        </span>
      {/each}
    </div>
  {/if}

  <!-- Actions -->
  <div class="mt-2 border-t border-(--color-highlight-menu-border) pt-1">
    <button
      type="button"
      class="flex w-full cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm text-(--color-text-inverse) hover:bg-white/10"
      onclick={onCopy}
      role="menuitem"
    >
      <span class="text-(--color-text-auxiliary)">📋</span>
      {t("reader.copiar")}
    </button>
    <button
      type="button"
      bind:this={tagBtn}
      class="flex w-full cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm text-(--color-text-inverse) hover:bg-white/10"
      onclick={onTag}
      role="menuitem"
    >
      <span class="text-(--color-text-auxiliary)">🏷️</span>
      {t("highlight.tag")}
    </button>
    <button
      type="button"
      class="flex w-full cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm text-(--color-text-inverse) hover:bg-white/10"
      onclick={onNote}
      role="menuitem"
    >
      <span class="text-(--color-text-auxiliary)">📝</span>
      {t("highlight.note")}
    </button>
    <button
      type="button"
      class="flex w-full cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm text-(--color-error) hover:bg-red-500/10"
      onclick={onDelete}
      role="menuitem"
    >
      <span>🗑️</span>
      {t("reader.eliminar_destacado")}
    </button>
  </div>
</div>

<svelte:window onkeydown={handleKeydown} />
