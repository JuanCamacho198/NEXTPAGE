<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    title: string;
    actions?: Snippet;
    class?: string;
  };

  let {
    title,
    actions,
    class: className = ""
  }: Props = $props();
</script>

<div class="flex items-center justify-between {className}">
  <h2 class="text-xl font-semibold text-[var(--color-primary)]">{title}</h2>

  {#if actions}
    <div class="actions flex items-center gap-2">
      {@render actions()}
    </div>
  {/if}
</div>