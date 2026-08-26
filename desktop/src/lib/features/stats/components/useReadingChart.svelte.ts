export type HoveredPoint = { label: string; value: number } | null;
export type TooltipPos = { x: number; y: number } | null;
export type GenreTooltip = {
  genre: string;
  minutes: number;
  percent: number;
  books: string[];
} | null;

export function useReadingChart(): {
  hoveredPoint: HoveredPoint;
  tooltipPos: TooltipPos;
  genreTooltip: GenreTooltip;
  genreTooltipPos: TooltipPos;
  chartModalOpen: boolean;
  openModal: () => void;
  closeModal: () => void;
  handlePointEnter: (
    point: { label: string; value: number },
    event: MouseEvent,
    container: HTMLElement | null,
  ) => void;
  handlePointMove: (event: MouseEvent, container: HTMLElement | null) => void;
  handlePointLeave: (event?: Event) => void;
  handlePointFocus: (
    point: { label: string; value: number },
    circleEl: SVGCircleElement,
    container: HTMLElement | null,
  ) => void;
  handlePointBlur: (circleEl: SVGCircleElement) => void;
  handleGenreEnter: (
    entry: { genre: string; minutes: number; percent: number },
    books: string[],
    event: MouseEvent,
    container: HTMLElement | null,
  ) => void;
  handleGenreMove: (event: MouseEvent, container: HTMLElement | null) => void;
  handleGenreLeave: () => void;
  handleGenreFocus: (
    entry: { genre: string; minutes: number; percent: number },
    books: string[],
    el: HTMLElement,
    container: HTMLElement | null,
  ) => void;
} {
  let hoveredPoint = $state<HoveredPoint>(null);
  let tooltipPos = $state<TooltipPos>(null);
  let genreTooltip = $state<GenreTooltip>(null);
  let genreTooltipPos = $state<TooltipPos>(null);
  let chartModalOpen = $state(false);

  function openModal(): void {
    chartModalOpen = true;
  }
  function closeModal(): void {
    chartModalOpen = false;
  }

  function handlePointEnter(
    point: { label: string; value: number },
    event: MouseEvent,
    container: HTMLElement | null,
  ): void {
    hoveredPoint = { label: point.label, value: point.value };
    if (container) {
      const rect = container.getBoundingClientRect();
      tooltipPos = { x: event.clientX - rect.left, y: event.clientY - rect.top };
    } else {
      // fallback: use client coords directly (matches original closest article fallback)
      tooltipPos = { x: event.clientX, y: event.clientY };
    }
    const circle = event.currentTarget as SVGCircleElement | null;
    if (circle) circle.style.r = '7';
  }

  function handlePointMove(event: MouseEvent, container: HTMLElement | null): void {
    if (!hoveredPoint || !container) return;
    const rect = container.getBoundingClientRect();
    tooltipPos = { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  function handlePointLeave(event?: Event): void {
    if (event?.currentTarget) {
      const circle = event.currentTarget as SVGCircleElement;
      try {
        circle.style.r = '5';
      } catch {}
    }
    hoveredPoint = null;
    tooltipPos = null;
  }

  function handlePointFocus(
    point: { label: string; value: number },
    circleEl: SVGCircleElement,
    container: HTMLElement | null,
  ): void {
    hoveredPoint = { label: point.label, value: point.value };
    circleEl.style.r = '7';
    const ib = circleEl.getBoundingClientRect();
    if (container) {
      const ab = container.getBoundingClientRect();
      tooltipPos = { x: ib.left - ab.left + 14, y: ib.top - ab.top - 30 };
    }
  }

  function handlePointBlur(circleEl: SVGCircleElement): void {
    circleEl.style.r = '5';
    hoveredPoint = null;
    tooltipPos = null;
  }

  function handleGenreEnter(
    entry: { genre: string; minutes: number; percent: number },
    books: string[],
    event: MouseEvent,
    container: HTMLElement | null,
  ): void {
    genreTooltip = { genre: entry.genre, minutes: entry.minutes, percent: entry.percent, books };
    if (container) {
      const rect = container.getBoundingClientRect();
      genreTooltipPos = { x: event.clientX - rect.left, y: event.clientY - rect.top };
    }
  }

  function handleGenreMove(event: MouseEvent, container: HTMLElement | null): void {
    if (!genreTooltip || !container) return;
    const rect = container.getBoundingClientRect();
    genreTooltipPos = { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  function handleGenreLeave(): void {
    genreTooltip = null;
    genreTooltipPos = null;
  }

  function handleGenreFocus(
    entry: { genre: string; minutes: number; percent: number },
    books: string[],
    el: HTMLElement,
    container: HTMLElement | null,
  ): void {
    genreTooltip = { genre: entry.genre, minutes: entry.minutes, percent: entry.percent, books };
    if (container) {
      const ab = container.getBoundingClientRect();
      const ib = el.getBoundingClientRect();
      genreTooltipPos = { x: ib.left - ab.left + 14, y: ib.top - ab.top - 38 };
    }
  }

  return {
    get hoveredPoint(): HoveredPoint {
      return hoveredPoint;
    },
    set hoveredPoint(v: HoveredPoint) {
      hoveredPoint = v;
    },
    get tooltipPos(): TooltipPos {
      return tooltipPos;
    },
    set tooltipPos(v: TooltipPos) {
      tooltipPos = v;
    },
    get genreTooltip(): GenreTooltip {
      return genreTooltip;
    },
    set genreTooltip(v: GenreTooltip) {
      genreTooltip = v;
    },
    get genreTooltipPos(): TooltipPos {
      return genreTooltipPos;
    },
    set genreTooltipPos(v: TooltipPos) {
      genreTooltipPos = v;
    },
    get chartModalOpen(): boolean {
      return chartModalOpen;
    },
    set chartModalOpen(v: boolean) {
      chartModalOpen = v;
    },
    openModal,
    closeModal,
    handlePointEnter,
    handlePointMove,
    handlePointLeave,
    handlePointFocus,
    handlePointBlur,
    handleGenreEnter,
    handleGenreMove,
    handleGenreLeave,
    handleGenreFocus,
  };
}

export type ReadingChartState = ReturnType<typeof useReadingChart>;
