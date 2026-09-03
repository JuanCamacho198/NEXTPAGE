<script lang="ts">
  import type { Snippet } from 'svelte';

  type Props = {
    trigger: Snippet;
    children?: Snippet;
    position?: 'bottom-left' | 'bottom-right';
  };

  let { trigger, children, position = 'bottom-right' }: Props = $props();

  let isOpen = $state(false);
  let containerEl = $state<HTMLDivElement | undefined>();

  function toggle(): void {
    isOpen = !isOpen;
  }

  function close(): void {
    isOpen = false;
  }

  // Click outside handler using $effect instead of use:handleClickOutside
  $effect(() => {
    if (!containerEl) return;
    const handle = (e: MouseEvent): void => {
      if (containerEl && !containerEl.contains(e.target as Node)) {
        isOpen = false;
      }
    };
    document.addEventListener('click', handle, true);
    return () => document.removeEventListener('click', handle, true);
  });

  // Close on Escape while the menu is open
  $effect(() => {
    if (!isOpen) return;
    const handle = (e: KeyboardEvent): void => {
      if (e.key === 'Escape') {
        isOpen = false;
      }
    };
    window.addEventListener('keydown', handle);
    return () => window.removeEventListener('keydown', handle);
  });
</script>

<div bind:this={containerEl} class="relative inline-block">
  <!-- The trigger snippet owns its own interaction semantics (every usage
       renders a <button>); the wrapper only forwards clicks so mouse and
       keyboard activation both open the menu without creating a nested
       interactive element. -->
  <div onclickcapture={toggle}>
    {@render trigger()}
  </div>

  {#if isOpen}
    <!-- Capture-phase close: runs before the clicked item's own handler,
         so selecting an action dismisses the menu. -->
    <div
      onclickcapture={close}
      class="absolute z-10 mt-2 w-56 rounded-md bg-(--color-elevated) shadow-lg ring-1 ring-(--color-border) focus:outline-none
				{position === 'bottom-right' ? 'right-0' : 'left-0'}"
    >
      <div class="py-1">
        {@render children?.()}
      </div>
    </div>
  {/if}
</div>
