<script lang="ts">
  import { SafeCover } from '$lib/features/library';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import { getSafeProgressPercentage } from '$lib/shared/stores/homeState';
  import {
    periodLabels,
    calculateGenreDistribution,
    periodWindow,
    previousWindow,
    computeDelta,
    type PeriodKey,
    type Granularity,
    type Props,
  } from './readingStatsState.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

  let { appState, t: tProp }: Props & { t?: (key: MessageKey, params?: Record<string, string | number>) => string } = $props();

  let activePeriod = $state<PeriodKey>('month');
  let activeGranularity = $state<Granularity>('day');

  const periodDropdownOptions = $derived(
    Object.entries(periodLabels).map(([value]) => ({
      value,
      label: _t(`stats.period${value.charAt(0).toUpperCase() + value.slice(1)}` as MessageKey),
    })),
  );
  const granularityOptions = $derived<Array<{ value: string; label: string }>>([
    { value: 'day', label: 'stats.granularityDay' },
    { value: 'week', label: 'stats.granularityWeek' },
    { value: 'month', label: 'stats.granularityMonth' },
  ]);

  // ─── Data fetching effects ───

  $effect(() => {
    void appState.loadStatsActivity(activePeriod, activeGranularity);
  });

  $effect(() => {
    const { from, to } = periodWindow(activePeriod);
    const { from: prevFrom, to: prevTo } = previousWindow(activePeriod);
    void appState.loadStatsRange(from, to, undefined, 'current');
    void appState.loadStatsRange(prevFrom, prevTo, undefined, 'previous');
  });

  $effect(() => {
    void appState.loadStatsStreak();
  });

  // ─── Derived from domain state with fallbacks from books ───

  const sd = $derived(appState.statsDomain);

  const genreDistribution = $derived(calculateGenreDistribution(appState.books));

  const totalMinutes = $derived(
    sd.currentStats?.totalMinutesRead ??
      appState.books.reduce((sum, book) => sum + book.minutesRead, 0),
  );
  const totalSessions = $derived(
    sd.currentStats?.totalSessions ?? Math.max(appState.books.length * 2, 0),
  );
  const booksStarted = $derived(
    sd.currentStats?.booksStarted ??
      appState.books.filter((book) => getSafeProgressPercentage(book) > 0).length,
  );
  const booksCompleted = $derived(
    sd.currentStats?.booksCompleted ??
      appState.books.filter((book) => book.readingStatus === 'completed' || getSafeProgressPercentage(book) >= 100)
        .length,
  );
  const averageProgress = $derived(
    sd.currentStats?.avgProgressPercentage ??
      (appState.books.length
        ? appState.books.reduce((sum, book) => sum + getSafeProgressPercentage(book), 0) /
          appState.books.length
        : 0),
  );

  const _t = (key: MessageKey, params?: Record<string, string | number>): string => {
    return tProp ? tProp(key, params) : key;
  };

  function deltaText(current: number | undefined, previous: number | undefined): string {
    const delta = computeDelta(current ?? 0, previous ?? 0);
    if (delta === null) return _t('stats.noPriorData');
    const sign = delta >= 0 ? '+' : '';
    return `${sign}${delta}% ${_t(`stats.delta${activePeriod.charAt(0).toUpperCase() + activePeriod.slice(1)}` as MessageKey)}`;
  }

  const metricCards = $derived([
    {
      label: _t('stats.minutesRead'),
      value: totalMinutes.toLocaleString('es-CO'),
      delta: deltaText(sd.currentStats?.totalMinutesRead, sd.previousStats?.totalMinutesRead),
    },
    {
      label: _t('stats.sessions'),
      value: totalSessions.toLocaleString('es-CO'),
      delta: deltaText(sd.currentStats?.totalSessions, sd.previousStats?.totalSessions),
    },
    {
      label: _t('stats.booksStarted'),
      value: booksStarted.toLocaleString('es-CO'),
      delta: deltaText(sd.currentStats?.booksStarted, sd.previousStats?.booksStarted),
    },
    {
      label: _t('stats.booksCompleted'),
      value: booksCompleted.toLocaleString('es-CO'),
      delta: deltaText(sd.currentStats?.booksCompleted, sd.previousStats?.booksCompleted),
    },
    {
      label: _t('stats.averageProgress'),
      value: `${Math.round(averageProgress)}%`,
      delta: deltaText(
        sd.currentStats?.avgProgressPercentage,
        sd.previousStats?.avgProgressPercentage,
      ),
    },
  ]);

  const activitySeries = $derived(
    sd.activitySeries.length > 0
      ? sd.activitySeries.map((point) => ({
          label: point.bucket,
          value: point.minutes,
        }))
      : [],
  );

  const chartMeta = $derived.by(() => {
    const max = Math.max(...activitySeries.map((point) => point.value), 1);
    const min = Math.min(...activitySeries.map((point) => point.value), 0);
    const width = 560;
    const height = 240;
    const step = activitySeries.length > 1 ? width / (activitySeries.length - 1) : width;

    const points = activitySeries.map((point, index) => {
      const x = index * step;
      const normalized = max === min ? 0.5 : (point.value - min) / (max - min);
      const y = height - normalized * (height - 18) - 10;
      return { ...point, x, y };
    });

    const line = points
      .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x},${point.y}`)
      .join(' ');
    const area = `${line} L ${width},${height} L 0,${height} Z`;
    return { max, points, line, area, width, height };
  });

  const mostReadBooks = $derived.by(() =>
    [...appState.books].sort((left, right) => right.minutesRead - left.minutesRead).slice(0, 3),
  );

  const streakDays = $derived(sd.streakDays);

  const streakCalendar = $derived.by(() => {
    const days = 14;
    const activeDays = Math.min(sd.streakDays, days);
    return Array.from({ length: days }, (_, index) => ({
      label: ['L', 'M', 'M', 'J', 'V', 'S', 'D'][index % 7],
      active: index >= days - activeDays,
    }));
  });

  const averageMinutesPerSession = $derived(
    totalSessions > 0 ? Math.round(totalMinutes / totalSessions) : 0,
  );
  const averageMinutesPerDay = $derived(
    activitySeries.length > 0 ? Math.round(totalMinutes / activitySeries.length) : 0,
  );
  const totalPagesRead = $derived(
    appState.books.reduce((sum, book) => sum + Math.max(book.currentPage, 0), 0),
  );

  // ─── Loading / unavailable derived from domain state ───
  const isLoading = $derived(sd.isLoadingActivity || sd.isLoadingRange || sd.isLoadingStreak);
  const disabledReason = $derived(
    sd.rangeUnavailableReason || sd.activityUnavailableReason || sd.streakUnavailableReason,
  );
</script>

<section class="space-y-5">
  <div
    class="rounded-(--radius-2xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(17,30,48,0.94),rgba(10,18,31,0.94))] p-5 shadow-(--shadow-hero)"
  >
    <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
      <div>
        <h1 class="text-3xl font-semibold tracking-tight text-(--color-primary)">{_t('stats.title')}</h1>
        <p class="mt-1 text-sm text-(--color-text-muted)">{_t('stats.subtitle')}</p>
      </div>

      <span class="sr-only">Periodo</span>
      <Dropdown options={periodDropdownOptions} bind:value={activePeriod} class="min-w-[120px]" />
    </div>
  </div>

  {#if disabledReason}
    <div
      class="rounded-(--radius-xl) border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900"
    >
      {disabledReason}
    </div>
  {:else if isLoading}
    <div
      class="rounded-(--radius-xl) border border-(--color-border) bg-(--color-bg-panel) px-4 py-8 text-sm text-(--color-text-muted)"
    >
      {_t('stats.loading')}
    </div>
  {:else}
    <div class="grid grid-cols-1 gap-4 xl:grid-cols-5">
      {#each metricCards as metric}
        <article
          class="rounded-(--radius-xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
        >
          <p class="text-xs text-(--color-text-muted)">{metric.label}</p>
          <p class="mt-3 text-3xl font-semibold tracking-tight text-(--color-primary)">
            {metric.value}
          </p>
          <p class="mt-2 text-xs text-(--color-success)">{metric.delta}</p>
        </article>
      {/each}
    </div>

    <div class="grid grid-cols-1 gap-4 2xl:grid-cols-[1.6fr_1fr]">
      <article
        class="rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
      >
        <div class="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 class="text-base font-semibold text-(--color-primary)">{_t('stats.minutesReadChart')}</h2>
            <p class="text-sm text-(--color-text-muted)">
              {_t('stats.activityTimeline')}
            </p>
          </div>

          <span class="text-xs text-(--color-text-muted)">{_t('stats.view')}</span>
          <Dropdown
            options={granularityOptions.map((o) => ({ ...o, label: _t(o.label as MessageKey) }))}
            bind:value={activeGranularity}
            class="min-w-[100px]"
          />
        </div>

        <div
          class="rounded-[22px] border border-(--color-border) bg-[linear-gradient(180deg,rgba(6,14,24,0.86),rgba(10,18,30,0.94))] p-4"
        >
          <svg viewBox={`0 0 ${chartMeta.width} ${chartMeta.height + 28}`} class="h-[280px] w-full">
            <defs>
              <linearGradient id="lineStroke" x1="0%" x2="100%" y1="0%" y2="0%">
                <stop offset="0%" stop-color="#4e8cff"></stop>
                <stop offset="100%" stop-color="#49d4ff"></stop>
              </linearGradient>
              <linearGradient id="lineFill" x1="0%" x2="0%" y1="0%" y2="100%">
                <stop offset="0%" stop-color="rgba(78,140,255,0.35)"></stop>
                <stop offset="100%" stop-color="rgba(78,140,255,0.02)"></stop>
              </linearGradient>
            </defs>

            {#each [0, 0.25, 0.5, 0.75, 1] as tick}
              <line
                x1="0"
                y1={chartMeta.height - tick * (chartMeta.height - 18)}
                x2={chartMeta.width}
                y2={chartMeta.height - tick * (chartMeta.height - 18)}
                stroke="rgba(148,173,206,0.12)"
                stroke-width="1"
              ></line>
            {/each}

            <path d={chartMeta.area} fill="url(#lineFill)"></path>
            <path
              d={chartMeta.line}
              fill="none"
              stroke="url(#lineStroke)"
              stroke-width="3"
              stroke-linecap="round"
            ></path>

            {#each chartMeta.points as point}
              <circle cx={point.x} cy={point.y} r="4" fill="#49d4ff"></circle>
            {/each}

            {#each chartMeta.points as point}
              <text
                x={point.x}
                y={chartMeta.height + 18}
                text-anchor="middle"
                font-size="11"
                fill="var(--color-text-muted)"
              >
                {point.label}
              </text>
            {/each}
          </svg>
        </div>
      </article>

      <article
        class="rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
      >
        <div class="mb-4">
          <h2 class="text-base font-semibold text-(--color-primary)">{_t('stats.timeByGenre')}</h2>
          <p class="text-sm text-(--color-text-muted)">
            {_t('stats.genreDistribution')}
          </p>
        </div>

        <div
          class="flex flex-col items-center gap-6 lg:flex-row lg:items-center lg:justify-between"
        >
          <div
            class="relative h-52 w-52 rounded-full"
            style={`background: conic-gradient(${genreDistribution
              .map((entry, index, array) => {
                const start = array
                  .slice(0, index)
                  .reduce((sum, current) => sum + current.percent, 0);
                const end = start + entry.percent;
                return `${entry.color} ${start}% ${end}%`;
              })
              .join(', ')});`}
          >
            <div
              class="absolute inset-[26px] flex flex-col items-center justify-center rounded-full bg-[rgba(9,17,29,0.96)] text-center"
            >
              <span class="text-3xl font-semibold text-(--color-primary)"
                >{totalMinutes.toLocaleString('es-CO')}</span
              >
              <span class="text-xs text-(--color-text-muted)">{_t('stats.minutes')}</span>
            </div>
          </div>

          <div class="w-full space-y-3">
            {#each genreDistribution as entry}
              <div class="flex items-center justify-between gap-3 text-sm">
                <div class="flex items-center gap-3">
                  <span class="h-3 w-3 rounded-full" style={`background:${entry.color};`}></span>
                  <span class="text-(--color-secondary)">{entry.genre}</span>
                </div>
                <span class="text-(--color-primary)">{entry.percent}%</span>
              </div>
            {/each}
          </div>
        </div>
      </article>
    </div>

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[1.35fr_1fr]">
      <article
        class="rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
      >
        <div class="mb-4">
          <h2 class="text-base font-semibold text-(--color-primary)">{_t('stats.mostReadBooks')}</h2>
          <p class="text-sm text-(--color-text-muted)">
            {_t('stats.mostReadBooksDesc')}
          </p>
        </div>

        <div class="space-y-3">
          {#each mostReadBooks as book}
            <button
              type="button"
              class="flex w-full items-center gap-3 rounded-[22px] border border-(--color-border) bg-(--color-surface-subtle) p-3 cursor-pointer hover:border-(--color-primary) text-left"
              onclick={() => appState.openShelfDetails(book)}
            >
              <div
                class="h-14 w-10 shrink-0 overflow-hidden rounded-xl bg-(--color-surface-subtle)"
              >
                <SafeCover
                  path={book.coverPath ?? ''}
                  alt={_t('stats.bookCover', { title: book.title })}
                  className="h-full w-full object-cover"
                >
                  {#snippet fallback()}
                    <div
                      class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] text-[9px] uppercase tracking-[0.16em] text-(--color-primary)"
                    >
                      {_t('stats.bookPlaceholder')}
                    </div>
                  {/snippet}
                </SafeCover>
              </div>

              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-(--color-primary)">{book.title}</p>
                <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                  <div
                    class="h-full rounded-full bg-(--gradient-accent-h)"
                    style={`width: ${Math.max(12, Math.round((book.minutesRead / Math.max(mostReadBooks[0]?.minutesRead || 1, 1)) * 100))}%;`}
                  ></div>
                </div>
              </div>

              <span class="shrink-0 text-sm text-(--color-secondary)">{book.minutesRead} min</span>
            </button>
          {/each}
        </div>
      </article>

      <article class="grid gap-4">
        <div
          class="rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
        >
          <div class="mb-3">
            <h2 class="text-base font-semibold text-(--color-primary)">{_t('stats.currentStreak')}</h2>
            <p class="text-sm text-(--color-text-muted)">{_t('stats.streakDesc')}</p>
          </div>

          <p class="text-4xl font-semibold tracking-tight text-(--color-primary)">
            {_t('stats.days', { count: streakDays })}
          </p>
          <p class="mt-1 text-sm text-(--color-text-muted)">{_t('stats.keepGoing')}</p>

          <div class="mt-5 flex flex-wrap gap-2">
            {#each streakCalendar as day}
              <div class="flex flex-col items-center gap-2">
                <div
                  class={`flex h-8 w-8 items-center justify-center rounded-full text-(--text-2xs) ${day.active ? 'bg-(--gradient-accent) text-[#07111d]' : 'border border-(--color-border) bg-(--color-surface-subtle) text-(--color-text-muted)'}`}
                >
                  {day.label}
                </div>
              </div>
            {/each}
          </div>
        </div>

        <div
          class="rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
        >
          <div class="mb-4">
            <h2 class="text-base font-semibold text-(--color-primary)">{_t('stats.additionalInfo')}</h2>
            <p class="text-sm text-(--color-text-muted)">
              {_t('stats.additionalDesc')}
            </p>
          </div>

          <div class="grid grid-cols-1 gap-3 sm:grid-cols-3 xl:grid-cols-1">
            <div
              class="rounded-[20px] border border-(--color-border) bg-(--color-surface-subtle) p-3"
            >
              <p class="text-xs text-(--color-text-muted)">{_t('stats.averagePerSession')}</p>
              <p class="mt-2 text-2xl font-semibold text-(--color-primary)">
                {averageMinutesPerSession} min
              </p>
            </div>
            <div
              class="rounded-[20px] border border-(--color-border) bg-(--color-surface-subtle) p-3"
            >
              <p class="text-xs text-(--color-text-muted)">{_t('stats.averagePerDay')}</p>
              <p class="mt-2 text-2xl font-semibold text-(--color-primary)">
                {averageMinutesPerDay} min
              </p>
            </div>
            <div
              class="rounded-[20px] border border-(--color-border) bg-(--color-surface-subtle) p-3"
            >
              <p class="text-xs text-(--color-text-muted)">{_t('stats.pagesRead')}</p>
              <p class="mt-2 text-2xl font-semibold text-(--color-primary)">
                {totalPagesRead.toLocaleString('es-CO')}
              </p>
            </div>
          </div>
        </div>
      </article>
    </div>
  {/if}
</section>
