<script lang="ts">
  import { Button, Modal } from '$lib/shared/ui';
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    open: boolean;
    onClose: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { open, onClose, t }: Props = $props();

  // 2-step flow: confirm → choose. Reset to step 1 every time the modal opens.
  let step = $state<'confirm' | 'choose'>('confirm');

  $effect(() => {
    if (open) {
      step = 'confirm';
    }
  });

  function handleLocalOnly(): void {
    const book = appState.pendingRemoveBook;
    onClose();
    if (book) void appState.handleHideBook(book);
  }

  function handleLocalAndDrive(): void {
    const book = appState.pendingRemoveBook;
    onClose();
    if (book) void appState.handleRemoveBookFromDrive(book);
  }
</script>

{#if open && appState.pendingRemoveBook}
  {@const pendingBook = appState.pendingRemoveBook}
  <Modal bind:open title={t('shelf.removeConfirmTitle')} size="sm">
    {#snippet children()}
      {#if step === 'confirm'}
        <p class="text-sm text-(--color-primary)">
          {t('shelf.removeConfirmBody', { title: pendingBook.title })}
        </p>
      {:else}
        <div class="space-y-3">
          <button
            type="button"
            class="w-full rounded-lg border border-(--color-border) bg-(--color-surface) p-3 text-left transition-colors hover:bg-(--color-surface-hover)"
            onclick={handleLocalOnly}
          >
            <span class="block text-sm font-medium text-(--color-primary)"
              >{t('shelf.removeLocalOnly')}</span
            >
            <span class="block text-xs text-(--color-text-muted)"
              >{t('shelf.removeLocalOnlySubtitle')}</span
            >
          </button>
          <button
            type="button"
            class="w-full rounded-lg border border-(--color-error)/40 bg-(--color-surface) p-3 text-left transition-colors hover:bg-(--color-surface-hover)"
            onclick={handleLocalAndDrive}
          >
            <span class="block text-sm font-medium text-red-700"
              >{t('shelf.removeLocalAndDrive')}</span
            >
            <span class="block text-xs text-(--color-text-muted)"
              >{t('shelf.removeLocalAndDriveSubtitle')}</span
            >
          </button>
        </div>
      {/if}
    {/snippet}

    {#snippet footer()}
      {#if step === 'confirm'}
        <Button variant="secondary" onclick={onClose}>{t('shelf.removeCancel')}</Button>
        <Button onclick={() => (step = 'choose')}>{t('shelf.removeContinue')}</Button>
      {:else}
        <Button variant="secondary" onclick={onClose}>{t('shelf.removeCancel')}</Button>
      {/if}
    {/snippet}
  </Modal>
{/if}
