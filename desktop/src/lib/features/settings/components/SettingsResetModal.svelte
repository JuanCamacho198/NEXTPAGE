<script lang="ts">
  import Modal from '$lib/shared/ui/layout/Modal.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    show: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onClose: () => void;
    onConfirm: () => void;
  };

  let { show, t, onClose, onConfirm }: Props = $props();

  let open = $derived(show);
  $effect(() => {
    if (!open) onClose();
  });
</script>

<Modal bind:open size="sm" title={t('settings.resetConfirmTitle')}>
  {#snippet children()}
    <p class="text-sm text-(--color-text-muted)">{t('settings.resetConfirmMessage')}</p>
  {/snippet}
  {#snippet footer()}
    <button
      type="button"
      onclick={() => (open = false)}
      class="rounded-lg border border-(--color-border) px-4 py-1.5 text-sm bg-transparent text-(--color-primary) cursor-pointer hover:opacity-80"
    >
      {t('settings.cancel')}
    </button>
    <button
      type="button"
      onclick={onConfirm}
      class="rounded-lg border border-red-500 px-4 py-1.5 text-sm bg-red-500 text-white cursor-pointer hover:opacity-90"
    >
      {t('settings.reset')}
    </button>
  {/snippet}
</Modal>
