<script lang="ts">
  import Button from "../forms/Button.svelte";
  import Modal from "../layout/Modal.svelte";

  type Props = {
    open?: boolean;
    title?: string;
    message?: string;
    confirmText?: string;
    cancelText?: string;
    onconfirm?: () => void;
    oncancel?: () => void;
  };

  let {
    open = $bindable(false),
    title = "Confirm",
    message = "Are you sure?",
    confirmText = "Confirm",
    cancelText = "Cancel",
    onconfirm,
    oncancel,
  }: Props = $props();

  function handleConfirm(): void {
    open = false;
    onconfirm?.();
  }

  function handleCancel(): void {
    open = false;
    oncancel?.();
  }
</script>

<Modal bind:open size="sm" title={title}>
  {#snippet children()}
    <p class="text-sm text-(--color-text-muted)">{message}</p>
  {/snippet}
  {#snippet footer()}
    <Button variant="secondary" onclick={handleCancel}>
      {cancelText}
    </Button>
    <Button variant="danger" onclick={handleConfirm}>
      {confirmText}
    </Button>
  {/snippet}
</Modal>
