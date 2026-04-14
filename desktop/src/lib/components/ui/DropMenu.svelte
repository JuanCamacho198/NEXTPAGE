<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    trigger: Snippet;
    children?: Snippet;
    position?: "bottom-left" | "bottom-right";
  };

  let { trigger, children, position = "bottom-right" }: Props = $props();

  let isOpen = $state(false);

  function toggle() {
    isOpen = !isOpen;
  }

  function handleClickOutside(node: HTMLElement) {
    const handle = (e: MouseEvent) => {
      if (node && !node.contains(e.target as Node)) {
        isOpen = false;
      }
    };
    document.addEventListener("click", handle, true);
    return {
      destroy() {
        document.removeEventListener("click", handle, true);
      }
    };
  }
</script>

<div class="relative inline-block" use:handleClickOutside>
  <div role="button" tabindex="0" onclick={toggle} onkeydown={(e) => e.key === 'Enter' && toggle()}>
    {@render trigger()}
  </div>
  
  {#if isOpen}
    <div
      class="absolute z-10 mt-2 w-56 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none 
      {position === 'bottom-right' ? 'right-0' : 'left-0'}"
    >
      <div class="py-1">
        {@render children?.()}
      </div>
    </div>
  {/if}
</div>