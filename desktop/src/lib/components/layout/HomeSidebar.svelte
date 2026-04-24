<script lang="ts">
  import ReadingStatsPanel from "../stats/ReadingStatsPanel.svelte";
  import type { ReadingStatsSummaryDto } from "$lib/types";
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

<aside class="space-y-4 xl:sticky xl:top-6 xl:self-start">
  <ReadingStatsPanel
    {stats}
    isLoading={isLoadingStats}
    disabledReason={statsUnavailableReason}
    {selectedBookTitle}
    onRefresh={onRefreshStats}
    {t}
  />

  <section class="rounded-[24px] border border-[color:var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-soft)] backdrop-blur-xl">
    <div class="flex items-start justify-between gap-3">
      <div>
        <p class="text-[11px] font-semibold uppercase tracking-[0.24em] text-[var(--color-text-muted)]">{t("home.heroHighlightsLabel")}</p>
        <h3 class="mt-2 text-lg font-semibold tracking-tight text-[var(--color-primary)]">{t("home.highlightsTitle")}</h3>
        <p class="mt-2 text-sm leading-6 text-[var(--color-secondary)]">{t("home.highlightsPlaceholder")}</p>
      </div>
      <button
        type="button"
        class="rounded-full border border-[color:var(--color-border)] px-3 py-1.5 text-xs font-medium text-[var(--color-primary)] transition-colors hover:border-[color:var(--color-border-strong)] hover:bg-[var(--color-panel-accent)]"
        onclick={onNavigateHighlights}
      >
        {t("home.highlightsTitle")}
      </button>
    </div>
  </section>

  <section class="rounded-[24px] border border-[color:var(--color-border)] bg-[linear-gradient(180deg,rgba(18,30,47,0.92),rgba(12,20,32,0.84))] p-5 shadow-[var(--shadow-soft)]">
    <p class="text-[11px] font-semibold uppercase tracking-[0.24em] text-[var(--color-text-muted)]">{t("home.futureTitle")}</p>
    <h3 class="mt-2 text-lg font-semibold tracking-tight text-[var(--color-primary)]">{t("home.heroSettingsTitle")}</h3>
    <p class="mt-2 text-sm leading-6 text-[var(--color-secondary)]">{t("home.futurePlaceholder")}</p>
    <div class="mt-4 grid gap-2 text-xs text-[var(--color-text-muted)]">
      <div class="rounded-2xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-3 py-2">
        {t("home.heroSettingsHint")}
      </div>
      <div class="rounded-2xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-3 py-2">
        {selectedBookTitle ? `${t("stats.scope")}: ${selectedBookTitle}` : `${t("stats.scope")}: ${t("stats.global")}`}
      </div>
    </div>
    <div class="mt-4">
      <button
        type="button"
        class="w-full rounded-full border border-[color:var(--color-border-strong)] bg-[var(--color-accent-soft)] px-3 py-2 text-sm font-medium text-[var(--color-primary)] transition-colors hover:bg-[rgba(73,212,255,0.24)]"
        onclick={onNavigateSettings}
      >
        {t("app.settings")}
      </button>
    </div>
  </section>
</aside>
