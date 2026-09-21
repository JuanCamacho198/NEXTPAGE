<script module lang="ts">
  import House from 'lucide-svelte/icons/house';
  import Library from 'lucide-svelte/icons/library';
  import ChartColumn from 'lucide-svelte/icons/chart-column';
  import Highlighter from 'lucide-svelte/icons/highlighter';
  import Settings from 'lucide-svelte/icons/settings';
  import Book from 'lucide-svelte/icons/book';
  import Search from 'lucide-svelte/icons/search';
  import LayoutGrid from 'lucide-svelte/icons/layout-grid';
  import BookOpen from 'lucide-svelte/icons/book-open';
  import BookText from 'lucide-svelte/icons/book-text';
  import Calendar from 'lucide-svelte/icons/calendar';
  import CalendarPlus from 'lucide-svelte/icons/calendar-plus';
  import Check from 'lucide-svelte/icons/check';
  import ChevronLeft from 'lucide-svelte/icons/chevron-left';
  import ChevronRight from 'lucide-svelte/icons/chevron-right';
  import Clock from 'lucide-svelte/icons/clock';
  import Copy from 'lucide-svelte/icons/copy';
  import EllipsisVertical from 'lucide-svelte/icons/ellipsis-vertical';
  import Filter from 'lucide-svelte/icons/filter';
  import Flame from 'lucide-svelte/icons/flame';
  import Hand from 'lucide-svelte/icons/hand';
  import Info from 'lucide-svelte/icons/info';
  import List from 'lucide-svelte/icons/list';
  import Minus from 'lucide-svelte/icons/minus';
  import Moon from 'lucide-svelte/icons/moon';
  import Plus from 'lucide-svelte/icons/plus';
  import Quote from 'lucide-svelte/icons/quote';
  import Square from 'lucide-svelte/icons/square';
  import SquarePen from 'lucide-svelte/icons/square-pen';
  import SquareStack from 'lucide-svelte/icons/square-stack';
  import TrendingUp from 'lucide-svelte/icons/trending-up';
  import Trash2 from 'lucide-svelte/icons/trash-2';
  import type { Component } from 'svelte';
  export type IconName =
    | 'home'
    | 'library'
    | 'stats'
    | 'highlights'
    | 'settings'
    | 'book'
    | 'check'
    | 'clock'
    | 'trend-up'
    | 'search'
    | 'moon'
    | 'chevron-left'
    | 'chevron-right'
    | 'copy'
    | 'edit'
    | 'trash'
    | 'more-vertical'
    | 'grid'
    | 'list'
    | 'more-dot'
    | 'note'
    | 'add'
    | 'filter'
    | 'info'
    | 'calendar'
    | 'hand'
    | 'minimize'
    | 'maximize'
    | 'restore'
    | 'flame'
    | 'book-open'
    | 'book-text'
    | 'quote'
    | 'calendar-plus';

  export const LUCIDE_BY_NAME: Record<IconName, Component> = {
    home: House as unknown as Component,
    library: Library as unknown as Component,
    stats: ChartColumn as unknown as Component,
    highlights: Highlighter as unknown as Component,
    settings: Settings as unknown as Component,
    book: Book as unknown as Component,
    search: Search as unknown as Component,
    grid: LayoutGrid as unknown as Component,
    check: Check as unknown as Component,
    clock: Clock as unknown as Component,
    'trend-up': TrendingUp as unknown as Component,
    moon: Moon as unknown as Component,
    'chevron-left': ChevronLeft as unknown as Component,
    'chevron-right': ChevronRight as unknown as Component,
    copy: Copy as unknown as Component,
    edit: SquarePen as unknown as Component,
    trash: Trash2 as unknown as Component,
    'more-vertical': EllipsisVertical as unknown as Component,
    list: List as unknown as Component,
    'more-dot': EllipsisVertical as unknown as Component,
    note: SquarePen as unknown as Component,
    add: Plus as unknown as Component,
    filter: Filter as unknown as Component,
    info: Info as unknown as Component,
    calendar: Calendar as unknown as Component,
    hand: Hand as unknown as Component,
    minimize: Minus as unknown as Component,
    maximize: Square as unknown as Component,
    restore: SquareStack as unknown as Component,
    flame: Flame as unknown as Component,
    'book-open': BookOpen as unknown as Component,
    'book-text': BookText as unknown as Component,
    quote: Quote as unknown as Component,
    'calendar-plus': CalendarPlus as unknown as Component,
  };

  const LUCIDE_SIZES = {
    sm: 14,
    md: 16,
    lg: 20,
  } as const;
</script>

<script lang="ts">
  type Props = {
    name: IconName;
    size?: 'sm' | 'md' | 'lg';
    title?: string;
    class?: string;
  };

  let { name, size = 'md', title, class: className = '' }: Props = $props();

  const LucideIcon = $derived(LUCIDE_BY_NAME[name]);

  const sizeClasses = {
    sm: 'h-3.5 w-3.5',
    md: 'h-4 w-4',
    lg: 'h-5 w-5',
  };

  const paths: Record<IconName, string> = {
    home: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6',
    library:
      'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253',
    stats:
      'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z',
    highlights:
      'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
    settings:
      'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z',
    book: 'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253',
    check: 'M5 13l4 4L19 7',
    clock: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z',
    'trend-up': 'M13 7h8m0 0v8m0-8l-8 8-4-4-6 6',
    search: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z',
    moon: 'M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z',
    'chevron-left': 'M15 19l-7-7 7-7',
    'chevron-right': 'M9 5l7 7-7 7',
    copy: 'M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z',
    edit: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
    trash:
      'M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16',
    'more-vertical':
      'M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z',
    grid: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
    list: 'M4 6h16M4 10h16M4 14h16M4 18h16',
    'more-dot':
      'M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z',
    note: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
    add: 'M12 4v16m8-8H4',
    filter:
      'M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z',
    info: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
    calendar:
      'M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 012 2v14a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2z',
    hand: 'M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15',
    minimize: 'M5 12h14',
    maximize: 'M5 5h14v14H5z',
    restore: 'M5 5h11v11H5zM8 8h11v11H8z',
    flame:
      'M8.5 14.5A2.5 2.5 0 0012 17a2.5 2.5 0 003.5-3.5c0-1.5-1.5-2.5-2.5-4 0 0-1 1.2-1 2.5a1 1 0 01-2 0c0-1.3-1-2.5-1-2.5-.8 1.2-2 2.5-2 4z M12 2C8.5 4.5 7 7 7 9.5c0 1.5.5 3 2 4.5',
    'book-open':
      'M12 7v14M3 18a1 1 0 01-1-1V4a1 1 0 011-1h5a4 4 0 014 4 4 4 0 014-4h5a1 1 0 011 1v13a1 1 0 01-1 1h-6a3 3 0 00-3 3 3 3 0 00-3-3z',
    'book-text':
      'M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20 M8 11h8 M8 7h6',
    quote:
      'M16 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z M5 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z',
    'calendar-plus':
      'M8 2v4M16 2v4M3 10h18M8 2h8a2 2 0 012 2v14a2 2 0 01-2 2H8a2 2 0 01-2-2V4a2 2 0 012-2zM16 14h-6M13 11v6',
  };
</script>

<div class="group relative inline-flex" class:has-tooltip={!!title}>
  {#if LucideIcon}
    <LucideIcon
      size={LUCIDE_SIZES[size]}
      strokeWidth={1.8}
      class="{sizeClasses[size]} {className}"
      aria-hidden="true"
    />
  {:else}
    <svg
      class="{sizeClasses[size]} {className}"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  {/if}
  {#if title}
    <span
      class="absolute bottom-full left-1/2 -translate-x-1/2 translate-y-1
             bg-(--color-surface) text-(--color-primary) p-1 rounded text-xs
             whitespace-nowrap opacity-0 invisible transition-all duration-150 ease
             pointer-events-none z-50 shadow-[0_2px_8px_rgba(0,0,0,0.2)]
             group-hover:opacity-100 group-hover:visible group-hover:translate-y-0
             after:absolute after:top-full after:left-1/2 after:-translate-x-1/2
             after:border-4 after:border-transparent after:border-t-(--color-surface)"
      role="tooltip">{title}</span
    >
  {/if}
</div>
