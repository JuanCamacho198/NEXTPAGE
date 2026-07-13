<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';
  import { i18n } from '$lib/shared/i18n';

  let locale = $state(i18n?.DEFAULT_LOCALE ?? 'es');
  $effect(() => {
    if (!i18n?.locale) return;
    const unsub = i18n.locale.subscribe((l) => { locale = l; });
    return () => unsub();
  });
  const tFn = (key: MessageKey): string => i18n?.t?.(locale, key) ?? key;

  type Props = {
    current?: number;
    total?: number;
    visible?: number;
    onchange?: (detail: { page: number }) => void;
  };

  let { current = $bindable(1), total = 1, visible = 5, onchange }: Props = $props();

  function goTo(page: number): void {
    if (page >= 1 && page <= total) {
      current = page;
      onchange?.({ page });
    }
  }

  const pages = $derived.by(() => {
    const result: (number | '...')[] = [];
    const half = Math.floor(visible / 2);
    let start = Math.max(1, current - half);
    let end = Math.min(total, start + visible - 1);

    if (end - start < visible - 1) {
      start = Math.max(1, end - visible + 1);
    }

    for (let i = start; i <= end; i++) {
      result.push(i);
    }

    if (start > 1) {
      result.unshift('...');
    }
    if (end < total) {
      result.push('...');
    }

    return result;
  });
</script>

<nav class="flex items-center gap-1">
  <button
    type="button"
    class="rounded-md border border-(--color-border) bg-(--color-surface) px-3 py-1.5 text-sm text-(--color-primary) hover:bg-(--color-surface-hover) disabled:cursor-not-allowed disabled:opacity-50"
    onclick={() => goTo(current - 1)}
    disabled={current <= 1}
    aria-label={tFn('pagination.prevAria')}
  >
    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
    </svg>
  </button>

  {#each pages as page}
    {#if page === '...'}
      <span class="px-2 text-(--color-text-muted)">...</span>
    {:else}
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm transition-colors
          {page === current
          ? 'bg-(--color-primary) text-(--color-background)'
          : 'text-(--color-primary) hover:bg-(--color-surface-hover)'}"
        onclick={() => goTo(page)}
      >
        {page}
      </button>
    {/if}
  {/each}

  <button
    type="button"
    class="rounded-md border border-(--color-border) bg-(--color-surface) px-3 py-1.5 text-sm text-(--color-primary) hover:bg-(--color-surface-hover) disabled:cursor-not-allowed disabled:opacity-50"
    onclick={() => goTo(current + 1)}
    disabled={current >= total}
    aria-label={tFn('pagination.nextAria')}
  >
    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
    </svg>
  </button>
</nav>
