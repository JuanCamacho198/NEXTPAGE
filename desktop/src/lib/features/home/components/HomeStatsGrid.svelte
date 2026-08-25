<script lang="ts">
  import type { ReadingStatsSummaryDto } from '$lib/shared/types';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { IconName } from '$lib/shared/ui/navigation/Icon.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
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
    dailyGoalMinutesProp !== null ? dailyGoalMinutesProp : (statsState.dailyGoalMinutes ?? settingsState.dailyGoalMinutes ?? 20),
  );
  const goalProgress = $derived(
    goalProgressProp !== null ? goalProgressProp : statsState.goalProgress,
  );
  const goalValue = $derived(`${todayMinutes}/${dailyGoalMinutes} min`);

  type StatItem = {
    label: string;
    value: string;
    icon: IconName;
    color: string;
    bg: string;
    progress?: number;
  };

  const statItems = $derived<StatItem[]>([
    {
      label: _t ? _t('stats.booksStartedLabel') : 'Iniciados',
      value: stats?.booksStarted?.toString() ?? '0',
      icon: 'book',
      color: 'var(--color-accent-blue)',
      bg: 'rgba(73, 212, 255, 0.1)',
    },
    {
      label: _t ? _t('stats.booksCompletedLabel') : 'Completados',
      value: stats?.booksCompleted?.toString() ?? '0',
      icon: 'check',
      color: 'var(--color-success)',
      bg: 'color-mix(in srgb, var(--color-success) 10%, transparent)',
    },
    {
      label: 'Meta diaria',
      value: goalValue,
      icon: 'clock',
      color: '#d8e2ff',
      bg: 'rgba(216,226,255,0.1)',
      progress: goalProgress,
    },
    {
      label: _t ? _t('stats.sessionsLabel') : 'Sesiones',
      value: stats?.totalSessions?.toString() ?? '0',
      icon: 'trend-up',
      color: 'var(--color-warning)',
      bg: 'color-mix(in srgb, var(--color-warning) 10%, transparent)',
    },
    {
      label: _t ? _t('stats.streakLabel') : 'Racha',
      value: isLoadingStreak
        ? '—'
        : _t
          ? _t('stats.days', { count: streakDays })
          : `${streakDays} ${streakDays === 1 ? 'día' : 'días'}`,
      icon: 'flame',
      color: streakDays === 0 ? 'var(--color-text-muted)' : '#ff6b35',
      bg: streakDays === 0 ? 'rgba(148,173,206,0.08)' : 'rgba(255,107,53,0.12)',
    },
  ]);
</script>

{#if disabledReason}
  <div class="rounded-xl border border-(--color-border) bg-(--color-surface) p-4 text-center">
    <p class="text-sm text-(--color-text-muted)">{disabledReason}</p>
  </div>
{:else}
  <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
    {#each statItems as item}
      <div
        class="group relative overflow-hidden rounded-[20px] border border-(--color-border) bg-(--color-surface) p-5 shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-(--color-border-strong) hover:shadow-(--shadow-soft)"
      >
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs font-medium uppercase tracking-wider text-(--color-text-muted)">
              {item.label}
            </p>
            {#if isLoading}
              <div class="mt-2 h-8 w-16 animate-pulse rounded bg-(--color-border)"></div>
            {:else}
              <p class="mt-1 text-3xl font-semibold tracking-tight text-(--color-primary)">
                {item.value}
              </p>
            {/if}
          </div>
          <div
            class="flex h-10 w-10 items-center justify-center rounded-full"
            style="background-color: {item.bg}; color: {item.color};"
          >
            <Icon name={item.icon} size="lg" />
          </div>
        </div>

        <!-- Goal progress bar (only for Meta diaria card) -->
        {#if item.progress !== undefined}
          <div class="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-[#161f33]/20">
            <div
              class="h-full rounded-full bg-[#d8e2ff] transition-all duration-500"
              style="width: {Math.round(Math.min(1, Math.max(0, item.progress)) * 100)}%"
            ></div>
          </div>
        {/if}

        <!-- Glow effect on hover -->
        <div
          class="absolute -bottom-8 -right-8 h-24 w-24 rounded-full opacity-0 blur-[30px] transition-opacity duration-300 group-hover:opacity-100"
          style="background-color: {item.color};"
        ></div>
      </div>
    {/each}
  </div>
{/if}
