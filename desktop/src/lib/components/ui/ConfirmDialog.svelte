<script lang="ts">
  import Button from "./Button.svelte";

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

  function handleConfirm() {
    open = false;
    onconfirm?.();
  }

  function handleCancel() {
    open = false;
    oncancel?.();
  }

  function handleBackdropClick(e: MouseEvent) {
    if (e.target === e.currentTarget) {
      handleCancel();
    }
  }
</script>

{#if open}
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    onclick={handleBackdropClick}
  >
    <div
      class="w-full max-w-md rounded-lg bg-[var(--color-surface)] p-6 shadow-xl"
      role="dialog"
      aria-modal="true"
    >
      <h3 class="mb-2 text-lg font-semibold text-[var(--color-primary)]">
        {title}
      </h3>
      <p class="mb-6 text-sm text-[var(--color-text-muted)]">{message}</p>
      <div class="flex justify-end gap-3">
        <Button variant="secondary" onclick={handleCancel}>
          {cancelText}
        </Button>
        <Button variant="danger" onclick={handleConfirm}>
          {confirmText}
        </Button>
      </div>
    </div>
  </div>
{/if}