<script lang="ts">
  import { tick } from "svelte";
  import { createFocusTrap } from "$lib/shared/utils/focusTrap";
  import type { MessageKey } from "$lib/shared/i18n";

  type Props = {
    open: boolean;
    note: string | null;
    onSave: (note: string | null) => void;
    onClose: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { open, note, onSave, onClose, t }: Props = $props();

  let textareaEl = $state<HTMLTextAreaElement | null>(null);
  let draft = $state("");

  function handleSave(): void {
    const trimmed = draft.trim();
    onSave(trimmed || null);
    onClose();
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
    }
  }

  $effect(() => {
    if (open) {
      draft = note ?? "";
      void tick().then(() => {
        textareaEl?.focus();
      });
    }
  });

  $effect(() => {
    if (!open || !textareaEl) return;
    const trap = createFocusTrap(textareaEl.parentElement as HTMLElement);
    trap.activate();
    return () => trap.deactivate();
  });
</script>

{#if open}
  <div
    class="fixed inset-0 z-[120] flex items-center justify-center bg-black/50 p-4"
    role="presentation"
    onclick={(e) => { if (e.target === e.currentTarget) onClose(); }}
  >
    <div
      class="w-full max-w-md rounded-2xl border border-(--color-highlight-menu-border) bg-(--color-note-modal-bg) p-5 shadow-2xl backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label={t("highlight.noteModalTitle")}
      tabindex="-1"
      onkeydown={handleKeydown}
    >
      <h3 class="text-base font-semibold text-(--color-text-inverse)">
        {t("highlight.noteModalTitle")}
      </h3>
      <textarea
        bind:this={textareaEl}
        bind:value={draft}
        rows="4"
        maxlength="1000"
        class="mt-3 w-full resize-none rounded-xl border border-(--color-highlight-menu-border) bg-(--color-bg-deep) p-3 text-sm text-(--color-text-inverse) placeholder-(--color-text-auxiliary) focus:outline-none focus:ring-1 focus:ring-(--color-accent-sky)"
        placeholder={t("highlight.notePlaceholder")}
      ></textarea>
      <div class="mt-4 flex justify-end gap-2">
        <button
          type="button"
          class="rounded-full px-4 py-2 text-sm text-(--color-text-auxiliary) hover:text-(--color-text-inverse)"
          onclick={onClose}
        >
          {t("highlight.cancel")}
        </button>
        <button
          type="button"
          class="rounded-full bg-(--color-accent-sky) px-4 py-2 text-sm font-medium text-(--color-bg-deep) hover:bg-(--color-accent-blue)"
          onclick={handleSave}
        >
          {t("highlight.save")}
        </button>
      </div>
    </div>
  </div>
{/if}
