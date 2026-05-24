<script lang="ts">
  import type { MessageKey } from "$lib/i18n";

  type Props = {
    selectedText: string;
    selectionBounds: { left: number; top: number; right: number; bottom: number };
    onCopy: () => void;
    onNote: (text: string) => void;
    onDismiss: () => void;
    onColorSelect: (color: string) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    selectedText,
    selectionBounds,
    onCopy,
    onNote,
    onDismiss,
    onColorSelect,
    t,
  }: Props = $props();

  const colors = [
    { hex: "#FACC15", label: "yellow" },
    { hex: "#4ADE80", label: "green" },
    { hex: "#60A5FA", label: "blue" },
    { hex: "#C084FC", label: "purple" },
    { hex: "#FB923C", label: "orange" },
  ];

  let selectedColor = $state("#FACC15");
  let showNoteEditor = $state(false);
  let noteText = $state("");

  // Position above selection if space allows
  const toolbarY = $derived(
    selectionBounds.top > 120
      ? selectionBounds.top - 56 // above
      : selectionBounds.bottom + 16 // below
  );
  const toolbarX = $derived(
    Math.max(
      16,
      Math.min(
        selectionBounds.left - 140, // center-ish above the selection
        window.innerWidth - 360
      )
    )
  );

  function selectColor(hex: string) {
    selectedColor = hex;
    onColorSelect(hex);
  }

  function handleCopy() {
    onCopy();
  }

  function handleNoteToggle() {
    showNoteEditor = !showNoteEditor;
    if (!showNoteEditor) noteText = "";
  }

  function handleSaveNote() {
    if (noteText.trim()) {
      onNote(noteText.trim());
    }
    showNoteEditor = false;
    noteText = "";
  }
</script>

<div
  class="fixed z-50"
  style="left: {toolbarX}px; top: {toolbarY}px;"
  role="presentation"
  onmouseup={(e) => e.stopPropagation()}
>
  <!-- Tip arrow pointing to selection -->
  <div
    class="mx-auto h-2.5 w-5"
    style="clip-path: polygon(50% 100%, 0 0, 100% 0); background: #1E293B;"
  ></div>

  <!-- Main toolbar -->
  <div class="flex items-center gap-5 rounded-[28px] border border-[#1E293B] bg-[#1E293B] px-5 py-2.5 shadow-xl">
    <!-- Color circles -->
    {#each colors as color}
      <button
        type="button"
        class="h-6 w-6 cursor-pointer rounded-full transition-transform hover:scale-110"
        class:ring-2={selectedColor === color.hex}
        class:ring-white={selectedColor === color.hex}
        style="background-color: {color.hex};"
        onclick={() => selectColor(color.hex)}
        aria-label={color.label}
      ></button>
    {/each}

    <!-- Separator -->
    <span class="text-base font-normal text-[#334155]">||</span>

    <!-- Copy -->
    <button
      type="button"
      class="cursor-pointer text-sm font-medium text-[#F8FAFC] hover:text-white"
      onclick={handleCopy}
    >
      {t("reader.copiar")}
    </button>

    <!-- Note -->
    <button
      type="button"
      class="cursor-pointer text-sm font-medium text-[#F8FAFC] hover:text-white"
      onclick={handleNoteToggle}
    >
      {t("reader.nota")}
    </button>

    <!-- Trash -->
    <button
      type="button"
      class="cursor-pointer text-[#EF4444] hover:text-red-400"
      onclick={onDismiss}
      aria-label={t("reader.eliminar_destacado")}
    >
      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 6h18"></path>
        <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
        <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
      </svg>
    </button>
  </div>

  <!-- Note editor (inline) -->
  {#if showNoteEditor}
    <div class="mt-2 rounded-2xl border border-[#1E293B] bg-[#1E293B] p-3 shadow-xl">
      <textarea
        bind:value={noteText}
        rows="2"
        class="w-full resize-none rounded-xl border border-[#334155] bg-[#0B1120] p-2 text-sm text-white placeholder-[#64748B] focus:outline-none focus:ring-1 focus:ring-[#38BDF8]"
        placeholder={t("highlight.notePlaceholder")}
        maxlength="500"
      ></textarea>
      <div class="mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="cursor-pointer rounded-full px-3 py-1 text-xs font-medium text-[#94A3B8] hover:text-white"
          onclick={handleNoteToggle}
        >
          {t("highlight.cancel")}
        </button>
        <button
          type="button"
          class="cursor-pointer rounded-full bg-[#38BDF8] px-3 py-1 text-xs font-medium text-[#0B1120] hover:bg-[#7DD3FC]"
          onclick={handleSaveNote}
          disabled={!noteText.trim()}
        >
          {t("highlight.save")}
        </button>
      </div>
    </div>
  {/if}
</div>
