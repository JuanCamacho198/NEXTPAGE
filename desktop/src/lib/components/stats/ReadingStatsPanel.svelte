<script lang="ts">
  import type { ReadingStatsSummaryDto } from "../../types";

  type Props = {
    stats: ReadingStatsSummaryDto | null;
    isLoading?: boolean;
    disabledReason?: string | null;
    selectedBookTitle?: string | null;
    onRefresh?: () => void;
  };

  let {
    stats,
    isLoading = false,
    disabledReason = null,
    selectedBookTitle = null,
    onRefresh,
  }: Props = $props();

  const pct = (value: number) => `${Math.round(value)}%`;
</script>

<section class="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
  <div class="mb-3 flex items-center justify-between">
    <h2 class="text-lg font-semibold text-slate-800">Reading Stats</h2>
    <button
      type="button"
      class="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
      onclick={() => onRefresh?.()}
      disabled={Boolean(disabledReason) || isLoading}
    >
      Refresh
    </button>
  </div>

  {#if selectedBookTitle}
    <p class="mb-3 text-xs text-slate-500">Scope: {selectedBookTitle}</p>
  {:else}
    <p class="mb-3 text-xs text-slate-500">Scope: Global</p>
  {/if}

  {#if disabledReason}
    <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      {disabledReason}
    </div>
  {:else if isLoading}
    <p class="text-sm text-slate-500">Loading stats...</p>
  {:else if !stats}
    <p class="text-sm text-slate-500">Stats unavailable.</p>
  {:else}
    <div class="grid grid-cols-2 gap-2">
      <div class="rounded-lg bg-slate-50 p-2">
        <p class="text-[11px] uppercase tracking-wide text-slate-500">Minutes</p>
        <p class="text-base font-semibold text-slate-800">{stats.totalMinutesRead}</p>
      </div>
      <div class="rounded-lg bg-slate-50 p-2">
        <p class="text-[11px] uppercase tracking-wide text-slate-500">Sessions</p>
        <p class="text-base font-semibold text-slate-800">{stats.totalSessions}</p>
      </div>
      <div class="rounded-lg bg-slate-50 p-2">
        <p class="text-[11px] uppercase tracking-wide text-slate-500">Started</p>
        <p class="text-base font-semibold text-slate-800">{stats.booksStarted}</p>
      </div>
      <div class="rounded-lg bg-slate-50 p-2">
        <p class="text-[11px] uppercase tracking-wide text-slate-500">Completed</p>
        <p class="text-base font-semibold text-slate-800">{stats.booksCompleted}</p>
      </div>
    </div>
    <p class="mt-3 text-sm text-slate-600">Average progress: <span class="font-semibold">{pct(stats.avgProgressPercentage)}</span></p>
  {/if}
</section>
