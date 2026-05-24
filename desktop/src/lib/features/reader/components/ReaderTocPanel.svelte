<script lang="ts">
  import type { MessageKey } from "$lib/i18n";

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

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === e.currentTarget) onClose();
  }
</script>

{#if open}
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    class="fixed inset-0 z-40"
    onclick={handleBackdropClick}
    onkeydown={(e) => e.key === "Escape" && onClose()}
    role="presentation"
  >
    <!-- Backdrop -->
    <div class="absolute inset-0 bg-[#101c2c]/70"></div>

    <!-- Sidebar -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
      class="absolute right-0 top-0 flex h-full w-[260px] flex-col overflow-y-auto border-l border-[#17263a] bg-[#101c2c]/70 pt-[60px] text-[#8fa3bf] backdrop-blur-sm"
      onkeydown={(e) => e.key === "Escape" && onClose()}
      role="dialog"
      aria-label={t("reader.tabla_contenidos")}
      tabindex="0"
    >
      <!-- Heading -->
      <div class="border-b border-[#94adce]/5 px-5 py-4">
        <h2 class="text-base font-bold text-[#f8fbff]">{t("reader.tabla_contenidos")}</h2>
      </div>

      <!-- Chapter List -->
      {#if entries.length === 0}
        <div class="flex flex-1 items-center justify-center px-5">
          <p class="text-sm italic text-[#8fa3bf]/60">{t("reader.toc_empty")}</p>
        </div>
      {:else}
        <nav class="flex-1 overflow-y-auto py-2">
          {#each entries as entry (entry.id)}
            <button
              type="button"
              class="flex w-full cursor-pointer items-start px-5 py-2 text-left transition-colors hover:bg-white/5"
              class:border-l-2={entry.id === activeId}
              class:border-[#49d4ff]={entry.id === activeId}
              style="background-color: {entry.id === activeId ? 'rgba(73, 212, 255, 0.05)' : 'transparent'};"
              class:pl-7={entry.depth === 1}
              class:pl-9={entry.depth === 2}
              class:pl-11={entry.depth >= 3}
              onclick={() => onNavigate(entry)}
            >
              <span
                class="text-sm leading-relaxed"
                class:text-[#f8fbff]={entry.id === activeId}
                class:font-medium={entry.id === activeId}
                class:text-[#8fa3bf]={entry.id !== activeId}
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
