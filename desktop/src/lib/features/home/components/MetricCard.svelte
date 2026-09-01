<script lang="ts">
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import type { IconName } from '$lib/shared/ui/navigation/Icon.svelte';

  type Props = {
    label: string;
    value: string;
    icon: IconName;
    progress?: number;
    isLoading?: boolean;
    disabledReason?: string | null;
  };

  let { label, value, icon, progress, isLoading = false, disabledReason = null }: Props = $props();

  const progressPct = $derived(
    progress !== undefined ? Math.round(Math.min(1, Math.max(0, progress)) * 100) : null,
  );
  const showProgress = $derived(progressPct !== null && !isLoading && !disabledReason);
</script>

<div
  class="rounded-(--radius-xl) border border-(--color-border) bg-(--color-surface) p-5"
  aria-busy={isLoading || undefined}
>
  <div class="flex items-start justify-between">
    <div class="min-w-0">
      <p class="text-xs uppercase tracking-wider text-(--color-text-muted)">{label}</p>
      {#if disabledReason}
        <p class="mt-2 text-sm text-(--color-text-muted)">{disabledReason}</p>
      {:else if isLoading}
        <div class="mt-2 h-8 w-16 animate-pulse rounded bg-(--color-border)"></div>
      {:else}
        <p class="mt-1 text-3xl font-semibold text-(--color-primary)">{value}</p>
      {/if}
    </div>
    <div
      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-(--color-accent-soft) text-(--color-accent)"
    >
      <Icon name={icon} size="md" />
    </div>
  </div>

  {#if showProgress}
    <div
      class="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-(--color-border)"
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={progressPct}
    >
      <div class="h-full rounded-full bg-(--color-accent)" style="width: {progressPct}%"></div>
    </div>
  {/if}
</div>
