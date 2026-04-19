<script lang="ts">
  import ReadingStatsPanel from "../stats/ReadingStatsPanel.svelte";
  import type { ReadingStatsSummaryDto } from "../../types";
  import type { MessageKey } from "../../i18n";

  type Props = {
    stats: ReadingStatsSummaryDto | null;
    isLoadingStats?: boolean;
    statsUnavailableReason?: string | null;
    selectedBookTitle?: string | null;
    onRefreshStats?: () => void;
    onNavigateHighlights?: () => void;
    onNavigateSettings?: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    stats,
    isLoadingStats = false,
    statsUnavailableReason = null,
    selectedBookTitle = null,
    onRefreshStats,
    onNavigateHighlights,
    onNavigateSettings,
    t,
  }: Props = $props();
</script>

<aside class="space-y-4">
  <ReadingStatsPanel
    {stats}
    isLoading={isLoadingStats}
    disabledReason={statsUnavailableReason}
    {selectedBookTitle}
    onRefresh={onRefreshStats}
    {t}
  />

  <section class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
    <div class="flex items-start justify-between gap-2">
      <div>
        <h3 class="text-sm font-semibold uppercase tracking-wide text-[var(--color-primary)]">{t("home.highlightsTitle")}</h3>
        <p class="mt-2 text-sm text-[var(--color-text-muted)]">{t("home.highlightsPlaceholder")}</p>
      </div>
      <button
        type="button"
        class="rounded-md border border-[color:var(--color-border)] px-2 py-1 text-xs text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
        onclick={onNavigateHighlights}
      >
        {t("home.highlightsTitle")}
      </button>
    </div>
  </section>

  <section class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
    <h3 class="text-sm font-semibold uppercase tracking-wide text-[var(--color-primary)]">{t("home.futureTitle")}</h3>
    <p class="mt-2 text-sm text-[var(--color-text-muted)]">{t("home.futurePlaceholder")}</p>
    <div class="mt-3">
      <button
        type="button"
        class="rounded-md border border-[color:var(--color-border)] px-2 py-1 text-xs text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
        onclick={onNavigateSettings}
      >
        {t("app.settings")}
      </button>
    </div>
  </section>
</aside>
