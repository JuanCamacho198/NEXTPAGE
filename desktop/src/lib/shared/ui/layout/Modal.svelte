<script lang="ts">
  import type { Snippet } from 'svelte';
  import { createFocusTrap } from '$lib/shared/utils/focusTrap';
  import type { MessageKey } from '$lib/shared/i18n';
  import { i18n } from '$lib/shared/i18n';

  import { fly, fade } from 'svelte/transition';

  let locale = $state(i18n?.DEFAULT_LOCALE ?? 'es');
  $effect(() => {
    if (!i18n?.locale) return;
    const unsub = i18n.locale.subscribe((l) => {
      locale = l;
    });
    return () => unsub();
  });
  const tFn = (key: MessageKey): string => i18n?.t?.(locale, key) ?? key;

  type Props = {
    open: boolean;
    title: string;
    children?: Snippet;
    footer?: Snippet;
    size?: 'sm' | 'md' | 'lg' | 'xl';
    noCloseButton?: boolean;
    class?: string;
  };

  let {
    open = $bindable(false),
    title,
    children,
    footer,
    size = 'md',
    noCloseButton = false,
    class: className = '',
  }: Props = $props();

  let dialogEl: HTMLDivElement | undefined = $state();

  const sizeClass = $derived(
    size === 'sm'
      ? 'max-w-sm'
      : size === 'lg'
        ? 'max-w-2xl'
        : size === 'xl'
          ? 'max-w-4xl'
          : 'max-w-lg',
  );

  const handleBackdropClick = (e: MouseEvent): void => {
    if (e.target === e.currentTarget) {
      open = false;
    }
  };

  const handleKeydown = (e: KeyboardEvent): void => {
    if (e.key === 'Escape') {
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
      class="flex max-h-[calc(100vh-2rem)] w-full {sizeClass} flex-col overflow-hidden rounded-xl border border-(--color-border) bg-(--color-elevated) shadow-xl {className}"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      transition:fly={{ duration: 200, opacity: 0, y: -20 }}
    >
      <div
        class="flex shrink-0 items-center justify-between border-b border-(--color-border) px-6 py-4"
      >
        <h2 id="modal-title" class="text-lg font-semibold text-(--color-primary)">{title}</h2>
        {#if !noCloseButton}
          <button
            class="flex items-center justify-center min-w-7 min-h-7 text-(--color-text-muted) transition-colors hover:text-(--color-primary)"
            onclick={() => (open = false)}
            aria-label={tFn('modal.closeAria')}
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        {/if}
      </div>

      <!--
        The body is the single scroll region: the dialog is height-bounded and
        flex-column, so an over-tall body scrolls here while the header and
        footer stay pinned. `tabindex="0"` + `role="region"` keep the scroll
        region reachable by keyboard.
      -->
      <!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
      <div
        class="min-h-0 flex-1 overflow-y-auto px-6 py-4"
        tabindex="0"
        role="region"
        aria-label={title}
      >
        {#if children}
          {@render children()}
        {/if}
      </div>

      {#if footer}
        <div
          class="flex shrink-0 items-center justify-end gap-3 border-t border-(--color-border) px-6 py-4"
        >
          {@render footer()}
        </div>
      {/if}
    </div>
  </div>
{/if}
