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

<div class="selection-overlay" aria-hidden="true">
  {#each selectionOverlayRects as rect, index (`${rect.left}-${rect.top}-${index}`)}
    <div
      class="selection-rect"
      style={`left: ${rect.left}px; top: ${rect.top}px; width: ${rect.width}px; height: ${rect.height}px;`}
    ></div>
  {/each}
</div>

<div class="highlights-overlay" role="presentation">
  {#each persistedHighlights.filter((h) => h.pageNumber === currentPage) as hl (hl.id)}
    {#each hl.rects as rect, index (`${hl.id}-${index}`)}
      <div
        class="highlight-rect"
        class:active={activeHighlightId === hl.id}
        style={`left: ${rect.left * scale}px; top: ${rect.top * scale}px; width: ${rect.width * scale}px; height: ${rect.height * scale}px; --highlight-color: ${hl.color};`}
        onclick={(e) => onHighlightClick(hl, e)}
        onkeydown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onHighlightClick(hl, e as unknown as MouseEvent); } }}
        role="button"
        tabindex="0"
        aria-label="Highlight"
      ></div>
    {/each}
  {/each}
</div>

{#if highlightToolbarPos && activeHighlightId}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <div
    class="highlight-manager"
    style="left: {highlightToolbarPos.x}px; top: {highlightToolbarPos.y}px;"
    onclick={(e) => e.stopPropagation()}
    role="toolbar"
    tabindex="-1"
    aria-label="Highlight options"
  >
    {#each HIGHLIGHT_COLORS as color}
      <button
        type="button"
        class="hm-color-btn"
        class:selected={activeHighlightColor === color.hex}
        style="background-color: {color.hex};"
        onclick={() => onHighlightColorPick(color.hex)}
        aria-label={color.label}
      ></button>
    {/each}
    <span class="hm-separator"></span>
    <button
      type="button"
      class="hm-delete-btn"
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
      class="hm-close-btn"
      onclick={onDismissHighlightManager}
      aria-label="Close"
    >
      &times;
    </button>
  </div>
{/if}

<style>
  .selection-overlay,
  .highlights-overlay {
    position: absolute;
    inset: 0;
    z-index: 1;
    pointer-events: none;
  }

  .selection-rect {
    position: absolute;
    border-radius: 4px;
    background: color-mix(in srgb, var(--pdf-selection-color, #3388ff) 42%, transparent);
    box-shadow:
      0 0 0 1px color-mix(in srgb, var(--pdf-selection-color, #3388ff) 22%, transparent),
      0 2px 4px rgba(0, 0, 0, 0.1);
    z-index: 3;
    pointer-events: none;
  }

  .highlight-rect {
    position: absolute;
    border-radius: 4px;
    background: color-mix(in srgb, var(--highlight-color, #FACC15) 48%, transparent);
    box-shadow:
      0 0 0 1px color-mix(in srgb, var(--highlight-color, #FACC15) 25%, transparent);
    z-index: 2;
    pointer-events: auto;
    cursor: pointer;
    transition: background-color 0.15s ease;
  }

  .highlight-rect:hover {
    background: color-mix(in srgb, var(--highlight-color, #FACC15) 60%, transparent);
  }

  .highlight-rect.active {
    background: color-mix(in srgb, var(--highlight-color, #FACC15) 72%, transparent);
    box-shadow:
      0 0 0 2px color-mix(in srgb, var(--highlight-color, #FACC15) 50%, transparent),
      0 0 12px color-mix(in srgb, var(--highlight-color, #FACC15) 30%, transparent);
  }

  /* Highlight manager toolbar — floating fixed menu */
  .highlight-manager {
    position: fixed;
    z-index: 100;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 14px;
    border-radius: 28px;
    background: #1E293B;
    border: 1px solid #334155;
    box-shadow:
      0 8px 24px rgba(0, 0, 0, 0.35),
      0 2px 8px rgba(0, 0, 0, 0.15);
    pointer-events: auto;
  }

  .highlight-manager .hm-color-btn {
    width: 22px;
    height: 22px;
    border: 2px solid rgba(255, 255, 255, 0.6);
    border-radius: 50%;
    cursor: pointer;
    padding: 0;
    transition:
      transform 0.15s ease,
      border-color 0.15s ease;
  }

  .highlight-manager .hm-color-btn:hover {
    transform: scale(1.15);
  }

  .highlight-manager .hm-color-btn.selected {
    border-color: white;
    box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
  }

  .highlight-manager .hm-separator {
    width: 1px;
    height: 20px;
    background: #334155;
    margin: 0 4px;
  }

  .highlight-manager .hm-delete-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border: none;
    border-radius: 50%;
    background: transparent;
    color: #EF4444;
    cursor: pointer;
    transition:
      background-color 0.15s ease,
      transform 0.15s ease;
  }

  .highlight-manager .hm-delete-btn:hover {
    background: rgba(239, 68, 68, 0.15);
    transform: scale(1.1);
  }

  .highlight-manager .hm-close-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border: none;
    border-radius: 50%;
    background: transparent;
    color: #94A3B8;
    cursor: pointer;
    font-size: 14px;
    line-height: 1;
    transition:
      background-color 0.15s ease,
      color 0.15s ease;
  }

  .highlight-manager .hm-close-btn:hover {
    background: rgba(148, 163, 184, 0.15);
    color: #F8FAFC;
  }
</style>
