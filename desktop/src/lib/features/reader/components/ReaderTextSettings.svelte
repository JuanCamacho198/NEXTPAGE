<script lang="ts">
  import type { MessageKey } from "$lib/i18n";
  import type { ReaderSettings, ReaderThemeMode, ReaderTextAlign, ReaderDirection } from "$lib/shared/types";
  import { getDefaultReaderSettings } from "$lib/api/tauriClient";
  import { createFocusTrap } from "$lib/shared/utils/focusTrap";

  type Props = {
    open: boolean;
    format: "pdf" | "epub";
    readerSettings: ReaderSettings;
    onSettingsChange: (settings: ReaderSettings) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onClose: () => void;
  };

  let { open, format, readerSettings, onSettingsChange, t, onClose }: Props = $props();

  const themes: Array<{ name: ReaderThemeMode; bg: string; label: string }> = [
    { name: "paper", bg: "#ffffff", label: "Paper" },
    { name: "sepia", bg: "#f4ecd8", label: "Sepia" },
    { name: "night", bg: "#000000", label: "Night" },
    { name: "dark", bg: "#444444", label: "Dark" },
    { name: "blue", bg: "#5b7fa3", label: "Blue" },
  ];

  const LINE_HEIGHT_PRESETS = [1.4, 1.6, 1.8, 2.0, 2.2, 2.4];
  const PARAGRAPH_SPACING_PRESETS = [0, 0.5, 1, 1.5, 2, 3];
  const MARGIN_PRESETS: Array<ReaderSettings["margins"]> = [
    { top: 0.5, bottom: 0.5, left: 0.75, right: 0.75 },
    { top: 1, bottom: 1, left: 1.5, right: 1.5 },
    { top: 1.5, bottom: 1.5, left: 2, right: 2 },
    { top: 2, bottom: 2, left: 2.5, right: 2.5 },
    { top: 2.5, bottom: 2.5, left: 3, right: 3 },
  ];
  const ALIGN_CYCLE: ReaderTextAlign[] = ["left", "center", "right", "justify"];

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

  // ── Helpers ─────────────────────────────────────────

  function changeFontSize(delta: number) {
    const current = readerSettings.epub.fontSize;
    const next = Math.max(80, Math.min(200, current + delta));
    if (next !== current) {
      onSettingsChange({
        ...readerSettings,
        epub: { ...readerSettings.epub, fontSize: next },
      });
    }
  }

  function cycleLineHeight() {
    const current = readerSettings.lineHeight;
    const idx = LINE_HEIGHT_PRESETS.indexOf(current);
    const next = idx >= 0 && idx < LINE_HEIGHT_PRESETS.length - 1
      ? LINE_HEIGHT_PRESETS[idx + 1]
      : LINE_HEIGHT_PRESETS[0];
    onSettingsChange({ ...readerSettings, lineHeight: next });
  }

  function changeLetterSpacing(delta: number) {
    const current = readerSettings.letterSpacing;
    const next = Math.max(-2, Math.min(10, current + delta));
    if (next !== current) {
      onSettingsChange({ ...readerSettings, letterSpacing: next });
    }
  }

  function cycleAlignment() {
    const current = readerSettings.textAlign;
    const idx = ALIGN_CYCLE.indexOf(current);
    const next = idx >= 0 && idx < ALIGN_CYCLE.length - 1
      ? ALIGN_CYCLE[idx + 1]
      : ALIGN_CYCLE[0];
    onSettingsChange({ ...readerSettings, textAlign: next });
  }

  function cycleParagraphSpacing() {
    const current = readerSettings.paragraphSpacing;
    const idx = PARAGRAPH_SPACING_PRESETS.indexOf(current);
    const next = idx >= 0 && idx < PARAGRAPH_SPACING_PRESETS.length - 1
      ? PARAGRAPH_SPACING_PRESETS[idx + 1]
      : PARAGRAPH_SPACING_PRESETS[0];
    onSettingsChange({ ...readerSettings, paragraphSpacing: next });
  }

  function cycleMargins() {
    const current = readerSettings.margins;
    const idx = MARGIN_PRESETS.findIndex(
      (m) => m.top === current.top && m.bottom === current.bottom && m.left === current.left && m.right === current.right,
    );
    const next = idx >= 0 && idx < MARGIN_PRESETS.length - 1
      ? MARGIN_PRESETS[idx + 1]
      : MARGIN_PRESETS[0];
    onSettingsChange({ ...readerSettings, margins: next });
  }

  function resetToDefaults() {
    const defaults = getDefaultReaderSettings();
    onSettingsChange(defaults);
  }

  // ── Alignment label ───────────────────────────────
  function alignLabel(al: ReaderTextAlign): string {
    const labels: Record<ReaderTextAlign, string> = {
      left: "Left", center: "Center", right: "Right", justify: "Justify",
    };
    return labels[al] ?? "Left";
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
          <!-- Alignment button -->
          <button
            type="button"
            class="cursor-pointer rounded p-2 hover:bg-white/5"
            onclick={cycleAlignment}
            aria-label={t("reader.alignment")}
            title={alignLabel(readerSettings.textAlign)}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="15" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
              <line x1="17" y1="10" x2="3" y2="10"></line>
              <line x1="21" y1="6" x2="3" y2="6"></line>
              <line x1="21" y1="14" x2="3" y2="14"></line>
              <line x1="17" y1="18" x2="3" y2="18"></line>
            </svg>
          </button>
          <!-- Font increase -->
          <button
            type="button"
            class="cursor-pointer rounded bg-[#49d4ff] p-2"
            onclick={() => changeFontSize(10)}
            aria-label={t("reader.font_increase")}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="17" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="4 7 4 4 20 4 20 7"></polyline>
              <line x1="9" y1="20" x2="15" y2="20"></line>
              <line x1="12" y1="4" x2="12" y2="20"></line>
            </svg>
          </button>
          <!-- List view / direction toggle -->
          <button
            type="button"
            class="cursor-pointer rounded p-2 hover:bg-white/5"
            onclick={() => onSettingsChange({
              ...readerSettings,
              direction: readerSettings.direction === "ltr" ? "rtl" : "ltr" as ReaderDirection,
            })}
            aria-label={t("reader.direction")}
            title={readerSettings.direction === "ltr" ? "LTR" : "RTL"}
          >
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
              class:ring-2={readerSettings.themeMode === theme.name}
              class:ring-[#49d4ff]={readerSettings.themeMode === theme.name}
              style="background-color: {theme.bg};"
              onclick={() => onSettingsChange({ ...readerSettings, themeMode: theme.name })}
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
          <!-- Font Family (EPUB only) -->
          <button
            type="button"
            class="flex w-full items-center justify-between rounded-xl bg-white/2 px-3 py-2"
            onclick={() => {
              const families = ["serif", "sans-serif", "monospace", "Georgia", "Palatino"];
              const current = readerSettings.epub.fontFamily;
              const idx = families.indexOf(current);
              const next = idx >= 0 && idx < families.length - 1 ? families[idx + 1] : families[0];
              onSettingsChange({
                ...readerSettings,
                epub: { ...readerSettings.epub, fontFamily: next },
              });
            }}
          >
            <span class="text-sm font-normal text-[#49d4ff]">{readerSettings.epub.fontFamily || "Default"}</span>
            <svg xmlns="http://www.w3.org/2000/svg" width="9" height="6" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          <!-- Font Size & Line Spacing (EPUB only) -->
          <div class="flex justify-between">
            <button
              type="button"
              class="flex h-12 w-26.25 cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5"
              onclick={() => changeFontSize(-10)}
              aria-label={t("reader.font_decrease")}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="17" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
                <polyline points="4 7 4 4 20 4 20 7"></polyline>
                <line x1="9" y1="20" x2="15" y2="20"></line>
                <line x1="12" y1="4" x2="12" y2="20"></line>
              </svg>
            </button>
            <button
              type="button"
              class="flex h-12 w-26.25 cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5"
              onclick={cycleLineHeight}
              aria-label={t("reader.line_spacing")}
              title={`Line height: ${readerSettings.lineHeight}`}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="27" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#f8fbff]">
                <line x1="5" y1="3" x2="19" y2="3"></line>
                <line x1="5" y1="21" x2="19" y2="21"></line>
                <polyline points="12 7 9 10 15 10"></polyline>
                <polyline points="9 14 12 17 15 14"></polyline>
                <line x1="12" y1="7" x2="12" y2="17"></line>
              </svg>
            </button>
          </div>

          <!-- Letter Spacing (EPUB only) -->
          <div class="flex justify-between">
            <button
              type="button"
              class="flex h-10 w-26.25 cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5"
              onclick={() => changeLetterSpacing(-1)}
              aria-label={t("reader.spacing_decrease")}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-[#8fa3bf]">
                <line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
            </button>
            <button
              type="button"
              class="flex h-10 w-26.25 cursor-pointer items-center justify-center rounded p-2 hover:bg-white/5"
              onclick={() => changeLetterSpacing(1)}
              aria-label={t("reader.spacing_increase")}
            >
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
            <!-- Direction -->
            <div class="flex items-center justify-between cursor-pointer" onclick={() => onSettingsChange({
              ...readerSettings,
              direction: readerSettings.direction === "ltr" ? "rtl" as ReaderDirection : "ltr" as ReaderDirection,
            })} role="button" tabindex="0" onkeydown={(e) => e.key === "Enter" && onSettingsChange({ ...readerSettings, direction: readerSettings.direction === "ltr" ? "rtl" as ReaderDirection : "ltr" as ReaderDirection })}>
              <span class="text-sm text-[#8fa3bf]">{t("reader.direction")}: {readerSettings.direction === "ltr" ? "LTR" : "RTL"}</span>
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </div>
            <!-- Alignment -->
            <div class="flex items-center justify-between cursor-pointer" onclick={cycleAlignment} role="button" tabindex="0" onkeydown={(e) => e.key === "Enter" && cycleAlignment()}>
              <span class="text-sm text-[#8fa3bf]">{t("reader.alignment")}: {alignLabel(readerSettings.textAlign)}</span>
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </div>
            <!-- Colors (info only - handled by theme swatches) -->
            <div class="flex items-center justify-between opacity-60">
              <span class="text-sm text-[#8fa3bf]">{t("reader.colors")}</span>
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </div>
            <!-- Margins -->
            <div class="flex items-center justify-between cursor-pointer" onclick={cycleMargins} role="button" tabindex="0" onkeydown={(e) => e.key === "Enter" && cycleMargins()}>
              <span class="text-sm text-[#8fa3bf]">{t("reader.margins")}: {readerSettings.margins.left.toFixed(1)}rem</span>
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </div>
            <!-- Paragraph spacing -->
            <div class="flex items-center justify-between cursor-pointer" onclick={cycleParagraphSpacing} role="button" tabindex="0" onkeydown={(e) => e.key === "Enter" && cycleParagraphSpacing()}>
              <span class="text-sm text-[#8fa3bf]">{t("reader.paragraph_spacing")}: {readerSettings.paragraphSpacing}em</span>
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </div>
            <!-- Hyphenation -->
            <div class="flex items-center justify-between cursor-pointer" onclick={() => onSettingsChange({ ...readerSettings, hyphenation: !readerSettings.hyphenation })} role="button" tabindex="0" onkeydown={(e) => e.key === "Enter" && onSettingsChange({ ...readerSettings, hyphenation: !readerSettings.hyphenation })}>
              <span class="text-sm text-[#8fa3bf]">{t("reader.hyphenation")}: {readerSettings.hyphenation ? "On" : "Off"}</span>
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </div>
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
            style="background-color: {readerSettings.showHeader ? '#49d4ff' : 'rgba(148, 173, 206, 0.2)'};"
            onclick={() => onSettingsChange({ ...readerSettings, showHeader: !readerSettings.showHeader })}
            role="switch"
            aria-checked={readerSettings.showHeader}
            aria-label={t("settings.reading.showHeader")}
          >
            <span
              class="h-4 w-4 rounded-full bg-white shadow transition-transform"
              class:translate-x-[22px]={readerSettings.showHeader}
              class:translate-x-[2px]={!readerSettings.showHeader}
            ></span>
          </button>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-[#8fa3bf]">{t("settings.reading.showFooter")}</span>
          <button
            type="button"
            class="relative flex h-5 w-10 cursor-pointer items-center rounded-full transition-colors"
            style="background-color: {readerSettings.showFooter ? '#49d4ff' : 'rgba(148, 173, 206, 0.2)'};"
            onclick={() => onSettingsChange({ ...readerSettings, showFooter: !readerSettings.showFooter })}
            role="switch"
            aria-checked={readerSettings.showFooter}
            aria-label={t("settings.reading.showFooter")}
          >
            <span
              class="h-4 w-4 rounded-full bg-white shadow transition-transform"
              class:translate-x-[22px]={readerSettings.showFooter}
              class:translate-x-[2px]={!readerSettings.showFooter}
            ></span>
          </button>
          </div>
          <!-- Vertical Scrolling -->
          {#if format === "epub"}
            <div class="flex items-center justify-between">
              <span class="text-sm text-[#8fa3bf]">{t("reader.vertical_scroll")}</span>
              <button
                type="button"
                class="relative flex h-5 w-10 cursor-pointer items-center rounded-full transition-colors"
                style="background-color: {readerSettings.verticalScrolling ? '#49d4ff' : 'rgba(148, 173, 206, 0.2)'};"
                onclick={() => onSettingsChange({ ...readerSettings, verticalScrolling: !readerSettings.verticalScrolling })}
                role="switch"
                aria-checked={readerSettings.verticalScrolling}
                aria-label={t("reader.vertical_scroll")}
              >
                <span
                  class="h-4 w-4 rounded-full bg-white shadow transition-transform"
                  class:translate-x-[22px]={readerSettings.verticalScrolling}
                  class:translate-x-[2px]={!readerSettings.verticalScrolling}
                ></span>
              </button>
            </div>
          {/if}
        </div>

        <!-- Saved Settings (reset to defaults) -->
        <button
          type="button"
          class="flex w-full items-center justify-between rounded-xl bg-white/2 px-3 py-2 cursor-pointer hover:bg-white/5"
          onclick={resetToDefaults}
        >
          <span class="text-sm text-[#49d4ff]">{t("reader.saved_settings")}</span>
          <svg xmlns="http://www.w3.org/2000/svg" width="9" height="6" viewBox="0 0 24 24" fill="none" stroke="#49d4ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </button>
      </div>
    </div>
  </div>
{/if}
