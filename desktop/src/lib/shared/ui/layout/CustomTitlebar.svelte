<script lang="ts">
  import { titlebarState } from '$lib/stores/titlebarState.svelte';
  import { Icon } from '$lib/shared/ui';
  import { onMount } from 'svelte';

  let { hidden = false }: { hidden?: boolean } = $props();

  onMount(() => {
    titlebarState.init();
    return () => titlebarState.destroy();
  });
</script>

<div class="flex h-9 items-center px-4 select-none" class:hidden={hidden}>
  <!-- Left: branding -->
  <span class="font-bold text-sm text-(--color-accent-blue) leading-none">NP</span>
  <span class="ml-2 text-sm font-medium text-(--color-primary) leading-none tracking-tight">NextPage</span>

  <!-- Center: drag region -->
  <div class="flex-1 h-full" data-tauri-drag-region></div>

  <!-- Right: window controls -->
  <button
    onclick={titlebarState.handleMinimize}
    class="w-11 h-9 flex items-center justify-center border-none text-(--color-text-secondary) hover:bg-(--color-surface-hover) cursor-pointer transition-colors"
    aria-label="Minimize"
  >
    <Icon name="minimize" size="md" />
  </button>
  <button
    onclick={titlebarState.handleMaximize}
    class="w-11 h-9 flex items-center justify-center border-none text-(--color-text-secondary) hover:bg-(--color-surface-hover) cursor-pointer transition-colors"
    aria-label="Maximize"
  >
    <Icon name={titlebarState.isMaximized ? 'restore' : 'maximize'} size="md" />
  </button>
  <button
    onclick={titlebarState.handleClose}
    class="w-11 h-9 flex items-center justify-center border-none text-(--color-text-secondary) hover:bg-(--color-error) hover:text-white cursor-pointer transition-colors"
    aria-label="Close"
  >
    <Icon name="close" size="md" />
  </button>
</div>
