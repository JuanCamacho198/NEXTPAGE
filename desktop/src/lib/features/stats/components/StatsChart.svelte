<script lang="ts">
  import { formatChartLabel, type ChartMeta, type Granularity } from './readingStatsState.svelte';
  import type { ReadingChartState } from './useReadingChart.svelte';

  type Props = {
    chartMeta: ChartMeta;
    granularity: Granularity;
    locale: string;
    size: 'inline' | 'modal';
    containerRef: HTMLElement | null;
    chart: ReadingChartState;
  };
  let { chartMeta, granularity, locale, size, containerRef, chart }: Props = $props();

  const strokeId = $derived(size === 'modal' ? 'lineStrokeModal' : 'lineStroke');
  const fillId = $derived(size === 'modal' ? 'lineFillModal' : 'lineFill');
  const extraHeight = $derived(size === 'modal' ? 320 : 40);
  const labelFontSize = $derived(size === 'modal' ? 11 : 10);
</script>

<div
  class="rounded-[22px] border border-(--color-border) bg-[linear-gradient(180deg,rgba(6,14,24,0.86),rgba(10,18,30,0.94))] p-4 overflow-hidden"
>
  <svg
    role="img"
    aria-label="chart"
    viewBox={`0 0 ${chartMeta.width} ${chartMeta.height + 28}`}
    class="w-full"
    style={`height: ${chartMeta.height + extraHeight}px`}
    onmouseleave={() => chart.handlePointLeave()}
  >
    <defs>
      <linearGradient id={strokeId} x1="0%" x2="100%" y1="0%" y2="0%">
        <stop offset="0%" stop-color="#4e8cff"></stop>
        <stop offset="100%" stop-color="#49d4ff"></stop>
      </linearGradient>
      <linearGradient id={fillId} x1="0%" x2="0%" y1="0%" y2="100%">
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
    <path d={chartMeta.area} fill={`url(#${fillId})`}></path>
    <path
      d={chartMeta.line}
      fill="none"
      stroke={`url(#${strokeId})`}
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
        onmouseenter={(e) => chart.handlePointEnter(point, e as MouseEvent, containerRef)}
        onmousemove={(e) => chart.handlePointMove(e as MouseEvent, containerRef)}
        onmouseleave={(e) => chart.handlePointLeave(e)}
        onfocus={(e) =>
          chart.handlePointFocus(point, e.currentTarget as SVGCircleElement, containerRef)}
        onblur={(e) => chart.handlePointBlur(e.currentTarget as SVGCircleElement)}
        onkeydown={(e) => {
          if ((e as KeyboardEvent).key === 'Escape') {
            chart.handlePointLeave();
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
          font-size={labelFontSize}
          fill="var(--color-text-muted)"
        >
          {formatChartLabel(point.label, granularity, locale)}
        </text>
      {/if}
    {/each}
  </svg>
</div>
