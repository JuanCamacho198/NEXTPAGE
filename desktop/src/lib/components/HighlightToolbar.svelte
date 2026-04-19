<script lang="ts">
  import { saveHighlight } from "../tauriClient";

  type Props = {
    selectedText: string;
    bookId: string;
    pageNumber: number;
    onClose: () => void;
  };

  let { selectedText, bookId, pageNumber, onClose }: Props = $props();

  const colors = [
    { name: "yellow", hex: "#fef08a" },
    { name: "green", hex: "#bbf7d0" },
    { name: "blue", hex: "#bfdbfe" },
    { name: "pink", hex: "#fbcfe8" },
    { name: "orange", hex: "#fed7aa" },
  ];

  let selectedColor = $state(colors[0].hex);
  let isSaving = $state(false);

  async function handleCreateHighlight() {
    if (!selectedText || isSaving) return;

    isSaving = true;
    try {
      await saveHighlight({
        id: crypto.randomUUID(),
        bookId,
        text: selectedText,
        color: selectedColor,
        page: pageNumber,
        rectLeft: 0,
        rectRight: 0,
        rectTop: 0,
        rectBottom: 0,
        cfi: null,
      });
      onClose();
    } catch (err) {
      console.error("Failed to save highlight:", err);
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
</script>

<div class="highlight-toolbar">
  <div class="color-picker">
    {#each colors as color}
      <button
        type="button"
        class="color-btn"
        class:selected={selectedColor === color.hex}
        style="background-color: {color.hex};"
        onclick={() => handleColorSelect(color.hex)}
        title={color.name}
        aria-label="Select {color.name} highlight"
      ></button>
    {/each}
  </div>
  <div class="actions">
    <button
      type="button"
      class="action-btn save"
      onclick={handleCreateHighlight}
      disabled={isSaving}
    >
      {isSaving ? "Saving..." : "Save"}
    </button>
    <button type="button" class="action-btn delete" onclick={handleDelete}>
      Cancel
    </button>
  </div>
</div>

<style>
  .highlight-toolbar {
    display: flex;
    align-items: center;
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
</style>