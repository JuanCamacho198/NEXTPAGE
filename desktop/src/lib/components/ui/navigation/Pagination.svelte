<script lang="ts">
  type Props = {
    current?: number;
    total?: number;
    visible?: number;
    onchange?: (detail: { page: number }) => void;
  };

  let {
    current = $bindable(1),
    total = 1,
    visible = 5,
    onchange,
  }: Props = $props();

  function goTo(page: number) {
    if (page >= 1 && page <= total) {
      current = page;
      onchange?.({ page });
    }
  }

  const pages = $derived.by(() => {
    const result: (number | "...")[] = [];
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
      result.unshift("...");
    }
    if (end < total) {
      result.push("...");
    }

    return result;
  });
</script>

<nav class="flex items-center gap-1">
  <button
    type="button"
    class="rounded-md border border-[color:var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)] disabled:cursor-not-allowed disabled:opacity-50"
    onclick={() => goTo(current - 1)}
    disabled={current <= 1}
    aria-label="Previous page"
  >
    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="M15 19l-7-7 7-7"
      />
    </svg>
  </button>

  {#each pages as page}
    {#if page === "..."}
      <span class="px-2 text-[var(--color-text-muted)]">...</span>
    {:else}
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm transition-colors
          {page === current
          ? 'bg-[var(--color-primary)] text-[var(--color-background)]'
          : 'text-[var(--color-primary)] hover:bg-[color:var(--color-border)]'}"
        onclick={() => goTo(page)}
      >
        {page}
      </button>
    {/if}
  {/each}

  <button
    type="button"
    class="rounded-md border border-[color:var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)] disabled:cursor-not-allowed disabled:opacity-50"
    onclick={() => goTo(current + 1)}
    disabled={current >= total}
    aria-label="Next page"
  >
    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="M9 5l7 7-7 7"
      />
    </svg>
  </button>
</nav>