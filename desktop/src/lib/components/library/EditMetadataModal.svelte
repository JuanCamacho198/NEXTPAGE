<script lang="ts">
  import Button from "../ui/Button.svelte";
  import type { LibraryBookDto } from "../../types";
  import type { MessageKey } from "../../i18n";

  type Props = {
    book: LibraryBookDto;
    open: boolean;
    onClose: () => void;
    onSave: (updatedBook: LibraryBookDto) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { book, open, onClose, onSave, t }: Props = $props();

  let title = $state(book.title);
  let author = $state(book.author || "");
  let isSaving = $state(false);
  let error = $state<string | null>(null);

  $effect(() => {
    if (open) {
      title = book.title;
      author = book.author || "";
      error = null;
    }
  });

  const hasChanges = $derived(title !== book.title || author !== (book.author || ""));

  const handleSave = async () => {
    if (!title.trim()) {
      error = t("library.editMetadata.titleRequired");
      return;
    }

    isSaving = true;
    error = null;

    try {
      await onSave({
        ...book,
        title: title.trim(),
        author: author.trim(),
      });
    } catch (e) {
      error = e instanceof Error ? e.message : t("errors.commandFailure");
    } finally {
      isSaving = false;
    }
  };
</script>

{#if open}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    role="dialog"
    aria-modal="true"
    aria-labelledby="edit-metadata-title"
    onclick={(e) => {
      if (e.target === e.currentTarget) {
        onClose();
      }
    }}
  >
    <div class="w-full max-w-md rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-6 shadow-lg">
      <h2 id="edit-metadata-title" class="mb-4 text-lg font-semibold text-[var(--color-primary)]">
        {t("library.editMetadata.title")}
      </h2>

      <div class="space-y-4">
        <div>
          <label for="edit-title" class="mb-1 block text-sm font-medium text-[var(--color-primary)]">
            {t("library.editMetadata.titleLabel")}
          </label>
          <input
            id="edit-title"
            type="text"
            bind:value={title}
            class="w-full rounded-md border border-[color:var(--color-border)] bg-[var(--color-background)] px-3 py-2 text-sm text-[var(--color-primary)] focus:border-[var(--color-primary)] focus:outline-none"
          />
        </div>

        <div>
          <label for="edit-author" class="mb-1 block text-sm font-medium text-[var(--color-primary)]">
            {t("library.editMetadata.authorLabel")}
          </label>
          <input
            id="edit-author"
            type="text"
            bind:value={author}
            class="w-full rounded-md border border-[color:var(--color-border)] bg-[var(--color-background)] px-3 py-2 text-sm text-[var(--color-primary)] focus:border-[var(--color-primary)] focus:outline-none"
          />
        </div>

        {#if error}
          <p class="text-sm text-red-600">{error}</p>
        {/if}
      </div>

      <div class="mt-6 flex justify-end gap-2">
        <Button variant="secondary" onclick={onClose} disabled={isSaving}>
          {t("library.editMetadata.cancel")}
        </Button>
        <Button onclick={handleSave} disabled={!hasChanges || isSaving}>
          {isSaving ? t("library.editMetadata.saving") : t("library.editMetadata.save")}
        </Button>
      </div>
    </div>
  </div>
{/if}