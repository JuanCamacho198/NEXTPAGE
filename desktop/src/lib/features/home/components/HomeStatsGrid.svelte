<script lang="ts">
  import type { ReadingStatsSummaryDto } from '$lib/shared/types';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { IconName } from '$lib/shared/ui/navigation/Icon.svelte';
  import MetricCard from './MetricCard.svelte';
  import { statsState } from '$lib/shared/stores/StatsDomainState.svelte';
  import { settingsState } from '$lib/shared/stores/SettingsDomainState.svelte';

  type Props = {
    stats: ReadingStatsSummaryDto | null;
    isLoading?: boolean;
    disabledReason?: string | null;
    streakDays?: number;
    isLoadingStreak?: boolean;
    t?: (key: MessageKey, params?: Record<string, string | number>) => string;
    todayMinutes?: number | null;
    dailyGoalMinutes?: number | null;
    goalProgress?: number | null;
  };

  let {
    stats,
    isLoading = false,
    disabledReason = null,
    streakDays = 0,
    isLoadingStreak = false,
    t: _t,
    todayMinutes: todayMinutesProp = null,
    dailyGoalMinutes: dailyGoalMinutesProp = null,
    goalProgress: goalProgressProp = null,
  }: Props = $props();

  const todayMinutes = $derived(
    todayMinutesProp !== null ? todayMinutesProp : statsState.todayMinutes,
  );
  const dailyGoalMinutes = $derived(
    dailyGoalMinutesProp !== null
      ? dailyGoalMinutesProp
      : (statsState.dailyGoalMinutes ?? settingsState.dailyGoalMinutes ?? 20),
  );
  const goalProgress = $derived(
    goalProgressProp !== null ? goalProgressProp : statsState.goalProgress,
  );
  const goalValue = $derived(
    _t
      ? _t('home.metrics.minutesFormat', { current: todayMinutes, total: dailyGoalMinutes })
      : `${todayMinutes}/${dailyGoalMinutes} min`,
  );

  type StatItem = {
    label: string;
    value: string;
    icon: IconName;
    progress?: number;
  };

  const statItems = $derived<StatItem[]>([
    {
      label: _t ? _t('stats.booksStartedLabel') : 'Iniciados',
      value: stats?.booksStarted?.toString() ?? '0',
      icon: 'book',
    },
    {
      label: _t ? _t('stats.booksCompletedLabel') : 'Completados',
      value: stats?.booksCompleted?.toString() ?? '0',
      icon: 'check',
    },
    {
      label: _t ? _t('home.metrics.dailyGoalLabel') : 'Meta diaria',
      value: goalValue,
      icon: 'clock',
      progress: goalProgress,
    },
    {
      label: _t ? _t('stats.sessionsLabel') : 'Sesiones',
      value: stats?.totalSessions?.toString() ?? '0',
      icon: 'trend-up',
    },
    {
      label: _t ? _t('stats.streakLabel') : 'Racha',
      value: isLoadingStreak
        ? '—'
        : _t
          ? _t('stats.days', { count: streakDays })
          : `${streakDays} ${streakDays === 1 ? 'día' : 'días'}`,
      icon: 'flame',
    },
  ]);
</script>

{#if disabledReason}
  <div class="rounded-xl border border-(--color-border) bg-(--color-surface) p-4 text-center">
    <p class="text-sm text-(--color-text-muted)">{disabledReason}</p>
  </div>
{:else}
  <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5" data-testid="stats-grid">
    {#each statItems as item}
      <MetricCard
        {isLoading}
        label={item.label}
        value={item.value}
        icon={item.icon}
        progress={item.progress}
      />
    {/each}
  </div>
{/if}
