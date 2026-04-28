<script lang="ts">
  type Props = {
    value?: number;
    showLabel?: boolean;
    class?: string;
  };

  let {
    value = $bindable(0),
    showLabel = false,
    class: className = "",
  }: Props = $props();

  const percentage = $derived(Math.min(100, Math.max(0, value)));
</script>

<div class="flex items-center gap-2 {className}">
  {#if showLabel}
    <span class="text-xs text-[var(--color-text-muted)]">{percentage}%</span>
  {/if}
  <div class="h-1.5 w-full overflow-hidden rounded bg-[color:var(--color-border)]">
    <div
      class="h-full rounded bg-[var(--color-primary)] transition-all"
      style="width: {percentage}%"
    ></div>
  </div>
</div>