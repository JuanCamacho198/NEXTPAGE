<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    type?: "success" | "info" | "error";
    message: string;
    dismissible?: boolean;
    visible?: boolean;
    action?: Snippet;
    onDismiss?: () => void;
    class?: string;
  };

  let {
    type = "info",
    message,
    dismissible = true,
    visible = $bindable(false),
    action,
    onDismiss,
    class: className = ""
  }: Props = $props();

  const variants = {
    success: "border-green-300 bg-green-50 text-green-800",
    info: "border-blue-300 bg-blue-50 text-blue-800",
    error: "border-amber-300 bg-amber-50 text-amber-800"
  };

  const iconPaths = {
    success: "M5 13l4 4L19 7",
    info: "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
    error: "M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
  };

  let timeoutId: ReturnType<typeof setTimeout> | null = null;

  $effect(() => {
    if (visible && !dismissible) {
      return;
    }
    if (visible) {
      if (timeoutId) clearTimeout(timeoutId);
      timeoutId = setTimeout(() => {
        handleDismiss();
      }, 5000);
    }
  });

  function handleDismiss() {
    visible = false;
    timeoutId = null;
    onDismiss?.();
  }
</script>

{#if visible}
  <div
    class="fixed bottom-4 right-4 z-50 max-w-sm rounded-lg border p-4 shadow-lg {variants[type]} {className}"
    role="alert"
  >
    <div class="flex items-start gap-3">
      <svg class="h-5 w-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d={iconPaths[type]} />
      </svg>
      <div class="flex-1">
        <p class="text-sm font-medium">{message}</p>
      </div>
      {#if dismissible}
        <button
          class="transition-colors hover:opacity-70"
          onclick={handleDismiss}
          aria-label="Dismiss"
        >
          ×
        </button>
      {/if}
    </div>
    {#if action}
      <div class="mt-3 flex gap-2">
        {@render action()}
      </div>
    {/if}
  </div>
{/if}