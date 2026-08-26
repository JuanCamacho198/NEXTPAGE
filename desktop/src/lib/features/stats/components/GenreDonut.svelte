<script lang="ts">
  import type { ReadingChartState } from './useReadingChart.svelte';

  type Props = {
    distribution: Array<{ genre: string; minutes: number; percent: number; color: string }>;
    booksByGenre: Map<string, string[]>;
    totalMinutes: number;
    containerRef: HTMLElement | null;
    chart: ReadingChartState;
    minutesLabel: string;
  };
  let { distribution, booksByGenre, totalMinutes, containerRef, chart, minutesLabel }: Props =
    $props();
</script>

<div class="flex flex-col items-center gap-6 lg:flex-row lg:items-center lg:justify-between">
  <div
    class="relative h-52 w-52 rounded-full"
    style={`background: conic-gradient(${distribution
      .map((entry, index, array) => {
        const start = array.slice(0, index).reduce((sum, cur) => sum + cur.percent, 0);
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
      <span class="text-xs text-(--color-text-muted)">{minutesLabel}</span>
    </div>
  </div>
  <div class="w-full space-y-3">
    {#each distribution as entry}
      <div
        class="flex cursor-pointer items-center justify-between gap-3 rounded-lg px-2 py-1.5 text-sm transition-colors duration-150 hover:bg-(--color-surface-subtle)"
        role="button"
        tabindex="0"
        aria-label={`${entry.genre}: ${entry.minutes} min, ${entry.percent}%`}
        onmouseenter={(e) =>
          chart.handleGenreEnter(
            entry,
            booksByGenre.get(entry.genre) ?? [],
            e as MouseEvent,
            containerRef,
          )}
        onmousemove={(e) => chart.handleGenreMove(e as MouseEvent, containerRef)}
        onmouseleave={() => chart.handleGenreLeave()}
        onfocus={(e) =>
          chart.handleGenreFocus(
            entry,
            booksByGenre.get(entry.genre) ?? [],
            e.currentTarget as HTMLElement,
            containerRef,
          )}
        onblur={() => chart.handleGenreLeave()}
        onkeydown={(e) => {
          if ((e as KeyboardEvent).key === 'Escape') chart.handleGenreLeave();
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
