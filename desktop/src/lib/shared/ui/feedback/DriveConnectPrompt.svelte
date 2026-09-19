<script lang="ts">
  import Button from '../forms/Button.svelte';
  import Modal from '../layout/Modal.svelte';
  import {
    clearDrivePrompt,
    declineDrivePrompt,
    downloadableCatalog,
  } from '$lib/stores/downloadableCatalog.svelte';
  import { beginDriveConnect } from '$lib/shared/services/DriveConnectService';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { t }: Props = $props();

  // Mirror the one-time pre-prompt request into a bindable dialog flag.
  // The dialog opens when a cloud download raises the request; any close
  // path (X, backdrop, Escape) clears the request without persisting a
  // decline — only "Not now" records the per-user decline marker.
  let dialogOpen = $state(false);

  $effect(() => {
    dialogOpen = downloadableCatalog.drivePromptPending;
  });

  $effect(() => {
    if (!dialogOpen) clearDrivePrompt();
  });

  async function handleConnect(): Promise<void> {
    clearDrivePrompt();
    await beginDriveConnect();
  }

  function handleDecline(): void {
    declineDrivePrompt();
  }
</script>

<Modal bind:open={dialogOpen} size="sm" title={t('drive.prompt.title')}>
  {#snippet children()}
    <p class="text-sm text-(--color-text-muted)">{t('drive.prompt.body')}</p>
  {/snippet}
  {#snippet footer()}
    <Button variant="secondary" onclick={handleDecline}>
      {t('drive.prompt.notNow')}
    </Button>
    <Button variant="primary" onclick={() => void handleConnect()}>
      {t('drive.prompt.connect')}
    </Button>
  {/snippet}
</Modal>
