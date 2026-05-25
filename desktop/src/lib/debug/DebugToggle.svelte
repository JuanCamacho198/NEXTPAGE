<script lang="ts">
  import { debugState } from "./debugState.svelte";
</script>

<button
  type="button"
  class="fixed bottom-4 right-4 z-[9999] flex h-10 w-10 items-center justify-center rounded-full border shadow-lg transition-colors"
  class:bg-(--color-primary)={debugState.enabled}
  class:bg-(--color-surface)={!debugState.enabled}
  class:text-white={debugState.enabled}
  class:text-(--color-primary)={!debugState.enabled}
  class:border-(--color-primary)={debugState.enabled}
  class:border-(--color-border)={!debugState.enabled}
  onclick={() => (debugState.enabled = !debugState.enabled)}
  aria-label="Toggle debug panel"
>
  <span class="text-lg leading-none">&#x1F41B;</span>
</button>
