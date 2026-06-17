<script lang="ts">
  import type { MessageKey } from "$lib/shared/i18n";
  import { createFocusTrap } from "$lib/shared/utils/focusTrap";

  export interface TocEntry {
    id: string;
    title: string;
    depth: number;
    active?: boolean;
    pageNumber?: number;
  }

  type Props = {
    open: boolean;
    entries: TocEntry[];
    activeId?: string;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onNavigate: (entry: TocEntry) => void;
    onClose: () => void;
  };

  let { open, entries, activeId, t, onNavigate, onClose }: Props = $props();

  let sidebarEl: HTMLDivElement | undefined = $state();

  function handleBackdropClick(e: MouseEvent): void {
    if (e.target === e.currentTarget) onClose();
  }

  // Focus trap: keep Tab focus inside the sidebar when open
  $effect(() => {
    if (open && sidebarEl) {
      const trap = createFocusTrap(sidebarEl);
      trap.activate();
      return () => trap.deactivate();
    }
  });
</script>

{#if open}
  <div
    class="fixed inset-0 z-40"
    onclick={handleBackdropClick}
    onkeydown={(e) => e.key === "Escape" && onClose()}
    role="presentation"
  >
    <!-- Backdrop -->
    <div class="absolute inset-0 bg-(--color-surface)/70"></div>

    <!-- Sidebar -->
    <div
      bind:this={sidebarEl}
      class="absolute right-0 top-0 flex h-full w-65 flex-col overflow-y-auto border-l border-(--color-border-deep) bg-(--color-surface)/70 pt-15 text-(--color-text-muted) backdrop-blur-sm"
      onkeydown={(e) => e.key === "Escape" && onClose()}
      role="dialog"
      aria-label={t("reader.tabla_contenidos")}
      tabindex="0"
    >
      <!-- Heading -->
      <div class="border-b border-(--color-border)/5 px-5 py-4">
        <h2 class="text-base font-bold text-(--color-primary)">{t("reader.tabla_contenidos")}</h2>
      </div>

      <!-- Chapter List -->
      {#if entries.length === 0}
        <div class="flex flex-1 items-center justify-center px-5">
          <p class="text-sm italic text-(--color-text-muted)/60">{t("reader.toc_empty")}</p>
        </div>
      {:else}
        <nav class="flex-1 overflow-y-auto py-2">
          {#each entries as entry (entry.id)}
            <button
              type="button"
              class="flex w-full cursor-pointer items-start px-5 py-2 text-left transition-colors hover:bg-(--color-border)"
              class:border-l-2={entry.id === activeId}
              class:border-(--color-accent-blue)={entry.id === activeId}
              style="background-color: {entry.id === activeId ? 'rgba(73, 212, 255, 0.05)' : 'transparent'};"
              class:pl-7={entry.depth === 1}
              class:pl-9={entry.depth === 2}
              class:pl-11={entry.depth >= 3}
              onclick={() => onNavigate(entry)}
            >
              <span
                class="text-sm leading-relaxed"
                class:text-(--color-primary)={entry.id === activeId}
                class:font-medium={entry.id === activeId}
                class:text-(--color-text-muted)={entry.id !== activeId}
                class:text-xs={entry.depth >= 2}
              >
                {entry.title}
              </span>
            </button>
          {/each}
        </nav>
      {/if}
    </div>
  </div>
{/if}
