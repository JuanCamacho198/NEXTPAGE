<script lang="ts">
  import type { MessageKey } from "$lib/i18n";
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
  import { createFocusTrap } from "$lib/shared/utils/focusTrap";

  type Props = {
    open: boolean;
    format: "pdf" | "epub";
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onClose: () => void;
  };

  let { open, format, t, onClose }: Props = $props();

  const themes = [
    { name: "paper", bg: "#ffffff", label: "Paper" },
    { name: "sepia", bg: "#f4ecd8", label: "Sepia" },
    { name: "night", bg: "#000000", label: "Night" },
    { name: "dark", bg: "#444444", label: "Dark" },
    { name: "blue", bg: "#5b7fa3", label: "Blue" },
  ];

  let selectedTheme = $state("paper");
  let publisherDefaults = $state(true);
  let verticalScrolling = $state(false);

  let sidebarEl: HTMLDivElement | undefined = $state();

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === e.currentTarget) onClose();
  }

  // Focus trap: keep Tab focus inside the text settings panel when open
  $effect(() => {
    if (open && sidebarEl) {
      const trap = createFocusTrap(sidebarEl);
      trap.activate();
      return () => trap.deactivate();
    }
  });
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
      bind:this={sidebarEl}
      class="absolute right-0 top-0 flex h-full w-65 flex-col overflow-y-auto border-l border-[#17263a] bg-[#101c2c]/70 pt-15 text-[#8fa3bf] backdrop-blur-sm"
      onkeydown={(e) => e.key === "Escape" && onClose()}
      role="dialog"
      aria-label={t("reader.ajustes_texto")}
      tabindex="0"
    >
      <!-- Sidebar Header Icons -->
      <div class="flex items-center justify-between border-b border-[#94adce]/5 px-4 py-4">
        <!-- Close button -->
        <button type="button" onclick={onClose} class="flex cursor-pointer items-center gap-1 text-[#8fa3bf] hover:text-white" aria-label={t("settings.close")}>
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
          <span class="text-xs font-medium">{t("settings.close")}</span>
        </button>
        {#if format === "epub"}
          <!-- 4 icon buttons (EPUB only) -->
          <button type="button" class="cursor-pointer rounded p-2 hover:bg-white/5" aria-label={t("reader.list_view")}>
            <svg xmlns="http://www.w3.org/2000/svg" width="17" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
              <line x1="8" y1="6" x2="21" y2="6"></line>
              <line x1="8" y1="12" x2="21" y2="12"></line>
              <line x1="8" y1="18" x2="21" y2="18"></line>
              <line x1="3" y1="6" x2="3.01" y2="6"></line>
              <line x1="3" y1="12" x2="3.01" y2="12"></line>
              <line x1="3" y1="18" x2="3.01" y2="18"></line>
            </svg>
          </button>
          <button type="button" class="cursor-pointer rounded p-2 hover:bg-white/5" aria-label={t("reader.align_left")}>
            <svg xmlns="http://www.w3.org/2000/svg" width="15" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
              <line x1="17" y1="10" x2="3" y2="10"></line>
              <line x1="21" y1="6" x2="3" y2="6"></line>
              <line x1="21" y1="14" x2="3" y2="14"></line>
              <line x1="17" y1="18" x2="3" y2="18"></line>
            </svg>
          </button>
          <button type="button" class="cursor-pointer rounded bg-[#49d4ff] p-2" aria-label={t("reader.font_increase")}>
            <svg xmlns="http://www.w3.org/2000/svg" width="17" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="4 7 4 4 20 4 20 7"></polyline>
              <line x1="9" y1="20" x2="15" y2="20"></line>
              <line x1="12" y1="4" x2="12" y2="20"></line>
            </svg>
          </button>
          <button type="button" class="cursor-pointer rounded p-2 hover:bg-white/5" aria-label={t("reader.columns")}>
            <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
              <rect x="3" y="3" width="7" height="7"></rect>
              <rect x="14" y="3" width="7" height="7"></rect>
              <rect x="14" y="14" width="7" height="7"></rect>
              <rect x="3" y="14" width="7" height="7"></rect>
            </svg>
          </button>
        {/if}
      </div>

      <div class="flex flex-col gap-6 p-4">
        <!-- Theme Swatches (both formats) -->
        <div class="flex items-center justify-between px-1">
          {#each themes as theme}
            <button
              type="button"
              class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full transition-transform hover:scale-110"
              class:ring-2={selectedTheme === theme.name}
              class:ring-[#49d4ff]={selectedTheme === theme.name}
              style="background-color: {theme.bg};"
              onclick={() => (selectedTheme = theme.name)}
              aria-label={theme.label}
            >
              <span
                class="text-[10px] font-normal"
                class:text-white={theme.name === "night" || theme.name === "dark" || theme.name === "blue"}
                class:text-black={theme.name === "paper" || theme.name === "sepia"}
              >
                Aa
              </span>
            </button>
          {/each}
        </div>

        {#if format === "epub"}
          <!-- Font Selection (EPUB only) -->
          <button type="button" class="flex w-full items-center justify-between rounded-xl bg-white/2 px-3 py-2">
            <span class="text-sm font-normal text-[#49d4ff]">Default</span>
            <svg xmlns="http://www.w3.org/2000/svg" width="9" height="6" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          <!-- Font Size & Spacing (EPUB only) -->
          <div class="flex justify-between">
            <button type="button" class="flex h-12 w-[105px] cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5" aria-label={t("reader.font_decrease")}>
              <svg xmlns="http://www.w3.org/2000/svg" width="17" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
                <polyline points="4 7 4 4 20 4 20 7"></polyline>
                <line x1="9" y1="20" x2="15" y2="20"></line>
                <line x1="12" y1="4" x2="12" y2="20"></line>
              </svg>
            </button>
            <button type="button" class="flex h-12 w-[105px] cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5" aria-label={t("reader.line_spacing")}>
              <svg xmlns="http://www.w3.org/2000/svg" width="27" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#f8fbff]">
                <line x1="5" y1="3" x2="19" y2="3"></line>
                <line x1="5" y1="21" x2="19" y2="21"></line>
                <polyline points="12 7 9 10 15 10"></polyline>
                <polyline points="9 14 12 17 15 14"></polyline>
                <line x1="12" y1="7" x2="12" y2="17"></line>
              </svg>
            </button>
          </div>

          <!-- A- / A+ row (EPUB only) -->
          <div class="flex justify-between">
            <button type="button" class="flex h-10 w-[105px] cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5" aria-label={t("reader.spacing_decrease")}>
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
                <line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
            </button>
            <button type="button" class="flex h-10 w-[105px] cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5" aria-label={t("reader.spacing_increase")}>
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
                <line x1="12" y1="5" x2="12" y2="19"></line>
                <line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
            </button>
          </div>

          <!-- Separator (EPUB only) -->
          <div class="h-px w-full bg-[#94adce]/5"></div>

          <!-- Collapsible Menu Items (EPUB only) -->
          <div class="flex flex-col gap-4">
            {#each ["reader.direction", "reader.alignment", "reader.colors", "reader.margins", "reader.paragraph_spacing", "reader.hyphenation"] as item}
              <div class="flex items-center justify-between">
                <span class="text-sm text-[#8fa3bf]">{t(item as MessageKey)}</span>
                <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
              </div>
            {/each}
          </div>

          <!-- Separator (EPUB only) -->
          <div class="h-px w-full bg-[#94adce]/5"></div>
        {/if}

        <!-- Toggles (both formats) -->
        <div class="flex flex-col gap-4">
          <div class="flex items-center justify-between">
            <span class="text-sm text-[#8fa3bf]">{t("settings.reading.showHeader")}</span>
          <button
            type="button"
            class="relative flex h-5 w-10 cursor-pointer items-center rounded-full transition-colors"
            style="background-color: {publisherDefaults ? '#49d4ff' : 'rgba(148, 173, 206, 0.2)'};"
            onclick={() => (publisherDefaults = !publisherDefaults)}
            role="switch"
            aria-checked={publisherDefaults}
            aria-label={t("settings.reading.showHeader")}
          >
            <span
              class="h-4 w-4 rounded-full bg-white shadow transition-transform"
              class:translate-x-[22px]={publisherDefaults}
              class:translate-x-[2px]={!publisherDefaults}
            ></span>
          </button>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-[#8fa3bf]">{t("settings.reading.showFooter")}</span>
          <button
            type="button"
            class="relative flex h-5 w-10 cursor-pointer items-center rounded-full transition-colors"
            style="background-color: {verticalScrolling ? '#49d4ff' : 'rgba(148, 173, 206, 0.2)'};"
            onclick={() => (verticalScrolling = !verticalScrolling)}
            role="switch"
            aria-checked={verticalScrolling}
            aria-label={t("settings.reading.showFooter")}
          >
            <span
              class="h-4 w-4 rounded-full bg-white shadow transition-transform"
              class:translate-x-[22px]={verticalScrolling}
              class:translate-x-[2px]={!verticalScrolling}
            ></span>
          </button>
          </div>
        </div>

        <!-- Saved Settings (both formats) -->
        <button type="button" class="flex w-full items-center justify-between rounded-xl bg-white/2 px-3 py-2">
          <span class="text-sm text-[#49d4ff]">{t("reader.saved_settings")}</span>
          <svg xmlns="http://www.w3.org/2000/svg" width="9" height="6" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </button>
      </div>
    </div>
  </div>
{/if}
