<script lang="ts">
  import type { Snippet } from "svelte";
  import HomeTopNav from "./HomeTopNav.svelte";
  import HomeSidebar from "./HomeSidebar.svelte";
  import HomeMainContent from "./HomeMainContent.svelte";
  import type { ReadingStatsSummaryDto } from "../../types";
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
  }: Props = $props();
</script>

<div class="space-y-4">
  <HomeTopNav
    {t}
    {activeRoute}
    {onNavigateHome}
    {onNavigateHighlights}
    {onNavigateSettings}
    actions={navbarActions}
  />

  <div class="grid gap-4 lg:grid-cols-[300px_1fr]">
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

    <HomeMainContent {t} {continueSection} {shelfSection} />
  </div>
</div>
