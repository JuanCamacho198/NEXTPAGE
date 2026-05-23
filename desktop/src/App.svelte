<script lang="ts">
  import { onMount } from "svelte";
  import { debugState } from "./lib/debug/debugState.svelte";
  import { appState } from "$lib/stores/AppState.svelte";
  import AppRouter from "./lib/components/layout/AppRouter.svelte";
  import AppModals from "./lib/components/layout/AppModals.svelte";

  onMount(() => {
    appState.init();
  });

  $effect(() => {
    debugState.currentRoute = appState.route;
  });
</script>

<main class="flex h-screen overflow-hidden bg-[var(--color-background)] text-[var(--color-primary)]">
  <AppRouter />
  <AppModals />
</main>
