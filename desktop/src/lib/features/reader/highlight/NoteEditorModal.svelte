<script lang="ts">
  import { tick } from 'svelte';
  import { scale } from 'svelte/transition';
  import { cubicOut } from 'svelte/easing';
  import { createFocusTrap } from '$lib/shared/utils/focusTrap';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    open: boolean;
    note: string | null;
    /**
     * Text of the highlighted passage. When provided, it is shown above the
     * textarea as a reference block so the user can see what they're
     * annotating without scrolling back to the book.
     */
    highlightText?: string | null;
    onSave: (note: string | null) => void;
    onClose: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { open, note, highlightText, onSave, onClose, t }: Props = $props();

  let textareaEl = $state<HTMLTextAreaElement | null>(null);
  let draft = $state('');

  // 4 color presets from the Pencil design. The note color is a local-only
  // piece of UI for now; persistence (column + Rust command) is a separate
  // piece of work and is intentionally deferred.
  const NOTE_COLORS = [
    { hex: '#60a5fa', name: 'blue' },
    { hex: '#facc15', name: 'yellow' },
    { hex: '#4ade80', name: 'green' },
    { hex: '#f87171', name: 'red' },
  ] as const;
  let noteColor = $state<string>(NOTE_COLORS[0].hex);

  function handleSave(): void {
    const trimmed = draft.trim();
    onSave(trimmed || null);
    onClose();
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
    }
  }

  $effect(() => {
    if (open) {
      draft = note ?? '';
      noteColor = NOTE_COLORS[0].hex;
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
    onclick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}
  >
    <div
      class="w-full max-w-2xl overflow-hidden rounded-3xl border border-(--color-highlight-menu-border) bg-(--color-note-modal-bg) shadow-2xl backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label={t('highlight.noteModalTitle')}
      tabindex="-1"
      onkeydown={handleKeydown}
      in:scale={{ duration: 160, start: 0.95, easing: cubicOut }}
      out:scale={{ duration: 100, start: 0.97, easing: cubicOut }}
    >
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-(--color-border) px-6 py-4">
        <h3 class="text-lg font-bold text-(--color-text-inverse)">
          {t('highlight.noteModalTitle')}
        </h3>
        <button
          type="button"
          class="flex h-5 w-5 cursor-pointer items-center justify-center text-(--color-text-muted) transition-colors hover:text-(--color-text-inverse) focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent-sky)"
          onclick={onClose}
          aria-label={t('highlight.cancel')}
        >
          <svg
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <!-- Content -->
      <div class="flex flex-col gap-4 p-6">
        <!-- Reference: highlighted text shown so the user can see what they're annotating -->
        {#if highlightText}
          <div
            class="rounded-r-lg border-l-4 border-(--color-accent-blue) bg-(--color-bg-deep)/40 p-3"
          >
            <div class="text-(--text-micro) font-bold tracking-[0.6px] text-(--color-text-muted) uppercase">
              {t('highlight.noteReference')}
            </div>
            <div class="mt-1 text-sm text-(--color-secondary)">
              “{highlightText}”
            </div>
          </div>
        {/if}

        <!-- Note textarea -->
        <textarea
          bind:this={textareaEl}
          bind:value={draft}
          rows="5"
          maxlength="1000"
          class="w-full resize-none rounded-2xl border border-(--color-border) bg-(--color-bg-deep) p-4 text-sm text-(--color-text-inverse) placeholder-(--color-text-auxiliary) focus:outline-none focus:ring-1 focus:ring-(--color-accent-sky)"
          placeholder={t('highlight.notePlaceholder')}
        ></textarea>

        <!-- Note color selector -->
        <div class="flex items-center gap-2">
          <span class="text-xs font-medium text-(--color-text-muted)">
            {t('highlight.noteColor')}:
          </span>
          <div class="flex items-center gap-2">
            {#each NOTE_COLORS as color (color.hex)}
              <button
                type="button"
                class="h-5 w-5 cursor-pointer rounded-full transition-transform hover:scale-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent-sky)"
                class:ring-2={noteColor === color.hex}
                class:ring-white={noteColor === color.hex}
                style="background-color: {color.hex};"
                onclick={() => (noteColor = color.hex)}
                aria-label={color.name}
                title={color.name}
              ></button>
            {/each}
          </div>
        </div>
      </div>

      <!-- Actions -->
      <div
        class="flex items-center justify-end gap-3 border-t border-(--color-border) bg-(--color-bg-deep)/9 px-6 py-4"
      >
        <button
          type="button"
          class="cursor-pointer rounded-full px-5 py-2 text-sm font-medium text-(--color-text-muted) transition-colors hover:text-(--color-text-inverse) focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent-sky)"
          onclick={onClose}
        >
          {t('highlight.cancel')}
        </button>
        <button
          type="button"
          class="cursor-pointer rounded-full bg-(--color-accent-blue) px-6 py-2 text-sm font-bold text-(--color-bg-deep) shadow-(--shadow-glow) transition-all hover:shadow-(--shadow-glow-hover) focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent-sky)"
          onclick={handleSave}
        >
          {t('highlight.save')}
        </button>
      </div>
    </div>
  </div>
{/if}
