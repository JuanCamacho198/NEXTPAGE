<script lang="ts">
  import Panel from "$lib/shared/ui/layout/Panel.svelte";
  import Button from "$lib/shared/ui/forms/Button.svelte";
  import type { MessageKey } from "$lib/shared/i18n";

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { t }: Props = $props();
</script>

<Panel title={t("settings.about")}>
  <div class="rounded-lg border border-(--color-border) bg-(--color-surface) p-4">
    <div class="flex items-center gap-3">
      <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="text-(--color-primary)"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
      <div class="flex flex-col">
        <span class="text-lg font-semibold text-(--color-primary)">NextPage</span>
        <span class="text-xs text-(--color-text-muted)">Version {'0.1.0'}</span>
      </div>
    </div>
    <p class="text-sm text-(--color-text-muted) mt-3">
      A modern e-reader application for enjoying your EPUB collection with a clean, customizable reading experience.
    </p>
  </div>

  <div class="rounded-lg border border-(--color-border) bg-(--color-surface) p-4 mt-4">
    <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">Credits</h4>
    <ul class="m-0 p-0 list-none">
      <li class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0">
        <span class="text-xs text-(--color-text-muted)">Core Team</span>
        <span class="text-xs text-(--color-primary) font-medium">NextPage Contributors</span>
      </li>
      <li class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0">
        <span class="text-xs text-(--color-text-muted)">EPUB Parsing</span>
        <span class="text-xs text-(--color-primary) font-medium">epub.js</span>
      </li>
      <li class="flex justify-between py-1 border-b border-(--color-border) last:border-b-0">
        <span class="text-xs text-(--color-text-muted)">Framework</span>
        <span class="text-xs text-(--color-primary) font-medium">Svelte / Tauri</span>
      </li>
    </ul>
  </div>

  <div class="rounded-lg border border-(--color-border) bg-(--color-surface) p-4 mt-4">
    <h4 class="mt-0 mb-2 text-sm font-semibold text-zinc-900">Links</h4>
    <div class="flex gap-2">
      <Button onclick={() => window.open("https://github.com/anomalyco/nextpage", "_blank")} variant="ghost" size="sm">
        GitHub
      </Button>
      <Button onclick={() => window.open("https://github.com/anomalyco/nextpage/issues", "_blank")} variant="ghost" size="sm">
        Report Issue
      </Button>
    </div>
  </div>
</Panel>
