<script lang="ts">
  import type { ReadingStatsSummaryDto } from '$lib/shared/types';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { IconName } from '$lib/shared/ui/navigation/Icon.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';

  type Props = {
    stats: ReadingStatsSummaryDto | null;
    isLoading?: boolean;
    disabledReason?: string | null;
    t?: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { stats, isLoading = false, disabledReason = null }: Props = $props();

  type StatItem = {
    label: string;
    value: string;
    icon: IconName;
    color: string;
    bg: string;
  };

  const statItems = $derived<StatItem[]>([
    {
      label: 'Iniciados', // Replace with t() key if available, like t("stats.booksStarted")
      value: stats?.booksStarted?.toString() ?? '0',
      icon: 'book',
      color: 'var(--color-accent-blue)',
      bg: 'rgba(73, 212, 255, 0.1)',
    },
    {
      label: 'Completados',
      value: stats?.booksCompleted?.toString() ?? '0',
      icon: 'check',
      color: '#4ade80',
      bg: 'rgba(74, 222, 128, 0.1)',
    },
    {
      label: 'Minutos leídos',
      value: stats?.totalMinutesRead?.toString() ?? '0',
      icon: 'clock',
      color: '#a78bfa',
      bg: 'rgba(167, 139, 250, 0.1)',
    },
    {
      label: 'Sesiones',
      value: stats?.totalSessions?.toString() ?? '0',
      icon: 'trend-up',
      color: '#fbbf24',
      bg: 'rgba(251, 191, 36, 0.1)',
    },
    {
      label: 'Progreso promedio',
      value: `${stats ? Math.round(stats.avgProgressPercentage) : 0}%`,
      icon: 'chart',
      color: '#60a5fa',
      bg: 'rgba(96, 165, 250, 0.1)',
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

        <!-- Glow effect on hover -->
        <div
          class="absolute -bottom-8 -right-8 h-24 w-24 rounded-full opacity-0 blur-[30px] transition-opacity duration-300 group-hover:opacity-100"
          style="background-color: {item.color};"
        ></div>
      </div>
    {/each}
  </div>
{/if}
