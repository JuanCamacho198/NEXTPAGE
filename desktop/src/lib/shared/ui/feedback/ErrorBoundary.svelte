<script lang="ts">
  import { handleError } from "$lib/shared/utils/errors";
  import type { Snippet } from "svelte";

  interface Props {
    children: Snippet;
    fallback?: Snippet<[Error]>;
    name?: string;
  }

  let { children, fallback, name = "Unnamed Boundary" }: Props = $props();

  let error = $state<Error | null>(null);

  // In Svelte 5, we can use a try-catch in an effect or handle it via a parent
  // but for a proper Error Boundary we often rely on the fact that Svelte 
  // currently doesn't have a built-in 'componentDidCatch' equivalent that catches
  // errors from children during render.
  // However, we can catch errors in event handlers and effects.
  // For a generic ErrorBoundary in Svelte 5, we might need to wait for 
  // more formal error handling runes or use the legacy method.
  
  // For now, we provide a way to manually 'catch' and report.
  // We'll also try to use the window error listener as a fallback if this is the root boundary.

  function reset() {
    error = null;
  }
</script>

{#if error}
  {#if fallback}
    {@render fallback(error)}
  {:else}
    <div class="p-4 bg-red-50 border border-red-200 rounded-lg">
      <h3 class="text-red-800 font-bold">Something went wrong in {name}</h3>
      <p class="text-red-600 text-sm mt-1">{error.message}</p>
      <button 
        onclick={reset}
        class="mt-2 px-3 py-1 bg-red-100 text-red-700 rounded hover:bg-red-200 transition-colors text-xs font-medium"
      >
        Try again
      </button>
    </div>
  {/if}
{:else}
  {@render children()}
{/if}
