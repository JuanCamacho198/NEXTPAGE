<script lang="ts">
  import { debugState } from "./debugState.svelte";
  import { getLogs, diagnose } from "$lib/api/tauriClient";
  import { epubCache } from "$lib/features/reader/epub/epubCache";
  import { documentCache } from "$lib/features/reader/pdf/pdfStreaming";
  import { metricsStore } from "$lib/logger/MetricsStore";
  import type { DiagnoseResult } from "$lib/types";

  let logsLoading = $state(false);
  let diagnoseResult = $state<DiagnoseResult | null>(null);
  let diagnoseLoading = $state(false);

  const handleExportLogs = async () => {
    logsLoading = true;
    try {
      const lines = await getLogs();
      const blob = new Blob([lines.join("\n")], { type: "application/jsonl" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `nextpage-logs-${new Date().toISOString().slice(0, 10)}.jsonl`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error("Failed to export logs:", e);
    } finally {
      logsLoading = false;
    }
  };

  const handleDiagnose = async () => {
    diagnoseLoading = true;
    try {
      diagnoseResult = await diagnose();
    } catch (e) {
      console.error("Diagnose failed:", e);
    } finally {
      diagnoseLoading = false;
    }
  };
</script>

{#if debugState.enabled}
  <div
    class="fixed top-4 right-4 z-[9999] w-96 max-h-[85vh] overflow-y-auto rounded-lg border bg-[var(--color-surface)] text-xs font-mono text-[var(--color-primary)] shadow-xl"
    role="region"
    aria-label="Debug information"
  >
    <!-- Route -->
    <div class="border-b border-[var(--color-border)] p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Route</h4>
      <p class="text-sm">{debugState.currentRoute || "Unknown Route"}</p>
    </div>

    <!-- Session Info -->
    <div class="border-b border-[var(--color-border)] p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Session</h4>
      <p class="truncate" title={metricsStore.getSessionId()}>ID: <span class="font-semibold">{metricsStore.getSessionId().slice(0, 8)}…</span></p>
      <p>Metrics: <span class="font-semibold">{metricsStore.getAll().length}</span></p>
    </div>

    <!-- Cache State -->
    <div class="border-b border-[var(--color-border)] p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Cache State</h4>
      <p>EPUB cache: <span class="font-semibold">{epubCache.size} books</span></p>
      <p>PDF cache: <span class="font-semibold">{documentCache.size} docs</span></p>
    </div>

    <!-- Diagnose -->
    <div class="border-b border-[var(--color-border)] p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Health Diagnose</h4>
      {#if diagnoseResult}
        <div class="space-y-0.5">
          <p>DB: <span class="font-semibold" class:status-okay={diagnoseResult.database === "healthy"} class:status-warn={diagnoseResult.database !== "healthy"}>{diagnoseResult.database}</span></p>
          <p>Queue: <span class="font-semibold">{diagnoseResult.queue}</span></p>
          <p>FS: <span class="font-semibold">{diagnoseResult.filesystem}</span></p>
          <p>Log: <span class="font-semibold">{diagnoseResult.logFile}</span></p>
          {#if Object.keys(diagnoseResult.details).length > 0}
            <details class="mt-1">
              <summary class="cursor-pointer text-[var(--color-text-muted)]">Details</summary>
              <pre class="mt-1 whitespace-pre-wrap break-all text-[10px]">{JSON.stringify(diagnoseResult.details, null, 2)}</pre>
            </details>
          {/if}
        </div>
      {/if}
      <button
        class="mt-1 rounded px-2 py-0.5 text-[10px] font-medium bg-[var(--color-primary)] text-white hover:opacity-80 disabled:opacity-50"
        onclick={handleDiagnose}
        disabled={diagnoseLoading}
      >
        {diagnoseLoading ? "Running…" : "Run Diagnose"}
      </button>
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
    <div class="border-b border-[var(--color-border)] p-3">
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

    <!-- Export Logs -->
    <div class="p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-[var(--color-text-muted)]">Logs</h4>
      <button
        class="rounded px-2 py-0.5 text-[10px] font-medium bg-[var(--color-primary)] text-white hover:opacity-80 disabled:opacity-50"
        onclick={handleExportLogs}
        disabled={logsLoading}
      >
        {logsLoading ? "Exporting…" : "Export Logs"}
      </button>
    </div>
  </div>
{/if}

<style>
  .status-okay {
    color: #22c55e;
  }
  .status-warn {
    color: #eab308;
  }
</style>
