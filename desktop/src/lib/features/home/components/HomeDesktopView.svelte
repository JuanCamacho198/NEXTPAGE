<script lang="ts">
  import { onMount } from 'svelte';
  import type { Snippet } from 'svelte';
  import HomeHero from './HomeHero.svelte';
  import HomeStatsGrid from './HomeStatsGrid.svelte';
  import HomeMainContent from './HomeMainContent.svelte';
  import type { ReadingStatsSummaryDto } from '$lib/types';
  import type { MessageKey } from '$lib/i18n';
  import { appState } from '$lib/shared/stores/AppState.svelte';

  type Props = {
    stats: ReadingStatsSummaryDto | null;
    isLoadingStats?: boolean;
    statsUnavailableReason?: string | null;
    streakDays?: number;
    isLoadingStreak?: boolean;
    selectedBookTitle?: string | null;
    onRefreshStats?: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    activeRoute?: 'home' | 'highlights' | 'settings';
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
    streakDays = 0,
    isLoadingStreak = false,
    t,
    navbarActions,
    continueSection,
    shelfSection,
  }: Props = $props();

  onMount(() => {
    void appState.loadStatsStreak();
  });
</script>

<div class="space-y-6">
  <HomeHero actions={navbarActions} {t} />

  <HomeStatsGrid {stats} isLoading={isLoadingStats} disabledReason={statsUnavailableReason} {streakDays} {isLoadingStreak} {t} />

  <HomeMainContent {t} {continueSection} {shelfSection} />
</div>
