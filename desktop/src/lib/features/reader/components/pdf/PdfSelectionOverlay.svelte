<script lang="ts">
  import { HIGHLIGHT_COLORS } from "$lib/features/reader/pdf/pdfSelection";

  type PersistedHighlight = {
    id: string;
    color: string;
    pageNumber: number;
    rects: Array<{ left: number; top: number; width: number; height: number }>;
  };

  type SelectionOverlayRect = {
    left: number;
    top: number;
    width: number;
    height: number;
  };

  type HighlightToolbarPos = { x: number; y: number } | null;

  type Props = {
    selectionOverlayRects: SelectionOverlayRect[];
    persistedHighlights: PersistedHighlight[];
    currentPage: number;
    scale: number;
    activeHighlightId: string | null;
    activeHighlightColor: string;
    highlightToolbarPos: HighlightToolbarPos;
    onHighlightClick: (hl: PersistedHighlight, event: MouseEvent) => void;
    onHighlightColorPick: (hex: string) => void;
    onHighlightDelete: () => void;
    onDismissHighlightManager: () => void;
  };

  let {
    selectionOverlayRects,
    persistedHighlights,
    currentPage,
    scale,
    activeHighlightId,
    activeHighlightColor,
    highlightToolbarPos,
    onHighlightClick,
    onHighlightColorPick,
    onHighlightDelete,
    onDismissHighlightManager,
  }: Props = $props();
</script>

<div class="absolute inset-0 z-1 pointer-events-none" aria-hidden="true">
  {#each selectionOverlayRects as rect, index (`${rect.left}-${rect.top}-${index}`)}
    <div
      class="absolute rounded pointer-events-none"
      style="left: {rect.left}px; top: {rect.top}px; width: {rect.width}px; height: {rect.height}px; background: color-mix(in srgb, var(--pdf-selection-color, var(--color-accent-blue)) 42%, transparent); box-shadow: 0 0 0 1px color-mix(in srgb, var(--pdf-selection-color, var(--color-accent-blue)) 22%, transparent), 0 2px 4px rgba(0,0,0,0.1);"
    ></div>
  {/each}
</div>

<div class="absolute inset-0 z-1 pointer-events-none" role="presentation">
  {#each persistedHighlights.filter((h) => h.pageNumber === currentPage) as hl (hl.id)}
    {#each hl.rects as rect, index (`${hl.id}-${index}`)}
      <div
        class="absolute rounded pointer-events-auto cursor-pointer"
        class:z-3={activeHighlightId === hl.id}
        style="left: {rect.left * scale}px; top: {rect.top * scale}px; width: {rect.width * scale}px; height: {rect.height * scale}px; --highlight-color: {hl.color}; background: color-mix(in srgb, var(--highlight-color, #FACC15) 48%, transparent); box-shadow: 0 0 0 1px color-mix(in srgb, var(--highlight-color, #FACC15) 25%, transparent);"
        onmouseenter={(e) => { (e.currentTarget as HTMLElement).style.background = `color-mix(in srgb, var(--highlight-color, #FACC15) 60%, transparent)`; }}
        onmouseleave={(e) => { const isActive = activeHighlightId === hl.id; (e.currentTarget as HTMLElement).style.background = isActive ? `color-mix(in srgb, var(--highlight-color, #FACC15) 72%, transparent)` : `color-mix(in srgb, var(--highlight-color, #FACC15) 48%, transparent)`; }}
        role="button"
        tabindex="0"
        aria-label="Highlight"
        onclick={(e) => onHighlightClick(hl, e)}
        onkeydown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onHighlightClick(hl, e as unknown as MouseEvent); } }}
      ></div>
    {/each}
  {/each}
</div>

{#if highlightToolbarPos && activeHighlightId}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <div
    class="fixed z-[100] flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-slate-800 border border-slate-700 shadow-[0_8px_24px_rgba(0,0,0,0.35),0_2px_8px_rgba(0,0,0,0.15)] pointer-events-auto"
    style="left: {highlightToolbarPos.x}px; top: {highlightToolbarPos.y}px;"
    onclick={(e) => e.stopPropagation()}
    role="toolbar"
    tabindex="-1"
    aria-label="Highlight options"
  >
    {#each HIGHLIGHT_COLORS as color}
      <button
        type="button"
        class="w-[22px] h-[22px] border-2 border-white/60 rounded-full cursor-pointer p-0 transition-[transform,border-color] duration-150 hover:scale-110"
        style="background-color: {color.hex}; {activeHighlightColor === color.hex ? 'border-color: white; box-shadow: 0 0 0 2px rgba(255,255,255,0.3);' : ''}"
        onclick={() => onHighlightColorPick(color.hex)}
        aria-label={color.label}
      ></button>
    {/each}
    <span class="w-px h-5 bg-slate-700 mx-1"></span>
    <button
      type="button"
      class="flex items-center justify-center w-7 h-7 border-none rounded-full bg-transparent text-red-500 cursor-pointer transition-[background-color,transform] duration-150 hover:bg-red-500/15 hover:scale-110"
      onclick={onHighlightDelete}
      aria-label="Delete highlight"
      title="Delete highlight"
    >
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 6h18"></path>
        <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
        <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
      </svg>
    </button>
    <button
      type="button"
      class="flex items-center justify-center w-5 h-5 border-none rounded-full bg-transparent text-slate-400 cursor-pointer text-sm leading-none transition-[background-color,color] duration-150 hover:bg-slate-400/15 hover:text-slate-100"
      onclick={onDismissHighlightManager}
      aria-label="Close"
    >
      &times;
    </button>
  </div>
{/if}
