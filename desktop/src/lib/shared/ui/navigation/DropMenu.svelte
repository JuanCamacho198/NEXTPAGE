<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    trigger: Snippet;
    children?: Snippet;
    position?: "bottom-left" | "bottom-right";
  };

  let { trigger, children, position = "bottom-right" }: Props = $props();

  let isOpen = $state(false);
  let containerEl = $state<HTMLDivElement | undefined>();

  function toggle(): void {
    isOpen = !isOpen;
  }

  // Click outside handler using $effect instead of use:handleClickOutside
  $effect(() => {
    if (!containerEl) return;
    const handle = (e: MouseEvent): void => {
      if (containerEl && !containerEl.contains(e.target as Node)) {
        isOpen = false;
      }
    };
    document.addEventListener("click", handle, true);
    return () => document.removeEventListener("click", handle, true);
  });
</script>

<div bind:this={containerEl} class="relative inline-block">
  <div role="button" tabindex="0" onclick={toggle} onkeydown={(e) => e.key === 'Enter' && toggle()}>
    {@render trigger()}
  </div>

  {#if isOpen}
    <div
      class="absolute z-10 mt-2 w-56 rounded-md bg-(--color-surface) shadow-lg ring-1 ring-(--color-border) focus:outline-none
      {position === 'bottom-right' ? 'right-0' : 'left-0'}"
    >
      <div class="py-1">
        {@render children?.()}
      </div>
    </div>
  {/if}
</div>
