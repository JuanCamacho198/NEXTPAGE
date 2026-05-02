<script lang="ts">
  import { errorState } from "$lib/shared/stores/errorState";
  import Button from "../forms/Button.svelte";

  let visible = $state(false);
  let message = $state("");
  let timeoutId: ReturnType<typeof setTimeout> | null = null;

  $effect(() => {
    if ($errorState.showToast && $errorState.currentError) {
      message = $errorState.currentError.message;
      visible = true;

      if (timeoutId) {
        clearTimeout(timeoutId);
      }

      timeoutId = setTimeout(() => {
        errorState.dismissToast();
        visible = false;
      }, 5000);
    } else {
      visible = false;
    }
  });

  const handleDismiss = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    errorState.dismissToast();
    visible = false;
  };

  const handleRetry = () => {
    handleDismiss();
    window.location.reload();
  };
</script>

{#if visible}
  <div
    class="fixed bottom-4 right-4 z-50 max-w-sm rounded-lg border border-amber-300 bg-amber-50 p-4 shadow-lg"
    role="alert"
  >
    <div class="flex items-start gap-3">
      <div class="flex-1">
        <p class="text-sm font-medium text-amber-800">Warning</p>
        <p class="mt-1 text-sm text-amber-700">{message}</p>
      </div>
      <button
        class="text-amber-600 hover:text-amber-800"
        onclick={handleDismiss}
        aria-label="Dismiss"
      >
        ×
      </button>
    </div>
    <div class="mt-3 flex gap-2">
      <Button size="sm" variant="secondary" onclick={handleRetry}>
        Retry
      </Button>
      <Button size="sm" variant="ghost" onclick={handleDismiss}>
        Dismiss
      </Button>
    </div>
  </div>
{/if}