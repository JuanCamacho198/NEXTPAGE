<script lang="ts">
  import { debugState } from "./debugState.svelte";
  import { getLogs, diagnose } from "$lib/shared/api/tauriClient";
  import { epubCache } from "$lib/features/reader/viewer-epub/epubCache";
  import { documentCache } from "$lib/features/reader/viewer-pdf/pdfStreaming";
  import { metricsStore } from "$lib/shared/logger/MetricsStore";
  import type { DiagnoseResult } from "$lib/types";

  let logsLoading = $state(false);
  let diagnoseResult = $state<DiagnoseResult | null>(null);
  let diagnoseLoading = $state(false);

  // ── IPC Performance auto-refresh ──
  const CHART_W = 340;
  let refreshTick = $state(0);

  $effect(() => {
    const interval = setInterval(() => {
      refreshTick++;
    }, 2000);
    return () => clearInterval(interval);
  });

  const ipcCalls = $derived.by(() => {
    void refreshTick; // force re-evaluation every 2s
    return metricsStore.getByName("ipc_call");
  });

  const ipcSummary = $derived.by(() => {
    const totalCalls = ipcCalls.length;
    const withDuration = ipcCalls.filter((c) => c.durationMs != null);
    const successCount = ipcCalls.filter((c) => c.success).length;
    const totalDuration = withDuration.reduce((sum, c) => sum + (c.durationMs ?? 0), 0);
    const avgDuration = withDuration.length > 0 ? Math.round(totalDuration / withDuration.length) : 0;
    const maxDuration = withDuration.length > 0 ? Math.round(Math.max(...withDuration.map((c) => c.durationMs ?? 0))) : 0;
    const successRate = totalCalls > 0 ? Math.round((successCount / totalCalls) * 100) : 100;
    return { totalCalls, avgDuration, maxDuration, successRate };
  });

  const recentCalls = $derived(ipcCalls.slice(-25).reverse());

  const ipcCommands = $derived.by(() => {
    type AccumEntry = {
      feature: string;
      count: number;
      durations: number[];
    };

    const grouped = new Map<string, AccumEntry>();
    for (const call of ipcCalls) {
      const key = call.feature ?? "unknown";
      let existing = grouped.get(key);
      if (!existing) {
        existing = { feature: key, count: 0, durations: [] };
        grouped.set(key, existing);
      }
      existing.count++;
      if (call.durationMs != null) {
        existing.durations.push(call.durationMs);
      }
    }

    type Row = {
      feature: string;
      count: number;
      avgDuration: number;
      minDuration: number;
      p50Duration: number;
      maxDuration: number;
      successRate: number;
    };

    const result: Row[] = [];
    for (const entry of grouped.values()) {
      const sorted = [...entry.durations].sort((a, b) => a - b);
      const minDuration = sorted.length > 0 ? Math.round(sorted[0]) : 0;
      const maxDuration = sorted.length > 0 ? Math.round(sorted[sorted.length - 1]) : 0;
      const avgDuration = sorted.length > 0 ? Math.round(sorted.reduce((a, b) => a + b, 0) / sorted.length) : 0;
      const p50Index = Math.floor(sorted.length / 2);
      const p50Duration = sorted.length > 0 ? Math.round(sorted[p50Index]) : 0;

      const successCount = ipcCalls.filter((c) => c.feature === entry.feature && c.success).length;
      const successRate = entry.count > 0 ? Math.round((successCount / entry.count) * 100) : 100;

      result.push({
        feature: entry.feature,
        count: entry.count,
        avgDuration,
        minDuration,
        p50Duration,
        maxDuration,
        successRate,
      });
    }

    result.sort((a, b) => b.count - a.count);
    return result;
  });



  const handleExportLogs = async (): Promise<void> => {
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

  const handleDiagnose = async (): Promise<void> => {
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
    class="fixed top-4 right-4 z-9999 w-96 max-h-[85vh] overflow-y-auto rounded-lg border bg-(--color-surface) text-xs font-mono text-(--color-primary) shadow-xl"
    role="region"
    aria-label="Debug information"
  >
    <!-- Route + Viewport -->
    <div class="border-b border-(--color-border) p-3">
      <div class="flex items-start justify-between gap-2">
        <div class="min-w-0 flex-1">
          <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">Route</h4>
          <p class="text-sm">{debugState.currentRoute || "Unknown Route"}</p>
        </div>
        <button
          type="button"
          class="shrink-0 rounded px-2 py-0.5 text-[10px] font-medium bg-(--color-primary) text-white hover:opacity-80"
          onclick={() => void debugState.copySnapshot()}
          title="Copy debug state to clipboard"
        >
          Copy State
        </button>
      </div>
      <div class="mt-2 flex items-baseline gap-3">
        <p class="text-[10px] text-(--color-text-muted)">
          <span class="font-semibold text-(--color-primary)">{debugState.viewportWidth}</span> × <span class="font-semibold text-(--color-primary)">{debugState.viewportHeight}</span>
        </p>
        <span class="rounded bg-(--color-border) px-1.5 py-0.5 text-[9px] font-mono font-semibold text-(--color-text-muted)">
          {debugState.breakpoint}
        </span>
      </div>
    </div>

    <!-- IPC Performance -->
    <div class="border-b border-(--color-border) p-3">
      <div class="flex items-center justify-between">
        <h4 class="text-[10px] uppercase tracking-wider text-(--color-text-muted)">IPC Performance</h4>
        <span class="text-[9px] text-(--color-text-muted)">refreshing…</span>
      </div>

      {#if ipcCalls.length === 0}
        <p class="mt-1 text-(--color-text-muted)">No IPC calls yet</p>
      {:else}
        <!-- Summary cards -->
        <div class="mt-2 grid grid-cols-4 gap-1.5">
          <div class="rounded bg-[rgba(255,255,255,0.04)] p-1.5 text-center">
            <p class="text-[9px] text-(--color-text-muted)">Calls</p>
            <p class="text-sm font-bold">{ipcSummary.totalCalls}</p>
          </div>
          <div class="rounded bg-[rgba(255,255,255,0.04)] p-1.5 text-center">
            <p class="text-[9px] text-(--color-text-muted)">Avg</p>
            <p class="text-sm font-bold">{ipcSummary.avgDuration}<span class="text-[9px] font-normal">ms</span></p>
          </div>
          <div class="rounded bg-[rgba(255,255,255,0.04)] p-1.5 text-center">
            <p class="text-[9px] text-(--color-text-muted)">Max</p>
            <p class="text-sm font-bold">{ipcSummary.maxDuration}<span class="text-[9px] font-normal">ms</span></p>
          </div>
          <div class="rounded bg-[rgba(255,255,255,0.04)] p-1.5 text-center">
            <p class="text-[9px] text-(--color-text-muted)">Ok</p>
            <p class="text-sm font-bold" class:text-green-500={ipcSummary.successRate >= 95} class:text-yellow-500={ipcSummary.successRate < 95}>
              {ipcSummary.successRate}%
            </p>
          </div>
        </div>

        <!-- Recent calls bar chart -->
        {#if recentCalls.length > 1}
          <div class="mt-2">
            <p class="mb-1 text-[9px] text-(--color-text-muted)">Recent calls (last {recentCalls.length})</p>
            <div class="rounded bg-[rgba(255,255,255,0.03)] p-2">
              <svg viewBox={`0 0 ${CHART_W} 68`} class="h-17 w-full">
                {#each recentCalls as call, i}
                  {@const barW = (CHART_W - (recentCalls.length - 1) * 2) / recentCalls.length}
                  {@const barH = call.durationMs != null ? Math.max(2, (call.durationMs / ipcSummary.maxDuration) * 56) : 2}
                  {@const x = i * (barW + 2)}
                  {@const y = 64 - barH}
                  <rect
                    {x} {y}
                    width={Math.max(3, barW)}
                    height={barH}
                    rx="1.5"
                    fill={call.success ? "#3388ff" : "#ef4444"}
                    opacity={call.success ? 0.7 : 0.9}
                  >
                    <title>{call.feature}: {call.durationMs}ms {call.success ? "✓" : "✗"}</title>
                  </rect>
                {/each}
              </svg>
            </div>
          </div>
        {/if}

        <!-- Per-command breakdown -->
        <div class="mt-2">
          <p class="mb-1 text-[9px] text-(--color-text-muted)">By command</p>
          <div class="space-y-1">
            {#each ipcCommands as cmd}
              {@const maxCount = Math.max(...ipcCommands.map((c) => c.count), 1)}
              <div class="rounded bg-[rgba(255,255,255,0.03)] p-1.5">
                <div class="flex items-center justify-between gap-1">
                  <span class="truncate text-[10px]" title={cmd.feature}>{cmd.feature}</span>
                  <span class="shrink-0 text-[10px] font-semibold">{cmd.count}x</span>
                </div>
                <div class="mt-0.5 flex items-center gap-2">
                  <!-- Mini bar -->
                  <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                    <div
                      class="h-full rounded-full"
                      class:bg-[#22c55e]={cmd.successRate >= 95}
                      class:bg-[#eab308]={cmd.successRate >= 80 && cmd.successRate < 95}
                      class:bg-[#ef4444]={cmd.successRate < 80}
                      style={`width: ${(cmd.count / Math.max(maxCount, 1)) * 100}%`}
                    ></div>
                  </div>
                  <span class="shrink-0 text-[9px] text-(--color-text-muted)">
                    {cmd.avgDuration}ms
                  </span>
                </div>
                <div class="mt-0.5 flex gap-2 text-[9px] text-(--color-text-muted)">
                  <span>min {cmd.minDuration}ms</span>
                  <span>p50 {cmd.p50Duration}ms</span>
                  <span>max {cmd.maxDuration}ms</span>
                  <span class:text-green-500={cmd.successRate >= 95} class:text-yellow-500={cmd.successRate < 95}>
                    {cmd.successRate}% ok
                  </span>
                </div>
              </div>
            {/each}
          </div>
        </div>
      {/if}
    </div>

    <!-- Session Info -->
    <div class="border-b border-(--color-border) p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">Session</h4>
      <p class="truncate" title={metricsStore.getSessionId()}>ID: <span class="font-semibold">{metricsStore.getSessionId().slice(0, 8)}…</span></p>
      <p>Metrics: <span class="font-semibold">{metricsStore.getAll().length}</span></p>
    </div>

    <!-- Cache State -->
    <div class="border-b border-(--color-border) p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">Cache State</h4>
      <p>EPUB cache: <span class="font-semibold">{epubCache.size} books</span></p>
      <p>PDF cache: <span class="font-semibold">{documentCache.size} docs</span></p>
    </div>

    <!-- Diagnose -->
    <div class="border-b border-(--color-border) p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">Health Diagnose</h4>
      {#if diagnoseResult}
        <div class="space-y-0.5">
          <p>DB: <span class="font-semibold" class:text-green-500={diagnoseResult.database === "healthy"} class:text-yellow-500={diagnoseResult.database !== "healthy"}>{diagnoseResult.database}</span></p>
          <p>Queue: <span class="font-semibold">{diagnoseResult.queue}</span></p>
          <p>FS: <span class="font-semibold">{diagnoseResult.filesystem}</span></p>
          <p>Log: <span class="font-semibold">{diagnoseResult.logFile}</span></p>
          {#if Object.keys(diagnoseResult.details).length > 0}
            <details class="mt-1">
              <summary class="cursor-pointer text-(--color-text-muted)">Details</summary>
              <pre class="mt-1 whitespace-pre-wrap break-all text-[10px]">{JSON.stringify(diagnoseResult.details, null, 2)}</pre>
            </details>
          {/if}
        </div>
      {/if}
      <button
        class="mt-1 rounded px-2 py-0.5 text-[10px] font-medium bg-(--color-primary) text-white hover:opacity-80 disabled:opacity-50"
        onclick={handleDiagnose}
        disabled={diagnoseLoading}
      >
        {diagnoseLoading ? "Running…" : "Run Diagnose"}
      </button>
    </div>

    <!-- Reader Info -->
    <div class="border-b border-(--color-border) p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">Reader Info</h4>
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
        <p class="text-(--color-text-muted)">No active reader</p>
      {/if}
    </div>

    <!-- Selection Inspector -->
    <div class="border-b border-(--color-border) p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">Selection Inspector</h4>
      {#if debugState.selection}
        <div class="space-y-0.5">
          <p>Source: <span class="font-semibold">{debugState.selection.source}</span></p>
          <p>Rects: <span class="font-semibold">{debugState.selection.rectCount}</span></p>
          <p class="truncate" title={debugState.selection.text}>
            Text: <span class="font-semibold">{debugState.selection.text.slice(0, 120)}{debugState.selection.text.length > 120 ? "…" : ""}</span>
          </p>
        </div>
      {:else}
        <p class="text-(--color-text-muted)">No selection</p>
      {/if}
    </div>

    <!-- EPUB Selection Debug -->
    <div class="border-b border-(--color-border) p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">EPUB Selection Debug</h4>
      <div class="space-y-0.5">
        <p>Iframe: <span class="font-semibold">
          {debugState.epub.iframeRect
            ? `${Math.round(debugState.epub.iframeRect.left)},${Math.round(debugState.epub.iframeRect.top)} (${Math.round(debugState.epub.iframeRect.width)}×${Math.round(debugState.epub.iframeRect.height)})`
            : "—"}
        </span></p>
        <p>PostMessage RX: <span class="font-semibold">{debugState.epub.postMessageCount}</span> (empty-text: <span class="font-semibold">{debugState.epub.emptyTextMessageCount}</span>)</p>
        <p>Guard: <span class="font-semibold" class:text-green-500={debugState.epub.guardResult === "pass"} class:text-yellow-500={debugState.epub.guardResult === "none"} class:text-red-500={debugState.epub.guardResult.startsWith("drop-")}>{debugState.epub.guardResult}</span></p>
        <p>onselection: <span class="font-semibold">{debugState.epub.onselectionCalled}</span> | onselectionclear: <span class="font-semibold">{debugState.epub.onselectionclearCalled}</span></p>
        <p>rectCount: <span class="font-semibold">{debugState.epub.rectCount}</span> | currentChapter: <span class="font-semibold">{debugState.epub.currentChapterIndex ?? "—"}</span></p>
        {#if debugState.epub.lastRawMessage}
          <details class="mt-1">
            <summary class="cursor-pointer text-(--color-text-muted)">Last msg ({debugState.epub.lastRawMessage.type})</summary>
            <pre class="mt-1 whitespace-pre-wrap break-all text-[10px]">{JSON.stringify(debugState.epub.lastRawMessage, null, 2)}</pre>
          </details>
        {/if}
        <hr class="my-1 border-(--color-border)" />
        <p>showToolbar: <span class="font-semibold" class:text-green-500={debugState.epub.parentState.showToolbar} class:text-red-500={!debugState.epub.parentState.showToolbar}>{String(debugState.epub.parentState.showToolbar)}</span></p>
        <p>selectedText: <span class="font-semibold">{debugState.epub.parentState.selectedText || "—"}</span></p>
        <p>bounds: <span class="font-semibold">{debugState.epub.parentState.selectionBounds ? `${Math.round(debugState.epub.parentState.selectionBounds.left)},${Math.round(debugState.epub.parentState.selectionBounds.top)} → ${Math.round(debugState.epub.parentState.selectionBounds.right)},${Math.round(debugState.epub.parentState.selectionBounds.bottom)}` : "—"}</span></p>
        <p>container: <span class="font-semibold">{debugState.epub.parentState.selectionContainer ? `${Math.round(debugState.epub.parentState.selectionContainer.left)},${Math.round(debugState.epub.parentState.selectionContainer.top)}` : "—"}</span></p>
        <p>toolbarX/Y: <span class="font-semibold text-cyan-600">
          {debugState.epub.computedToolbarX !== null && debugState.epub.computedToolbarY !== null
            ? `${Math.round(debugState.epub.computedToolbarX)}, ${Math.round(debugState.epub.computedToolbarY)}`
            : "—"}
        </span></p>
        <hr class="my-1 border-(--color-border)" />
        <p class="text-(--color-text-muted)">dismiss: <span class="font-semibold">{debugState.epub.dismissToolbarCallCount}</span> ({debugState.epub.lastDismissTrigger})</p>
        <p class="text-(--color-text-muted)">color pick: <span class="font-semibold">{debugState.epub.colorPickCount}</span> ({debugState.epub.lastPickedColor})</p>
        <p class="text-(--color-text-muted)">saveHighlight: <span class="font-semibold">{debugState.epub.saveHighlightCallCount}</span>{debugState.epub.saveHighlightLastError ? ` err="${debugState.epub.saveHighlightLastError.slice(0, 30)}"` : ""}</p>
        <p class="text-(--color-text-muted)">persistedHighlights: <span class="font-semibold">{debugState.epub.persistedHighlightsCount}</span></p>
      </div>
    </div>

    <!-- Export Logs -->
    <div class="p-3">
      <h4 class="mb-1 text-[10px] uppercase tracking-wider text-(--color-text-muted)">Logs</h4>
      <button
        class="rounded px-2 py-0.5 text-[10px] font-medium bg-(--color-primary) text-white hover:opacity-80 disabled:opacity-50"
        onclick={handleExportLogs}
        disabled={logsLoading}
      >
        {logsLoading ? "Exporting…" : "Export Logs"}
      </button>
    </div>
  </div>
{/if}
