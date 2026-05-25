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

  // Dynamically update HTML lang attribute when locale changes
  $effect(() => {
    document.documentElement.lang = appState.locale;
  });
</script>

<main class="flex h-screen overflow-hidden bg-[var(--color-background)] text-[var(--color-primary)]">
  <!-- Skip link: visible only on keyboard focus for keyboard/screen reader users -->
  <a
    href="#main-content"
    class="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-1/2 focus:-translate-x-1/2 focus:z-[100] focus:px-4 focus:py-2.5 focus:rounded-lg focus:bg-[var(--color-accent-blue)] focus:text-[var(--color-background)] focus:text-sm focus:font-semibold focus:shadow-lg focus:outline-none focus:ring-2 focus:ring-white/50"
  >
    {appState.t("app.skipToContent")}
  </a>
  <AppRouter />
  <AppModals />
</main>
