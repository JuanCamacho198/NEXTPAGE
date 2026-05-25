<script lang="ts">
  import type { MessageKey } from "$lib/i18n";

  type Props = {
    toc: Array<{ id: string; label: string; href: string }>;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onNavigate: (chapter: { id: string; label: string; href: string }) => void;
  };

  let { toc, t, onNavigate }: Props = $props();
</script>

<aside class="w-60 bg-[var(--color-surface)] border-r border-[var(--color-border)] overflow-y-auto shrink-0">
  <h3 class="px-3 py-3 text-sm font-semibold border-b border-[var(--color-border)] m-0 text-[var(--color-primary)]">
    {t("epub.tableOfContents")}
  </h3>
  <ul class="list-none m-0 p-0">
    {#each toc as chapter}
      <li>
        <button
          type="button"
          onclick={() => onNavigate(chapter)}
          class="w-full px-3 py-2.5 border-none bg-transparent text-left cursor-pointer text-xs leading-[1.4] break-words text-[var(--color-primary)] hover:bg-[color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]"
        >
          {chapter.label}
        </button>
      </li>
    {/each}
  </ul>
</aside>
