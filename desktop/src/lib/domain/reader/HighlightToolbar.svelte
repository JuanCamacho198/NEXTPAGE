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
  const selectionPreview = $derived(
    selectedText.trim().length > 140 ? `${selectedText.trim().slice(0, 140)}...` : selectedText.trim()
  );

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
  role="presentation"
  onkeydown={handleRootKeydown}
>
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div onkeydown={handleRootKeydown}>
    <div class="toolbar-header">
    <p class="selection-preview">{selectionPreview}</p>
    <button type="button" class="dismiss-btn" onclick={handleDelete} aria-label={t("highlight.cancel")}>
      ×
    </button>
  </div>

  <div class="toolbar-body">
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
</div>

<style>
  .highlight-toolbar {
    --toolbar-surface: color-mix(in srgb, var(--pdf-reader-surface-bg, #fff) 92%, #0f172a 8%);
    --toolbar-border: rgba(148, 163, 184, 0.3);
    --toolbar-shadow: 0 20px 40px rgba(15, 23, 42, 0.18);
    display: flex;
    flex-direction: column;
    gap: 10px;
    width: 100%;
    padding: 12px;
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.28), rgba(255, 255, 255, 0)),
      var(--toolbar-surface);
    border-radius: 18px;
    box-shadow: var(--toolbar-shadow);
    border: 1px solid var(--toolbar-border);
    backdrop-filter: blur(18px);
  }

  .toolbar-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .selection-preview {
    margin: 0;
    color: color-mix(in srgb, var(--pdf-reader-text, #0f172a) 92%, white 8%);
    font-size: 12px;
    line-height: 1.45;
    font-weight: 600;
    letter-spacing: 0.01em;
    display: -webkit-box;
    overflow: hidden;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
  }

  .dismiss-btn {
    width: 28px;
    height: 28px;
    border: 1px solid rgba(148, 163, 184, 0.28);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.62);
    color: color-mix(in srgb, var(--pdf-reader-text, #0f172a) 86%, white 14%);
    cursor: pointer;
    font-size: 18px;
    line-height: 1;
    flex-shrink: 0;
    transition:
      transform 0.18s ease,
      background-color 0.18s ease;
  }

  .dismiss-btn:hover {
    transform: translateY(-1px);
    background: rgba(255, 255, 255, 0.85);
  }

  .toolbar-body {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .color-picker {
    display: flex;
    gap: 6px;
    padding: 6px;
    border-radius: 999px;
    background: rgba(148, 163, 184, 0.12);
  }

  .color-btn {
    width: 26px;
    height: 26px;
    border: 2px solid rgba(255, 255, 255, 0.78);
    border-radius: 50%;
    cursor: pointer;
    padding: 0;
    box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
    transition:
      transform 0.15s ease,
      box-shadow 0.15s ease,
      border-color 0.15s ease;
  }

  .color-btn:hover {
    transform: translateY(-1px) scale(1.05);
  }

  .color-btn.selected {
    border-color: rgba(15, 23, 42, 0.76);
    box-shadow:
      inset 0 0 0 1px rgba(15, 23, 42, 0.12),
      0 0 0 3px rgba(148, 163, 184, 0.18);
  }

  .actions {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    flex: 1;
  }

  .action-btn {
    display: inline-flex;
    align-items: center;
    min-height: 34px;
    padding: 7px 12px;
    border: 1px solid transparent;
    border-radius: 999px;
    cursor: pointer;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.01em;
    transition:
      transform 0.18s ease,
      background-color 0.18s ease,
      border-color 0.18s ease,
      box-shadow 0.18s ease;
  }

  .action-btn.save {
    background: color-mix(in srgb, #0f172a 82%, var(--pdf-reader-text, #0f172a) 18%);
    color: #f8fafc;
    box-shadow: 0 10px 18px rgba(15, 23, 42, 0.2);
  }

  .action-btn.save:hover:not(:disabled) {
    transform: translateY(-1px);
    background: color-mix(in srgb, #020617 88%, var(--pdf-reader-text, #0f172a) 12%);
  }

  .action-btn.save:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    box-shadow: none;
  }

  .action-btn.delete {
    background: rgba(255, 255, 255, 0.58);
    border-color: rgba(148, 163, 184, 0.28);
    color: color-mix(in srgb, var(--pdf-reader-text, #0f172a) 86%, white 14%);
  }

  .action-btn.delete:hover:not(:disabled) {
    transform: translateY(-1px);
    background: rgba(255, 255, 255, 0.82);
  }

  .action-btn.note {
    background: color-mix(in srgb, #dbeafe 72%, white 28%);
    border-color: rgba(96, 165, 250, 0.2);
    color: #0c4a6e;
  }

  .action-btn.note:hover:not(:disabled) {
    transform: translateY(-1px);
    background: color-mix(in srgb, #bfdbfe 82%, white 18%);
  }

  .note-editor {
    display: flex;
    flex-direction: column;
    gap: 6px;
    width: 100%;
    padding-top: 4px;
    border-top: 1px solid rgba(148, 163, 184, 0.2);
  }

  .note-editor textarea {
    width: 100%;
    min-width: 0;
    resize: vertical;
    border: 1px solid rgba(148, 163, 184, 0.3);
    border-radius: 12px;
    padding: 10px 12px;
    font-size: 12px;
    font-family: inherit;
    background: rgba(255, 255, 255, 0.82);
    color: var(--pdf-reader-text, #0f172a);
  }

  .error {
    margin: 0;
    width: 100%;
    color: #b91c1c;
    font-size: 12px;
    font-weight: 600;
  }

  @media (max-width: 640px) {
    .highlight-toolbar {
      padding: 10px;
      border-radius: 16px;
    }

    .toolbar-body {
      align-items: stretch;
    }

    .color-picker {
      justify-content: center;
      width: 100%;
    }

    .actions {
      width: 100%;
    }

    .action-btn {
      flex: 1 1 calc(50% - 6px);
      justify-content: center;
    }
  }
</style>
