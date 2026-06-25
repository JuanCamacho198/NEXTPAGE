<script lang="ts">
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

  type Props = {
    selectionOverlayRects: SelectionOverlayRect[];
    persistedHighlights: PersistedHighlight[];
    currentPage: number;
    scale: number;
    activeHighlightId: string | null;
    onHighlightClick: (hl: PersistedHighlight, event: MouseEvent) => void;
  };

  let {
    selectionOverlayRects,
    persistedHighlights,
    currentPage,
    scale,
    activeHighlightId,
    onHighlightClick,
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
