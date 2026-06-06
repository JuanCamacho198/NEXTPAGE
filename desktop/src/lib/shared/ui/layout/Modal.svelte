<script lang="ts">
  import type { Snippet } from "svelte";
  import { createFocusTrap } from "$lib/shared/utils/focusTrap";

  import { fly, fade } from "svelte/transition";

  type Props = {
    open: boolean;
    title: string;
    children?: Snippet;
    footer?: Snippet;
    size?: "sm" | "md" | "lg";
    noCloseButton?: boolean;
    class?: string;
  };

  let {
    open = $bindable(false),
    title,
    children,
    footer,
    size = "md",
    noCloseButton = false,
    class: className = ""
  }: Props = $props();

  let dialogEl: HTMLDivElement | undefined = $state();

  const sizeClass = $derived(
    size === "sm" ? "max-w-sm" : size === "lg" ? "max-w-2xl" : "max-w-lg"
  );

  const handleBackdropClick = (e: MouseEvent): void => {
    if (e.target === e.currentTarget) {
      open = false;
    }
  };

  const handleKeydown = (e: KeyboardEvent): void => {
    if (e.key === "Escape") {
      open = false;
    }
  };

  // Focus trap: trap Tab focus inside the dialog when open
  $effect(() => {
    if (open && dialogEl) {
      const trap = createFocusTrap(dialogEl);
      trap.activate();
      return () => trap.deactivate();
    }
  });
</script>

<svelte:window onkeydown={handleKeydown} />

{#if open}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    onclick={handleBackdropClick}
    onkeydown={handleKeydown}
    role="presentation"
    transition:fade={{ duration: 200 }}
  >
    <div 
      bind:this={dialogEl}
      class="w-full {sizeClass} rounded-xl border border-(--color-border) bg-(--color-surface) shadow-xl {className}"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      transition:fly={{ duration: 200, opacity: 0, y: -20 }}
    >
      <div class="flex items-center justify-between border-b border-(--color-border) px-6 py-4">
        <h2 id="modal-title" class="text-lg font-semibold text-(--color-primary)">{title}</h2>
        {#if !noCloseButton}
          <button
            class="flex items-center justify-center min-w-7 min-h-7 text-(--color-text-muted) transition-colors hover:text-(--color-primary)"
            onclick={() => (open = false)}
            aria-label="Close"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        {/if}
      </div>

      <div class="px-6 py-4">
        {#if children}
          {@render children()}
        {/if}
      </div>

      {#if footer}
        <div class="flex items-center justify-end gap-3 border-t border-(--color-border) px-6 py-4">
          {@render footer()}
        </div>
      {/if}
    </div>
  </div>
{/if}