<script lang="ts">
  import { errorState } from "$lib/stores/errorState";
  import Button from "./Button.svelte";

  const handleReload = () => {
    window.location.reload();
  };

  const handleGoHome = () => {
    errorState.clearError();
  };
</script>

{#if $errorState.showFallback && $errorState.currentError}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
    role="alert"
  >
    <div class="mx-4 max-w-md rounded-xl border border-red-200 bg-white p-6 shadow-2xl">
      <div class="text-center">
        <div class="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-full bg-red-100">
          <svg
            class="h-6 w-6 text-red-600"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>

        <h2 class="mb-2 text-lg font-semibold text-gray-900">
          Something went wrong
        </h2>

        <p class="mb-6 text-sm text-gray-600">
          {$errorState.currentError.message}
        </p>

        <div class="flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Button onclick={handleReload}>
            Reload App
          </Button>
          <Button variant="secondary" onclick={handleGoHome}>
            Try Again Later
          </Button>
        </div>
      </div>
    </div>
  </div>
{/if}