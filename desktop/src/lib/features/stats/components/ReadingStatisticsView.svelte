<script lang="ts">
  import { SafeCover } from '$lib/features/library';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import { Icon, Modal } from '$lib/shared/ui/';
  import { UNCLASSIFIED_GENRE } from '$lib/shared/services/genreHeuristic';
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

  // ─── Tooltip state (positioned via DOM mouse coords, not viewBox) ───
  let hoveredPoint: { label: string; value: number } | null = $state(null);
  let tooltipPos = $state<{ x: number; y: number } | null>(null);
  const tooltipOffsetX = 14;
  const tooltipOffsetY = -42;

  // ─── Genre tooltip state (with books) ───
  let genreTooltip: { genre: string; minutes: number; percent: number; books: string[] } | null = $state(null);
  let genreTooltipPos = $state<{ x: number; y: number } | null>(null);

  // ─── Chart fullscreen modal ───
  let chartModalOpen = $state(false);

  // ─── Thin sticky toolbar visibility ───
  let heroEl: HTMLDivElement | undefined = $state();
  let heroVisible = $state(true);

  $effect(() => {
    if (!heroEl) return;
    const observer = new IntersectionObserver(
      ([entry]) => { heroVisible = entry.isIntersecting; },
      { threshold: 0, rootMargin: '-1px 0px 0px 0px' },
    );
    observer.observe(heroEl);
    return () => observer.disconnect();
  });

  // ─── Month names via Intl.DateTimeFormat (zero-maintenance for any locale) ───
  function getShortMonthName(monthIndex: number): string {
    const date = new Date(2026, monthIndex, 1);
    const name = new Intl.DateTimeFormat(appState.locale, { month: 'short' }).format(date);
    return name.charAt(0).toUpperCase() + name.slice(1);
  }

  // Chart height: 240 (modal scales via viewBox)
  const chartHeight = 240;

  // ─── Books grouped by genre for tooltip ───
  const booksByGenre = $derived.by(() => {
    const map = new Map<string, string[]>();
    for (const book of appState.books) {
      const genre = (book.genre?.trim()) || UNCLASSIFIED_GENRE;
      if (!map.has(genre)) map.set(genre, []);
      map.get(genre)!.push(book.title);
    }
    return map;
  });

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
    const cur = current ?? 0;
    const prev = previous ?? 0;

    // Both zero → no data at all
    if (cur === 0 && prev === 0) return '';
    // First period with data → "New"
    if (cur > 0 && prev === 0) return _t('stats.firstPeriod');
    // Current period empty but history exists → do not show penalty
    if (cur === 0 && prev > 0) return '—';

    // Both have real data → normal delta
    const delta = computeDelta(cur, prev);
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

  /** Minimum pixels between x-axis labels to avoid overlap */
  const MIN_LABEL_SPACING = 46;

  const chartMeta = $derived.by(() => {
    const max = Math.max(...activitySeries.map((point) => point.value), 1);
    const min = Math.min(...activitySeries.map((point) => point.value), 0);
    const width = 800;
    const height = chartHeight;
    const step = activitySeries.length > 1 ? width / (activitySeries.length - 1) : width;

    const points = activitySeries.map((point, index) => {
      const x = index * step;
      const normalized = max === min ? 0.5 : (point.value - min) / (max - min);
      const y = height - normalized * (height - 18) - 10;
      return { ...point, x, y };
    });

    const maxVisibleLabels = Math.max(1, Math.floor(width / MIN_LABEL_SPACING));
    const labelInterval = Math.max(1, Math.ceil(points.length / maxVisibleLabels));

    const line = points
      .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x},${point.y}`)
      .join(' ');
    const area = `${line} L ${width},${height} L 0,${height} Z`;
    return { max, points, line, area, width, height, labelInterval };
  });

  /** Shorten date labels on x-axis to prevent crowding. */
  function formatChartLabel(label: string): string {
    if (activeGranularity === 'day') {
      // "2026-06-24" → "6/24" | "06-24" → "6/24"
      const m = label.match(/(?:^|\D)(\d{1,2})[\-/](\d{1,2})(?:$|\D)/);
      if (m) return `${parseInt(m[1])}/${parseInt(m[2])}`;
      if (/^\d{1,2}$/.test(label)) return label;
    }
    if (activeGranularity === 'week') {
      const w = label.match(/[WSws]\s*0*(\d+)/);
      if (w) return `${label.match(/[WSws]/)?.[0]?.toUpperCase() ?? 'W'}${w[1]}`;
    }
    if (activeGranularity === 'month') {
      const monthNum = parseInt(label.replace(/^\D+/, '').split(/[\s-]+/).pop() || '', 10);
      if (monthNum >= 1 && monthNum <= 12) {
        return getShortMonthName(monthNum - 1);
      }
      // Fallback: already a name like "January" → "Jan"
      const parts = label.split(/[\s-]+/);
      const last = parts[parts.length - 1];
      return last.length <= 4 ? last : last.slice(0, 3);
    }
    return label;
  }

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

<!-- Thin sticky toolbar (rendered outside <section> to avoid negative margin issues) -->
{#if !heroVisible}
  <div
    class="sticky top-0 z-20 border-b border-(--color-border) bg-[rgba(10,18,31,0.97)] px-4 py-3 shadow-(--shadow-panel) md:px-6"
  >
    <div class="mx-auto flex max-w-7xl items-center justify-between">
      <span class="text-sm font-semibold text-(--color-primary)">{_t('stats.title')}</span>
      <Dropdown options={periodDropdownOptions} bind:value={activePeriod} class="min-w-[120px]" />
    </div>
  </div>
{/if}

<section class="space-y-5">
  <!-- Hero header (not sticky, normal flow) -->
  <div
    bind:this={heroEl}
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
          {#if metric.delta}
            <p class="mt-2 text-xs" class:text-(--color-success)={!metric.delta.startsWith('—') && !metric.delta.startsWith('-')}>
              {metric.delta}
            </p>
          {/if}
        </article>
      {/each}
    </div>

    <div class="grid grid-cols-1 gap-4 2xl:grid-cols-[2.2fr_1fr]">
      <article
        class="relative rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
      >
        <div class="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 class="text-base font-semibold text-(--color-primary)">{_t('stats.minutesReadChart')}</h2>
            <p class="text-sm text-(--color-text-muted)">
              {_t('stats.activityTimeline')}
            </p>
          </div>

          <Dropdown
            options={granularityOptions.map((o) => ({ ...o, label: _t(o.label as MessageKey) }))}
            bind:value={activeGranularity}
            class="min-w-[100px]"
          />

          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-lg border border-(--color-border) bg-(--color-surface-subtle) text-sm text-(--color-text-muted) cursor-pointer hover:border-(--color-primary) hover:text-(--color-primary) transition-colors duration-150"
            onclick={() => (chartModalOpen = true)}
            aria-label="Pantalla completa"
            title="Pantalla completa"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="15 3 21 3 21 9"></polyline>
              <polyline points="9 21 3 21 3 15"></polyline>
              <line x1="21" y1="3" x2="14" y2="10"></line>
              <line x1="3" y1="21" x2="10" y2="14"></line>
            </svg>
          </button>
        </div>

        {#if hoveredPoint && tooltipPos}
          <div
            class="pointer-events-none absolute z-10 rounded-lg border border-(--color-border) bg-(--color-bg-panel) px-3 py-2 text-xs shadow-(--shadow-panel)"
            style="left: {tooltipPos.x + tooltipOffsetX}px; top: {tooltipPos.y + tooltipOffsetY}px;"
          >
            <p class="font-medium text-(--color-primary)">{hoveredPoint.value} min</p>
            <p class="mt-0.5 text-(--color-text-muted)">{hoveredPoint.label}</p>
          </div>
        {/if}

        <div
          class="rounded-[22px] border border-(--color-border) bg-[linear-gradient(180deg,rgba(6,14,24,0.86),rgba(10,18,30,0.94))] p-4 overflow-hidden"
        >
          <svg
            role="img"
            aria-label={_t('stats.minutesReadChart')}
            viewBox={`0 0 ${chartMeta.width} ${chartMeta.height + 28}`}
            class="w-full"
            style="height: {chartMeta.height + 40}px;"
            onmouseleave={() => {
              hoveredPoint = null;
              tooltipPos = null;
            }}
          >
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
              <circle
                cx={point.x}
                cy={point.y}
                r="5"
                fill="#49d4ff"
                style="transition: r 0.15s ease;"
                class="cursor-pointer"
                role="button"
                tabindex="0"
                aria-label={`${point.value} min — ${point.label}`}
                onmouseenter={(e) => {
                  const circle = e.currentTarget as SVGCircleElement;
                  circle.style.r = '7';
                  hoveredPoint = { label: point.label, value: point.value };
                  const article = circle.closest('article');
                  if (article) {
                    const rect = article.getBoundingClientRect();
                    tooltipPos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
                  }
                }}
                onmousemove={(e) => {
                  const circle = e.currentTarget as SVGCircleElement;
                  const article = circle.closest('article');
                  if (article) {
                    const rect = article.getBoundingClientRect();
                    tooltipPos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
                  }
                }}
                onmouseleave={(e) => {
                  const circle = e.currentTarget as SVGCircleElement;
                  circle.style.r = '5';
                  hoveredPoint = null;
                  tooltipPos = null;
                }}
                onfocus={(e) => {
                  const circle = e.currentTarget as SVGCircleElement;
                  circle.style.r = '7';
                  hoveredPoint = { label: point.label, value: point.value };
                  const ib = circle.getBoundingClientRect();
                  const ab = circle.closest('article')!.getBoundingClientRect();
                  tooltipPos = { x: ib.left - ab.left + 14, y: ib.top - ab.top - 30 };
                }}
                onblur={(e) => {
                  const circle = e.currentTarget as SVGCircleElement;
                  circle.style.r = '5';
                  hoveredPoint = null;
                  tooltipPos = null;
                }}
                onkeydown={(e) => {
                  if (e.key === 'Escape') {
                    hoveredPoint = null;
                    tooltipPos = null;
                  }
                }}
              ></circle>
            {/each}

            {#each chartMeta.points as point, i}
              {#if i % chartMeta.labelInterval === 0 || i === chartMeta.points.length - 1}
                <text
                  x={point.x}
                  y={chartMeta.height + 18}
                  text-anchor="middle"
                  font-size="10"
                  fill="var(--color-text-muted)"
                >
                  {formatChartLabel(point.label)}
                </text>
              {/if}
            {/each}
          </svg>
        </div>
      </article>

      <!-- Fullscreen modal for chart -->
      <Modal bind:open={chartModalOpen} title={_t('stats.minutesReadChart')} size="xl">
        {#snippet children()}
          <div class="relative -mx-2 -mt-2">
            {#if hoveredPoint && tooltipPos}
              <div
                class="pointer-events-none absolute z-10 rounded-lg border border-(--color-border) bg-(--color-bg-panel) px-3 py-2 text-xs shadow-(--shadow-panel)"
                style="left: {tooltipPos.x + 14}px; top: {tooltipPos.y - 42}px;"
              >
                <p class="font-medium text-(--color-primary)">{hoveredPoint.value} min</p>
                <p class="mt-0.5 text-(--color-text-muted)">{hoveredPoint.label}</p>
              </div>
            {/if}
            <div
              class="rounded-[22px] border border-(--color-border) bg-[linear-gradient(180deg,rgba(6,14,24,0.86),rgba(10,18,30,0.94))] p-4"
            >
              <svg
                role="img"
                aria-label={_t('stats.minutesReadChart')}
                viewBox={`0 0 ${chartMeta.width} ${chartMeta.height + 28}`}
                class="w-full"
                style="height: {chartMeta.height + 320}px;"
                onmouseleave={() => {
                  hoveredPoint = null;
                  tooltipPos = null;
                }}
              >
                <defs>
                  <linearGradient id="lineStrokeModal" x1="0%" x2="100%" y1="0%" y2="0%">
                    <stop offset="0%" stop-color="#4e8cff"></stop>
                    <stop offset="100%" stop-color="#49d4ff"></stop>
                  </linearGradient>
                  <linearGradient id="lineFillModal" x1="0%" x2="0%" y1="0%" y2="100%">
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

                <path d={chartMeta.area} fill="url(#lineFillModal)"></path>
                <path d={chartMeta.line} fill="none" stroke="url(#lineStrokeModal)" stroke-width="3" stroke-linecap="round"></path>

                {#each chartMeta.points as point}
                  <circle
                    cx={point.x}
                    cy={point.y}
                    r="5"
                    fill="#49d4ff"
                    style="transition: r 0.15s ease;"
                    class="cursor-pointer"
                    role="button"
                    tabindex="0"
                    aria-label={`${point.value} min — ${point.label}`}
                    onmouseenter={(e) => {
                      const circle = e.currentTarget as SVGCircleElement;
                      circle.style.r = '7';
                      hoveredPoint = { label: point.label, value: point.value };
                      const modal = circle.closest('[role="dialog"]');
                      if (modal) {
                        const rect = modal.getBoundingClientRect();
                        tooltipPos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
                      }
                    }}
                    onmousemove={(e) => {
                      const modal = (e.currentTarget as SVGCircleElement).closest('[role="dialog"]');
                      if (modal) {
                        const rect = modal.getBoundingClientRect();
                        tooltipPos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
                      }
                    }}
                    onmouseleave={(e) => {
                      const circle = e.currentTarget as SVGCircleElement;
                      circle.style.r = '5';
                      hoveredPoint = null;
                      tooltipPos = null;
                    }}
                    onfocus={(e) => {
                      const circle = e.currentTarget as SVGCircleElement;
                      circle.style.r = '7';
                      hoveredPoint = { label: point.label, value: point.value };
                      const ib = circle.getBoundingClientRect();
                      const modal = circle.closest('[role="dialog"]')!;
                      const ab = modal.getBoundingClientRect();
                      tooltipPos = { x: ib.left - ab.left + 14, y: ib.top - ab.top - 30 };
                    }}
                    onblur={(e) => {
                      const circle = e.currentTarget as SVGCircleElement;
                      circle.style.r = '5';
                      hoveredPoint = null;
                      tooltipPos = null;
                    }}
                    onkeydown={(e) => {
                      if (e.key === 'Escape') {
                        hoveredPoint = null;
                        tooltipPos = null;
                      }
                    }}
                  ></circle>
                {/each}

                {#each chartMeta.points as point, i}
                  {#if i % chartMeta.labelInterval === 0 || i === chartMeta.points.length - 1}
                    <text
                      x={point.x}
                      y={chartMeta.height + 18}
                      text-anchor="middle"
                      font-size="11"
                      fill="var(--color-text-muted)"
                    >
                      {formatChartLabel(point.label)}
                    </text>
                  {/if}
                {/each}
              </svg>
            </div>
          </div>
        {/snippet}
      </Modal>

      <article
        class="relative rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-panel)"
      >
        <!-- Genre tooltip overlay with books -->
        {#if genreTooltip && genreTooltipPos}
          <div
            class="pointer-events-none absolute z-10 rounded-lg border border-(--color-border) bg-(--color-bg-panel) px-3 py-2 text-xs shadow-(--shadow-panel)"
            style="left: {genreTooltipPos.x + 14}px; top: {genreTooltipPos.y - 38}px;"
          >
            <p class="font-medium text-(--color-primary)">{genreTooltip.genre}</p>
            <p class="mt-0.5 text-(--color-text-muted)">{genreTooltip.minutes} min · {genreTooltip.percent}%</p>
            {#if genreTooltip.books.length > 0}
              <div class="mt-1.5 border-t border-(--color-border) pt-1.5">
                {#each genreTooltip.books.slice(0, 3) as bookTitle}
                  <p class="truncate text-(--color-text-muted)">
                    <Icon name="book" size="sm" class="inline -mt-0.5 mr-1" />
                    {bookTitle}
                  </p>
                {/each}
                {#if genreTooltip.books.length > 3}
                  <p class="mt-0.5 text-(--color-text-muted)">+{genreTooltip.books.length - 3} más</p>
                {/if}
              </div>
            {/if}
          </div>
        {/if}

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
              <div
                class="flex cursor-pointer items-center justify-between gap-3 rounded-lg px-2 py-1.5 text-sm transition-colors duration-150 hover:bg-(--color-surface-subtle)"
                role="button"
                tabindex="0"
                aria-label={`${entry.genre}: ${entry.minutes} min, ${entry.percent}%`}
                onmouseenter={(e) => {
                  const titles = booksByGenre.get(entry.genre) ?? [];
                  genreTooltip = { genre: entry.genre, minutes: entry.minutes, percent: entry.percent, books: titles };
                  const article = (e.currentTarget as HTMLElement).closest('article');
                  if (article) {
                    const rect = article.getBoundingClientRect();
                    genreTooltipPos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
                  }
                }}
                onmousemove={(e) => {
                  const article = (e.currentTarget as HTMLElement).closest('article');
                  if (article) {
                    const rect = article.getBoundingClientRect();
                    genreTooltipPos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
                  }
                }}
                onmouseleave={() => {
                  genreTooltip = null;
                  genreTooltipPos = null;
                }}
                onfocus={(e) => {
                  const titles = booksByGenre.get(entry.genre) ?? [];
                  genreTooltip = { genre: entry.genre, minutes: entry.minutes, percent: entry.percent, books: titles };
                  const el = e.currentTarget as HTMLElement;
                  const ab = el.closest('article')!.getBoundingClientRect();
                  const ib = el.getBoundingClientRect();
                  genreTooltipPos = { x: ib.left - ab.left + 14, y: ib.top - ab.top - 38 };
                }}
                onblur={() => {
                  genreTooltip = null;
                  genreTooltipPos = null;
                }}
                onkeydown={(e) => {
                  if (e.key === 'Escape') {
                    genreTooltip = null;
                    genreTooltipPos = null;
                  }
                }}
              >
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
                  class={`flex h-8 w-8 items-center justify-center rounded-full text-2xs ${day.active ? 'bg-(--gradient-accent) text-[#07111d]' : 'border border-(--color-border) bg-(--color-surface-subtle) text-(--color-text-muted)'}`}
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
