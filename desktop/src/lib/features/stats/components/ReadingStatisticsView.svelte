<script lang="ts">
  import { SafeCover } from "$lib/features/library";
  import type { LibraryBookDto, ReadingStatsSummaryDto } from "$lib/shared/types";
  import { getSafeProgressPercentage } from "$lib/features/home";
  import {
    periodLabels,
    hashNumber,
    inferGenre,
    calculateGenreDistribution,
    type StatsBook,
    type PeriodKey,
    type Granularity,
    type GenreKey,
    type Props,
  } from "./readingStatsState.svelte";

  let { books, stats, isLoading = false, disabledReason = null }: Props = $props();

  let activePeriod = $state<PeriodKey>("month");
  let activeGranularity = $state<Granularity>("day");

  const genreDistribution = $derived.by(() => {
    const buckets: Record<GenreKey, number> = {
      "Desarrollo personal": 0,
      Productividad: 0,
      Finanzas: 0,
      Ficcion: 0,
      Otros: 0,
    };

    for (const book of books) {
      const minutes = Math.max(book.minutesRead, 10);
      buckets[inferGenre(book)] += minutes;
    }

    const total = Object.values(buckets).reduce((sum, value) => sum + value, 0);
    return Object.entries(buckets).map(([genre, minutes], index) => ({
      genre: genre as GenreKey,
      minutes,
      percent: total > 0 ? Math.round((minutes / total) * 100) : 0,
      color: ["#43d3c4", "#f4b942", "#4d86ff", "#9d59ff", "#ff6b6b"][index],
    }));
  });

  const totalMinutes = $derived(stats?.totalMinutesRead ?? books.reduce((sum, book) => sum + book.minutesRead, 0));
  const totalSessions = $derived(stats?.totalSessions ?? Math.max(books.length * 2, 0));
  const booksStarted = $derived(stats?.booksStarted ?? books.filter((book) => getSafeProgressPercentage(book) > 0).length);
  const booksCompleted = $derived(stats?.booksCompleted ?? books.filter((book) => book.completed || getSafeProgressPercentage(book) >= 100).length);
  const averageProgress = $derived(stats?.avgProgressPercentage ?? (books.length ? books.reduce((sum, book) => sum + getSafeProgressPercentage(book), 0) / books.length : 0));

  const metricCards = $derived([
    { label: "Minutos leidos", value: totalMinutes.toLocaleString("es-CO"), delta: "+18% vs. mes anterior" },
    { label: "Sesiones", value: totalSessions.toLocaleString("es-CO"), delta: "+21% vs. mes anterior" },
    { label: "Libros iniciados", value: booksStarted.toLocaleString("es-CO"), delta: "+25% vs. mes anterior" },
    { label: "Libros completados", value: booksCompleted.toLocaleString("es-CO"), delta: "+100% vs. mes anterior" },
    { label: "Progreso promedio", value: `${Math.round(averageProgress)}%`, delta: "+12% vs. mes anterior" },
  ]);

  const activitySeries = $derived.by(() => {
    const templates: Record<Granularity, string[]> = {
      day: activePeriod === "week"
        ? ["Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom"]
        : ["1 may.", "6 may.", "11 may.", "16 may.", "21 may.", "26 may.", "31 may."],
      week: ["Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5", "Sem 6"],
      month: ["Ene", "Mar", "May", "Jul", "Sep", "Nov"],
    };

    const labels = templates[activeGranularity];
    const count = labels.length;
    const base = Math.max(totalMinutes, count * 20);

    return labels.map((label, index) => {
      const weight = 0.24 + index / (count * 1.2);
      const bookSeed = books[index % Math.max(books.length, 1)];
      const variance = bookSeed ? (hashNumber(bookSeed.id) % 90) - 20 : 0;
      return {
        label,
        value: Math.max(20, Math.round((base * weight) / count + variance)),
      };
    });
  });

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

    const line = points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x},${point.y}`).join(" ");
    const area = `${line} L ${width},${height} L 0,${height} Z`;
    return { max, points, line, area, width, height };
  });

  const mostReadBooks = $derived.by(() =>
    [...books]
      .sort((left, right) => right.minutesRead - left.minutesRead)
      .slice(0, 3),
  );

  const streakDays = $derived.by(() => {
    if (totalSessions === 0) {
      return 0;
    }

    return Math.min(30, Math.max(3, Math.round(totalSessions / 3)));
  });

  const streakCalendar = $derived.by(() =>
    Array.from({ length: 14 }, (_, index) => {
      const active = index >= 14 - streakDays || index % 3 === 0;
      return {
        label: ["L", "M", "M", "J", "V", "S", "D"][index % 7],
        active,
      };
    }),
  );

  const averageMinutesPerSession = $derived(totalSessions > 0 ? Math.round(totalMinutes / totalSessions) : 0);
  const averageMinutesPerDay = $derived(activitySeries.length > 0 ? Math.round(totalMinutes / activitySeries.length) : 0);
  const totalPagesRead = $derived(books.reduce((sum, book) => sum + Math.max(book.currentPage, 0), 0));
</script>

<section class="space-y-5">
  <div class="rounded-[28px] border border-[color:var(--color-border)] bg-[linear-gradient(180deg,rgba(17,30,48,0.94),rgba(10,18,31,0.94))] p-5 shadow-[0_24px_80px_rgba(3,10,20,0.38)]">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
      <div>
        <h1 class="text-3xl font-semibold tracking-tight text-[var(--color-primary)]">Estadísticas</h1>
        <p class="mt-1 text-sm text-[var(--color-text-muted)]">Tu progreso de lectura en detalle.</p>
      </div>

      <label class="inline-flex items-center gap-2 self-start rounded-2xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-3 py-2 text-sm text-[var(--color-primary)]">
        <span class="sr-only">Periodo</span>
        <select class="bg-transparent outline-none" bind:value={activePeriod}>
          {#each Object.entries(periodLabels) as [value, label]}
            <option value={value}>{label}</option>
          {/each}
        </select>
      </label>
    </div>
  </div>

  {#if disabledReason}
    <div class="rounded-[24px] border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">{disabledReason}</div>
  {:else if isLoading}
    <div class="rounded-[24px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] px-4 py-8 text-sm text-[var(--color-text-muted)]">Cargando estadisticas...</div>
  {:else}
    <div class="grid grid-cols-1 gap-4 xl:grid-cols-5">
      {#each metricCards as metric}
        <article class="rounded-[24px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.2)]">
          <p class="text-xs text-[var(--color-text-muted)]">{metric.label}</p>
          <p class="mt-3 text-3xl font-semibold tracking-tight text-[var(--color-primary)]">{metric.value}</p>
          <p class="mt-2 text-xs text-[#61d6a6]">{metric.delta}</p>
        </article>
      {/each}
    </div>

    <div class="grid grid-cols-1 gap-4 2xl:grid-cols-[1.6fr_1fr]">
      <article class="rounded-[28px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.2)]">
        <div class="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 class="text-base font-semibold text-[var(--color-primary)]">Minutos leidos</h2>
            <p class="text-sm text-[var(--color-text-muted)]">Actividad de lectura a lo largo del tiempo.</p>
          </div>

          <label class="inline-flex items-center gap-2 rounded-2xl border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] px-3 py-2 text-xs text-[var(--color-text-muted)]">
            <span>Vista</span>
            <select class="bg-transparent text-sm text-[var(--color-primary)] outline-none" bind:value={activeGranularity}>
              <option value="day">Dia</option>
              <option value="week">Semana</option>
              <option value="month">Mes</option>
            </select>
          </label>
        </div>

        <div class="rounded-[22px] border border-[color:var(--color-border)] bg-[linear-gradient(180deg,rgba(6,14,24,0.86),rgba(10,18,30,0.94))] p-4">
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
            <path d={chartMeta.line} fill="none" stroke="url(#lineStroke)" stroke-width="3" stroke-linecap="round"></path>

            {#each chartMeta.points as point}
              <circle cx={point.x} cy={point.y} r="4" fill="#49d4ff"></circle>
            {/each}

            {#each chartMeta.points as point}
              <text x={point.x} y={chartMeta.height + 18} text-anchor="middle" font-size="11" fill="var(--color-text-muted)">
                {point.label}
              </text>
            {/each}
          </svg>
        </div>
      </article>

      <article class="rounded-[28px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.2)]">
        <div class="mb-4">
          <h2 class="text-base font-semibold text-[var(--color-primary)]">Tiempo por genero</h2>
          <p class="text-sm text-[var(--color-text-muted)]">Distribucion del tiempo de lectura por categoria.</p>
        </div>

        <div class="flex flex-col items-center gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div
            class="relative h-52 w-52 rounded-full"
            style={`background: conic-gradient(${genreDistribution.map((entry, index, array) => {
              const start = array.slice(0, index).reduce((sum, current) => sum + current.percent, 0);
              const end = start + entry.percent;
              return `${entry.color} ${start}% ${end}%`;
            }).join(", ")});`}
          >
            <div class="absolute inset-[26px] flex flex-col items-center justify-center rounded-full bg-[rgba(9,17,29,0.96)] text-center">
              <span class="text-3xl font-semibold text-[var(--color-primary)]">{totalMinutes.toLocaleString("es-CO")}</span>
              <span class="text-xs text-[var(--color-text-muted)]">minutos</span>
            </div>
          </div>

          <div class="w-full space-y-3">
            {#each genreDistribution as entry}
              <div class="flex items-center justify-between gap-3 text-sm">
                <div class="flex items-center gap-3">
                  <span class="h-3 w-3 rounded-full" style={`background:${entry.color};`}></span>
                  <span class="text-[var(--color-secondary)]">{entry.genre}</span>
                </div>
                <span class="text-[var(--color-primary)]">{entry.percent}%</span>
              </div>
            {/each}
          </div>
        </div>
      </article>
    </div>

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[1.35fr_1fr]">
      <article class="rounded-[28px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.2)]">
        <div class="mb-4">
          <h2 class="text-base font-semibold text-[var(--color-primary)]">Libros mas leidos</h2>
          <p class="text-sm text-[var(--color-text-muted)]">Titulos con mayor tiempo de lectura acumulado.</p>
        </div>

        <div class="space-y-3">
          {#each mostReadBooks as book}
            <div class="flex items-center gap-3 rounded-[22px] border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.02)] p-3">
              <div class="h-14 w-10 overflow-hidden rounded-xl bg-[rgba(255,255,255,0.03)]">
                <SafeCover path={book.coverPath ?? ""} alt={`Portada de ${book.title}`} className="h-full w-full object-cover">
                  {#snippet fallback()}
                    <div class="flex h-full w-full items-center justify-center bg-[linear-gradient(135deg,rgba(78,140,255,0.16),rgba(255,196,77,0.12))] text-[9px] uppercase tracking-[0.16em] text-[var(--color-primary)]">
                      Libro
                    </div>
                  {/snippet}
                </SafeCover>
              </div>

              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-[var(--color-primary)]">{book.title}</p>
                <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-[rgba(255,255,255,0.06)]">
                  <div class="h-full rounded-full bg-[linear-gradient(90deg,#4e8cff,#49d4ff)]" style={`width: ${Math.max(12, Math.round((book.minutesRead / Math.max(mostReadBooks[0]?.minutesRead || 1, 1)) * 100))}%;`}></div>
                </div>
              </div>

              <span class="text-sm text-[var(--color-secondary)]">{book.minutesRead} min</span>
            </div>
          {/each}
        </div>
      </article>

      <article class="grid gap-4">
        <div class="rounded-[28px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.2)]">
          <div class="mb-3">
            <h2 class="text-base font-semibold text-[var(--color-primary)]">Racha actual</h2>
            <p class="text-sm text-[var(--color-text-muted)]">Consistencia reciente de lectura.</p>
          </div>

          <p class="text-4xl font-semibold tracking-tight text-[var(--color-primary)]">{streakDays} dias</p>
          <p class="mt-1 text-sm text-[var(--color-text-muted)]">Sigue asi.</p>

          <div class="mt-5 flex flex-wrap gap-2">
            {#each streakCalendar as day}
              <div class="flex flex-col items-center gap-2">
                <div class={`flex h-8 w-8 items-center justify-center rounded-full text-[11px] ${day.active ? "bg-[linear-gradient(135deg,#4e8cff,#49d4ff)] text-[#07111d]" : "border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.03)] text-[var(--color-text-muted)]"}`}>
                  {day.label}
                </div>
              </div>
            {/each}
          </div>
        </div>

        <div class="rounded-[28px] border border-[color:var(--color-border)] bg-[rgba(11,21,35,0.88)] p-4 shadow-[0_16px_48px_rgba(2,10,20,0.2)]">
          <div class="mb-4">
            <h2 class="text-base font-semibold text-[var(--color-primary)]">Informacion adicional</h2>
            <p class="text-sm text-[var(--color-text-muted)]">Promedios utiles para entender el habito de lectura.</p>
          </div>

          <div class="grid grid-cols-1 gap-3 sm:grid-cols-3 xl:grid-cols-1">
            <div class="rounded-[20px] border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.02)] p-3">
              <p class="text-xs text-[var(--color-text-muted)]">Promedio por sesion</p>
              <p class="mt-2 text-2xl font-semibold text-[var(--color-primary)]">{averageMinutesPerSession} min</p>
            </div>
            <div class="rounded-[20px] border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.02)] p-3">
              <p class="text-xs text-[var(--color-text-muted)]">Promedio por dia</p>
              <p class="mt-2 text-2xl font-semibold text-[var(--color-primary)]">{averageMinutesPerDay} min</p>
            </div>
            <div class="rounded-[20px] border border-[color:var(--color-border)] bg-[rgba(255,255,255,0.02)] p-3">
              <p class="text-xs text-[var(--color-text-muted)]">Paginas leidas</p>
              <p class="mt-2 text-2xl font-semibold text-[var(--color-primary)]">{totalPagesRead.toLocaleString("es-CO")}</p>
            </div>
          </div>
        </div>
      </article>
    </div>
  {/if}
</section>
