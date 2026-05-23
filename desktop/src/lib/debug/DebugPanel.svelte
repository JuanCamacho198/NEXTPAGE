<script lang="ts">
  import { debugState } from "./debugState.svelte";
</script>

{#if debugState.enabled}
  <div
    class="fixed top-4 right-4 z-[9999] w-80 max-h-[80vh] overflow-y-auto rounded-lg border bg-[var(--color-surface)] text-xs font-mono text-[var(--color-primary)] shadow-xl"
    role="region"
    aria-label="Debug information"
  >
    <!-- Route -->
    <div class="border-b border-[var(--color-border)] p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Route</h4>
      <p class="text-sm">{debugState.currentRoute || "Unknown Route"}</p>
    </div>

    <!-- Reader Info -->
    <div class="border-b border-[var(--color-border)] p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Reader Info</h4>
      {#if debugState.readerInfo}
        <div class="space-y-0.5">
          <p>Format: <span class="font-semibold">{debugState.readerInfo.format ?? "—"}</span></p>
          <p>TOC: <span class="font-semibold">{String(debugState.readerInfo.isTocOpen)}</span></p>
          <p>Search: <span class="font-semibold">{String(debugState.readerInfo.isSearchOpen)}</span></p>
          <p>Fullscreen: <span class="font-semibold">{String(debugState.readerInfo.isFullscreen)}</span></p>
          <p>Page: <span class="font-semibold">{debugState.readerInfo.pageInfo}</span></p>
          <p>Scale: <span class="font-semibold">{debugState.readerInfo.scale}</span></p>
        </div>
      {:else}
        <p class="text-[var(--color-text-muted)]">No active reader</p>
      {/if}
    </div>

    <!-- Selection Inspector -->
    <div class="p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Selection Inspector</h4>
      {#if debugState.selection}
        <div class="space-y-0.5">
          <p>Source: <span class="font-semibold">{debugState.selection.source}</span></p>
          <p>Rects: <span class="font-semibold">{debugState.selection.rectCount}</span></p>
          <p class="truncate" title={debugState.selection.text}>
            Text: <span class="font-semibold">{debugState.selection.text.slice(0, 120)}{debugState.selection.text.length > 120 ? "…" : ""}</span>
          </p>
        </div>
      {:else}
        <p class="text-[var(--color-text-muted)]">No selection</p>
      {/if}
    </div>
  </div>
{/if}
