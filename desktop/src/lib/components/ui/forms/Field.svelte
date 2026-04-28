<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    label: string;
    error?: string;
    id: string;
    input?: Snippet;
    class?: string;
  };

  let {
    label,
    error,
    id,
    input,
    class: className = ""
  }: Props = $props();
</script>

<div class="flex flex-col gap-1.5 {className}">
  <label for={id} class="text-sm font-medium text-[var(--color-primary)]">
    {label}
  </label>

  <div class="field-input">
    {#if input}
      {@render input()}
    {/if}
  </div>

  {#if error}
    <p class="text-sm text-[var(--color-error)]">{error}</p>
  {/if}
</div>