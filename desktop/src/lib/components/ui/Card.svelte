<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    title?: string;
    subtitle?: string;
    clickable?: boolean;
    media?: Snippet;
    default?: Snippet;
    actions?: Snippet;
    onclick?: () => void;
    class?: string;
  };

  let {
    title,
    subtitle,
    clickable = false,
    media,
    default: content,
    actions,
    onclick,
    class: className = ""
  }: Props = $props();

  const baseClasses = "rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] overflow-hidden";

  function handleKeydown(e: KeyboardEvent) {
    if (clickable && (e.key === "Enter" || e.key === " ")) {
      e.preventDefault();
      onclick?.();
    }
  }
</script>

<div class="{baseClasses} {clickable ? 'cursor-pointer transition-all hover:shadow-md hover:border-[var(--color-primary)]' : ''} {className}" onclick={clickable ? onclick : undefined} onkeydown={handleKeydown} role={clickable ? "button" : undefined} tabindex={clickable ? 0 : -1}>
  {#if media}
    <div class="media">
      {@render media()}
    </div>
  {/if}

  <div class="p-4">
    {#if title || subtitle}
      <div class="mb-3">
        {#if title}
          <h3 class="text-lg font-semibold text-[var(--color-primary)]">{title}</h3>
        {/if}
        {#if subtitle}
          <p class="mt-1 text-sm text-[var(--color-muted)]">{subtitle}</p>
        {/if}
      </div>
    {/if}

    {#if content}
      <div class="content">
        {@render content()}
      </div>
    {/if}
  </div>

  {#if actions}
    <div class="flex items-center gap-2 border-t border-[var(--color-border)] px-4 py-3">
      {@render actions()}
    </div>
  {/if}
</div>

<style>
  .media :global(img) {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
</style>