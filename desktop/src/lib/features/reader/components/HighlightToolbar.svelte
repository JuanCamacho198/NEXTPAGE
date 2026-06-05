<script lang="ts">
  import { saveHighlight } from "$lib/shared/api/tauriClient";
  import type { MessageKey } from "$lib/shared/i18n";

  type Props = {
    selectedText: string;
    bookId: string;
    pageNumber: number;
    selectionBounds?: { left: number; top: number; right: number; bottom: number };
    cfi?: string | null;
    hasSelectionAnchor?: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onClose: () => void;
  };

  let { selectedText, bookId, pageNumber, selectionBounds = { left: 0, top: 0, right: 0, bottom: 0 }, cfi = null, hasSelectionAnchor = true, t, onClose }: Props = $props();

  const colors = [
    { name: "yellow", hex: "#fef08a" },
    { name: "green", hex: "#bbf7d0" },
    { name: "blue", hex: "#bfdbfe" },
    { name: "pink", hex: "#fbcfe8" },
    { name: "orange", hex: "#fed7aa" },
  ];

  let selectedColor = $state(colors[0].hex);
  let isSaving = $state(false);
  let showNoteEditor = $state(false);
  let noteText = $state("");
  let errorMessage = $state<string | null>(null);
  const selectionPreview = $derived(
    selectedText.trim().length > 140 ? `${selectedText.trim().slice(0, 140)}...` : selectedText.trim()
  );

  function hasResolvableSelectionContext(): boolean {
    if (!selectedText.trim() || !bookId.trim() || isSaving || !hasSelectionAnchor) {
      return false;
    }

    const hasPage = Number.isInteger(pageNumber) && pageNumber > 0;
    const hasCfi = typeof cfi === "string" && cfi.trim().length > 0;
    return hasPage || hasCfi;
  }

  async function handleCreateHighlight(note: string | null = null): Promise<void> {
    if (!hasResolvableSelectionContext()) {
      errorMessage = t("highlight.selectionUnavailable");
      return;
    }

    isSaving = true;
    errorMessage = null;

    const normalizedCfi = typeof cfi === "string" && cfi.trim().length > 0 ? cfi.trim() : null;

    try {
      await saveHighlight({
        id: crypto.randomUUID(),
        bookId,
        text: selectedText.trim(),
        color: selectedColor,
        pageNumber,
        rectLeft: selectionBounds.left,
        rectRight: selectionBounds.right,
        rectTop: selectionBounds.top,
        rectBottom: selectionBounds.bottom,
        cfi: normalizedCfi,
        note,
      });
      onClose();
    } catch (err) {
      console.error("Failed to save highlight:", err);
      errorMessage = t("highlight.saveFailed");
    } finally {
      isSaving = false;
    }
  }

  function handleDelete(): void {
    onClose();
  }

  function handleColorSelect(color: string): void {
    selectedColor = color;
  }

  function handleToggleNoteEditor(): void {
    showNoteEditor = !showNoteEditor;
    if (!showNoteEditor) {
      noteText = "";
    }
  }

  async function handleSaveWithNote(): Promise<void> {
    const trimmedNote = noteText.trim();
    if (trimmedNote.length === 0) {
      errorMessage = t("highlight.noteRequired");
      return;
    }

    await handleCreateHighlight(trimmedNote);
  }

  function handleRootKeydown(event: KeyboardEvent): void {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
    }
  }
</script>

<div
  class="flex w-full flex-col gap-2.5 rounded-2xl border border-[rgba(148,163,184,0.3)] p-3 shadow-[0_20px_40px_rgba(15,23,42,0.18)] backdrop-blur-xl sm:rounded-2xl sm:p-3"
  style="background: linear-gradient(180deg, rgba(255,255,255,0.28), rgba(255,255,255,0)), color-mix(in srgb, var(--pdf-reader-surface-bg, #fff) 92%, #0f172a 8%);"
  role="presentation"
  onkeydown={handleRootKeydown}
>
  <div onkeydown={handleRootKeydown} role="presentation">
    <div class="flex items-start justify-between gap-3">
      <p
        class="m-0 line-clamp-3 text-xs font-semibold leading-[1.45] tracking-[0.01em]"
        style="color: color-mix(in srgb, var(--pdf-reader-text, #0f172a) 92%, white 8%);"
      >
        {selectionPreview}
      </p>
      <button
        type="button"
        class="shrink-0 cursor-pointer rounded-full border border-[rgba(148,163,184,0.28)] bg-white/62 p-0 text-center text-lg leading-none transition-transform duration-150 hover:-translate-y-0.5 hover:bg-white/85"
        style="width: 28px; height: 28px; color: color-mix(in srgb, var(--pdf-reader-text, #0f172a) 86%, white 14%);"
        onclick={handleDelete}
        aria-label={t("highlight.cancel")}
      >
        ×
      </button>
    </div>

    <div class="flex flex-wrap items-center gap-2.5 sm:items-center max-sm:items-stretch">
      <div class="flex gap-1.5 rounded-full bg-[rgba(148,163,184,0.12)] p-1.5 max-sm:w-full max-sm:justify-center">
        {#each colors as color}
          <button
            type="button"
            class="cursor-pointer rounded-full border-2 border-white/78 p-0 shadow-[inset_0_0_0_1px_rgba(15,23,42,0.08)] transition-all duration-150 hover:-translate-y-0.5 hover:scale-105"
            style="width: 26px; height: 26px; background-color: {color.hex};"
            class:border-[rgba(15,23,42,0.76)]={selectedColor === color.hex}
            class:shadow-[inset_0_0_0_1px_rgba(15,23,42,0.12),0_0_0_3px_rgba(148,163,184,0.18)]={selectedColor === color.hex}
            onclick={() => handleColorSelect(color.hex)}
            title={t(`settings.color.${color.name}` as MessageKey)}
            aria-label={t("highlight.selectColor", { color: t(`settings.color.${color.name}` as MessageKey) })}
          ></button>
        {/each}
      </div>
      <div class="flex flex-1 flex-wrap gap-1.5 max-sm:w-full">
        <button
          type="button"
          class="inline-flex min-h-8.5 cursor-pointer items-center rounded-full border border-transparent bg-[#0f172a]/82 px-3 py-1.75 text-xs font-bold tracking-[0.01em] text-[#f8fafc] shadow-[0_10px_18px_rgba(15,23,42,0.2)] transition-all duration-150 hover:-translate-y-0.5 hover:bg-[#020617]/88 disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none max-sm:flex-1 max-sm:justify-center"
          onclick={() => handleCreateHighlight()}
          disabled={isSaving}
        >
          {isSaving ? t("highlight.saving") : t("highlight.save")}
        </button>
        <button
          type="button"
          class="inline-flex min-h-8.5 cursor-pointer items-center rounded-full border border-[rgba(96,165,250,0.2)] bg-[#dbeafe]/72 px-3 py-1.75 text-xs font-bold tracking-[0.01em] text-[#0c4a6e] transition-all duration-150 hover:-translate-y-0.5 hover:bg-[#bfdbfe]/82 disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none max-sm:flex-1 max-sm:justify-center"
          onclick={handleToggleNoteEditor}
          disabled={isSaving}
          aria-expanded={showNoteEditor}
          aria-controls="highlight-note-editor"
        >
          {t("highlight.note")}
        </button>
        <button
          type="button"
          class="inline-flex min-h-8.5 cursor-pointer items-center rounded-full border border-[rgba(148,163,184,0.28)] bg-white/58 px-3 py-1.75 text-xs font-bold tracking-[0.01em] transition-all duration-150 hover:-translate-y-0.5 hover:bg-white/82 disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none max-sm:flex-1 max-sm:justify-center"
          style="color: color-mix(in srgb, var(--pdf-reader-text, #0f172a) 86%, white 14%);"
          onclick={handleDelete}
        >
          {t("highlight.cancel")}
        </button>
      </div>
    </div>

    {#if showNoteEditor}
      <div class="flex w-full flex-col gap-1.5 border-t border-[rgba(148,163,184,0.2)] pt-1" id="highlight-note-editor">
        <textarea
          bind:value={noteText}
          rows="3"
          maxlength="500"
          class="w-full min-w-0 resize-y rounded-xl border border-[rgba(148,163,184,0.3)] bg-white/82 px-3 py-2.5 text-xs font-[inherit]"
          style="color: var(--pdf-reader-text, #0f172a);"
          placeholder={t("highlight.notePlaceholder")}
          aria-label={t("highlight.noteInputAriaLabel")}
        ></textarea>
        <button
          type="button"
          class="inline-flex min-h-8.5 cursor-pointer items-center self-end rounded-full border border-transparent bg-[#0f172a]/82 px-3 py-1.75 text-xs font-bold tracking-[0.01em] text-[#f8fafc] shadow-[0_10px_18px_rgba(15,23,42,0.2)] transition-all duration-150 hover:-translate-y-0.5 hover:bg-[#020617]/88 disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none"
          onclick={handleSaveWithNote}
          disabled={isSaving}
        >
          {isSaving ? t("highlight.saving") : t("highlight.saveWithNote")}
        </button>
      </div>
    {/if}

    {#if errorMessage}
      <p class="m-0 w-full text-xs font-semibold text-red-700" role="status" aria-live="polite">{errorMessage}</p>
    {/if}
  </div>
</div>
