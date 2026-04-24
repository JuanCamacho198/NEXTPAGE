<script lang="ts">
  import { saveHighlight } from "$lib/api/tauriClient";
  import type { MessageKey } from "$lib/i18n";

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

  function hasResolvableSelectionContext() {
    if (!selectedText.trim() || !bookId.trim() || isSaving || !hasSelectionAnchor) {
      return false;
    }

    const hasPage = Number.isInteger(pageNumber) && pageNumber > 0;
    const hasCfi = typeof cfi === "string" && cfi.trim().length > 0;
    return hasPage || hasCfi;
  }

  async function handleCreateHighlight(note: string | null = null) {
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

  function handleDelete() {
    onClose();
  }

  function handleColorSelect(color: string) {
    selectedColor = color;
  }

  function handleToggleNoteEditor() {
    showNoteEditor = !showNoteEditor;
    if (!showNoteEditor) {
      noteText = "";
    }
  }

  async function handleSaveWithNote() {
    const trimmedNote = noteText.trim();
    if (trimmedNote.length === 0) {
      errorMessage = t("highlight.noteRequired");
      return;
    }

    await handleCreateHighlight(trimmedNote);
  }

  function handleRootKeydown(event: KeyboardEvent) {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
    }
  }
</script>

<div
  class="highlight-toolbar"
  role="dialog"
  tabindex="-1"
  aria-label={t("highlight.menuAriaLabel")}
  onkeydown={handleRootKeydown}
>
  <div class="color-picker">
    {#each colors as color}
      <button
        type="button"
        class="color-btn"
        class:selected={selectedColor === color.hex}
        style="background-color: {color.hex};"
        onclick={() => handleColorSelect(color.hex)}
        title={t(`settings.color.${color.name}` as MessageKey)}
        aria-label={t("highlight.selectColor", { color: t(`settings.color.${color.name}` as MessageKey) })}
      ></button>
    {/each}
  </div>
  <div class="actions">
    <button
      type="button"
      class="action-btn save"
      onclick={() => handleCreateHighlight()}
      disabled={isSaving}
    >
      {isSaving ? t("highlight.saving") : t("highlight.save")}
    </button>
    <button
      type="button"
      class="action-btn note"
      onclick={handleToggleNoteEditor}
      disabled={isSaving}
      aria-expanded={showNoteEditor}
      aria-controls="highlight-note-editor"
    >
      {t("highlight.note")}
    </button>
    <button type="button" class="action-btn delete" onclick={handleDelete}>
      {t("highlight.cancel")}
    </button>
  </div>

  {#if showNoteEditor}
    <div class="note-editor" id="highlight-note-editor">
      <textarea
        bind:value={noteText}
        rows="3"
        maxlength="500"
        placeholder={t("highlight.notePlaceholder")}
        aria-label={t("highlight.noteInputAriaLabel")}
      ></textarea>
      <button type="button" class="action-btn save" onclick={handleSaveWithNote} disabled={isSaving}>
        {isSaving ? t("highlight.saving") : t("highlight.saveWithNote")}
      </button>
    </div>
  {/if}

  {#if errorMessage}
    <p class="error" role="status" aria-live="polite">{errorMessage}</p>
  {/if}
</div>

<style>
  .highlight-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px;
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    border: 1px solid #e5e7eb;
  }

  .color-picker {
    display: flex;
    gap: 4px;
  }

  .color-btn {
    width: 24px;
    height: 24px;
    border: 2px solid transparent;
    border-radius: 50%;
    cursor: pointer;
    padding: 0;
    transition: transform 0.15s ease;
  }

  .color-btn:hover {
    transform: scale(1.1);
  }

  .color-btn.selected {
    border-color: #374151;
  }

  .actions {
    display: flex;
    gap: 4px;
    border-left: 1px solid #e5e7eb;
    padding-left: 8px;
  }

  .action-btn {
    padding: 4px 8px;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;
    font-weight: 500;
  }

  .action-btn.save {
    background: #374151;
    color: #fff;
  }

  .action-btn.save:hover:not(:disabled) {
    background: #1f2937;
  }

  .action-btn.save:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .action-btn.delete {
    background: #f3f4f6;
    color: #374151;
  }

  .action-btn.delete:hover {
    background: #e5e7eb;
  }

  .action-btn.note {
    background: #e0f2fe;
    color: #0c4a6e;
  }

  .action-btn.note:hover:not(:disabled) {
    background: #bae6fd;
  }

  .note-editor {
    display: flex;
    flex-direction: column;
    gap: 6px;
    width: 100%;
    margin-top: 4px;
  }

  .note-editor textarea {
    width: 100%;
    min-width: 220px;
    resize: vertical;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    padding: 6px;
    font-size: 12px;
    font-family: inherit;
  }

  .error {
    margin: 0;
    width: 100%;
    color: #b91c1c;
    font-size: 12px;
  }
</style>
