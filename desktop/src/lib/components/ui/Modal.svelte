<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    open: boolean;
    title: string;
    children?: Snippet;
    footer?: Snippet;
    class?: string;
  };

  let {
    open = $bindable(false),
    title,
    children,
    footer,
    class: className = ""
  }: Props = $props();

  const handleBackdropClick = (e: MouseEvent) => {
    if (e.target === e.currentTarget) {
      open = false;
    }
  };

  const handleKeydown = (e: KeyboardEvent) => {
    if (e.key === "Escape") {
      open = false;
    }
  };
</script>

<svelte:window on:keydown={handleKeydown} />

{#if open}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    onclick={handleBackdropClick}
    onkeydown={handleKeydown}
    role="presentation"
  >
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div 
      class="w-full max-w-lg rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xl {className}"
      onkeydown={handleKeydown}
    >
      <div class="flex items-center justify-between border-b border-[var(--color-border)] px-6 py-4">
        <h2 id="modal-title" class="text-lg font-semibold text-[var(--color-primary)]">{title}</h2>
        <button
          class="text-[var(--color-muted)] transition-colors hover:text-[var(--color-primary)]"
          onclick={() => (open = false)}
          aria-label="Close"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div class="px-6 py-4">
        {#if children}
          {@render children()}
        {/if}
      </div>

      {#if footer}
        <div class="flex items-center justify-end gap-3 border-t border-[var(--color-border)] px-6 py-4">
          {@render footer()}
        </div>
      {/if}
    </div>
  </div>
{/if}