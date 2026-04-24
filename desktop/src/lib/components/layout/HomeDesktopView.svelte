<script lang="ts">
  import type { Snippet } from "svelte";
  import HomeTopNav from "./HomeTopNav.svelte";
  import HomeSidebar from "./HomeSidebar.svelte";
  import HomeMainContent from "./HomeMainContent.svelte";
  import type { ReadingStatsSummaryDto } from "$lib/types";
  import type { MessageKey } from "../../i18n";

  type Props = {
    stats: ReadingStatsSummaryDto | null;
    isLoadingStats?: boolean;
    statsUnavailableReason?: string | null;
    selectedBookTitle?: string | null;
    onRefreshStats?: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    activeRoute?: "home" | "highlights" | "settings";
    onNavigateHome?: () => void;
    onNavigateHighlights?: () => void;
    onNavigateSettings?: () => void;
    navbarActions?: Snippet;
    continueSection?: Snippet;
    shelfSection?: Snippet;
    continueCount?: number;
    shelfCount?: number;
    statsMinutes?: number;
  };

  let {
    stats,
    isLoadingStats = false,
    statsUnavailableReason = null,
    selectedBookTitle = null,
    onRefreshStats,
    t,
    activeRoute = "home",
    onNavigateHome,
    onNavigateHighlights,
    onNavigateSettings,
    navbarActions,
    continueSection,
    shelfSection,
    continueCount = 0,
    shelfCount = 0,
    statsMinutes = 0,
  }: Props = $props();
</script>

<div class="space-y-6">
  <HomeTopNav
    {t}
    {activeRoute}
    {onNavigateHome}
    {onNavigateHighlights}
    {onNavigateSettings}
    actions={navbarActions}
  />

  <div class="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
    <HomeSidebar
      {stats}
      {t}
      isLoadingStats={isLoadingStats}
      statsUnavailableReason={statsUnavailableReason}
      selectedBookTitle={selectedBookTitle}
      onRefreshStats={onRefreshStats}
      {onNavigateHighlights}
      {onNavigateSettings}
    />

    <HomeMainContent
      {t}
      {continueSection}
      {shelfSection}
      {selectedBookTitle}
      {continueCount}
      {shelfCount}
      {statsMinutes}
    />
  </div>
</div>
