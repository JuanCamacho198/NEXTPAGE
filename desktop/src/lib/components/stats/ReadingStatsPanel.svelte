<script lang="ts">
  import type { ReadingStatsSummaryDto } from "$lib/types";
  import type { MessageKey } from "../../i18n";
  import Panel from "../ui/Panel.svelte";
  import Button from "../ui/Button.svelte";

  type Props = {
    stats: ReadingStatsSummaryDto | null;
    isLoading?: boolean;
    disabledReason?: string | null;
    selectedBookTitle?: string | null;
    onRefresh?: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    stats,
    isLoading = false,
    disabledReason = null,
    selectedBookTitle = null,
    onRefresh,
    t,
  }: Props = $props();

  const pct = (value: number) => `${Math.round(value)}%`;
</script>

<Panel title={t("stats.title")}>
  {#snippet children()}
    {#if selectedBookTitle}
      <p class="mb-3 text-xs text-[var(--color-text-muted)]">{t("stats.scope")}: {selectedBookTitle}</p>
    {:else}
      <p class="mb-3 text-xs text-[var(--color-text-muted)]">{t("stats.scope")}: {t("stats.global")}</p>
    {/if}

    {#if disabledReason}
      <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
        {disabledReason}
      </div>
    {:else if isLoading}
      <p class="text-sm text-[var(--color-text-muted)]">{t("stats.loading")}</p>
    {:else if !stats}
      <p class="text-sm text-[var(--color-text-muted)]">{t("stats.unavailable")}</p>
    {:else}
      <div class="grid grid-cols-2 gap-2">
        <div class="rounded-lg bg-[var(--color-background)] p-2">
          <p class="text-[11px] uppercase tracking-wide text-[var(--color-text-muted)]">{t("stats.minutes")}</p>
          <p class="text-base font-semibold text-[var(--color-primary)]">{stats.totalMinutesRead}</p>
        </div>
        <div class="rounded-lg bg-[var(--color-background)] p-2">
          <p class="text-[11px] uppercase tracking-wide text-[var(--color-text-muted)]">{t("stats.sessions")}</p>
          <p class="text-base font-semibold text-[var(--color-primary)]">{stats.totalSessions}</p>
        </div>
        <div class="rounded-lg bg-[var(--color-background)] p-2">
          <p class="text-[11px] uppercase tracking-wide text-[var(--color-text-muted)]">{t("stats.started")}</p>
          <p class="text-base font-semibold text-[var(--color-primary)]">{stats.booksStarted}</p>
        </div>
        <div class="rounded-lg bg-[var(--color-background)] p-2">
          <p class="text-[11px] uppercase tracking-wide text-[var(--color-text-muted)]">{t("stats.completed")}</p>
          <p class="text-base font-semibold text-[var(--color-primary)]">{stats.booksCompleted}</p>
        </div>
      </div>
      <p class="mt-3 text-sm text-[var(--color-secondary)]">{t("stats.averageProgress")}: <span class="font-semibold text-[var(--color-primary)]">{pct(stats.avgProgressPercentage)}</span></p>
    {/if}
  {/snippet}

  {#snippet actions()}
    <Button size="sm" variant="ghost" onclick={() => onRefresh?.()} disabled={Boolean(disabledReason) || isLoading}>
      {t("stats.refresh")}
    </Button>
  {/snippet}
</Panel>
