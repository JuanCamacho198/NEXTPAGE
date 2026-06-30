<script lang="ts">
  import { fly } from 'svelte/transition';
  import { cubicOut } from 'svelte/easing';
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { bulkImportState } from '$lib/shared/stores/BulkImportDomainState.svelte';
  import Progress from '$lib/shared/ui/feedback/Progress.svelte';
  import type { ImportNotice } from '$lib/shared/stores/BulkImportDomainState.svelte';

  // ─── Derived state ───
  // The notice is owned by bulkImportState. The banner is a pure
  // presentation layer over it.
  const notice = $derived<ImportNotice | null>(bulkImportState.importNotice);

  // ─── i18n ───
  const t = $derived(appState.t);

  const titleText = $derived.by(() => {
    if (!notice) return '';
    if (notice.status === 'importing') {
      return t('import.bannerImporting', { name: notice.fileName });
    }
    if (notice.status === 'success') {
      return t('import.bannerSuccess', { name: notice.fileName });
    }
    return t('import.bannerErrorTitle', { name: notice.fileName });
  });

  // The service-level progress message (e.g. "Leyendo archivo...") is only
  // meaningful during the importing state. After completion we let the
  // titleText carry the user-facing copy.
  const subtitleText = $derived(
    notice?.status === 'importing' && notice.message ? notice.message : null,
  );

  // During importing we surface the raw service message as a subtitle so
  // the user sees each phase ("Leyendo archivo..." → "Importando a la
  // biblioteca..."). On success/error we keep the banner single-line.

  // ─── Style maps ───
  // Container chrome per state. Uses Tailwind palette for semantic colors
  // (matches Toast.svelte / BulkImportModal error banner).
  const containerClass = $derived.by(() => {
    if (!notice) return '';
    switch (notice.status) {
      case 'importing':
        return 'border-blue-300 bg-blue-50 text-blue-900';
      case 'success':
        return 'border-green-300 bg-green-50 text-green-900';
      case 'error':
        return 'border-red-300 bg-red-50 text-red-900';
    }
  });

  const iconClass = $derived.by(() => {
    if (!notice) return '';
    switch (notice.status) {
      case 'importing':
        return 'text-blue-600';
      case 'success':
        return 'text-green-600';
      case 'error':
        return 'text-red-600';
    }
  });

  // Progress bar tint. The shared <Progress> uses --color-primary for
  // the bar fill; we override the fill + track colors per state so the
  // bar matches the container chrome.
  const progressBarClass = $derived.by(() => {
    if (!notice) return '';
    switch (notice.status) {
      case 'importing':
        return '[&>div>div]:bg-blue-600 [&>div]:bg-blue-200';
      case 'success':
        return '[&>div>div]:bg-green-600 [&>div]:bg-green-200';
      case 'error':
        return '[&>div>div]:bg-red-600 [&>div]:bg-red-200';
    }
  });

  function handleDismiss(): void {
    bulkImportState.dismissImportNotice();
  }
</script>

{#if notice}
  <div
    data-testid="import-progress-banner"
    data-status={notice.status}
    role={notice.status === 'error' ? 'alert' : 'status'}
    aria-live={notice.status === 'error' ? 'assertive' : 'polite'}
    class="fixed top-0 left-0 right-0 z-40 border-b shadow-sm {containerClass}"
    transition:fly={{ y: -24, duration: 220, easing: cubicOut, opacity: 0 }}
  >
    <div class="mx-auto flex max-w-5xl items-center gap-3 px-4 py-2.5 lg:px-6">
      <!-- Icon -->
      <div class="shrink-0 {iconClass}" aria-hidden="true">
        {#if notice.status === 'importing'}
          <!-- Spinner -->
          <svg class="size-5 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle
              class="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              stroke-width="4"
            ></circle>
            <path
              class="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"
            ></path>
          </svg>
        {:else if notice.status === 'success'}
          <!-- Check -->
          <svg
            class="size-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            stroke-width="2.5"
          >
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
          </svg>
        {:else}
          <!-- Alert -->
          <svg
            class="size-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            stroke-width="2"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z"
            />
          </svg>
        {/if}
      </div>

      <!-- Text block -->
      <div class="min-w-0 flex-1">
        <p class="m-0 truncate text-sm font-semibold">
          {titleText}
        </p>
        {#if subtitleText}
          <p class="m-0 mt-0.5 truncate text-xs opacity-80">
            {subtitleText}
          </p>
        {/if}
        {#if notice.status === 'error' && notice.message}
          <p class="m-0 mt-0.5 truncate text-xs opacity-80" title={notice.message}>
            {notice.message}
          </p>
        {/if}
      </div>

      <!-- Progress bar (importing only) -->
      {#if notice.status === 'importing'}
        <div class="hidden w-32 shrink-0 sm:block {progressBarClass}">
          <Progress value={notice.percentage} />
        </div>
      {/if}

      <!-- Dismiss button (error always; success also dismissible for early close) -->
      {#if notice.status === 'error' || notice.status === 'success'}
        <button
          type="button"
          class="shrink-0 rounded-md p-1 transition-colors hover:bg-black/5 focus:outline-none focus:ring-2 focus:ring-current/30"
          onclick={handleDismiss}
          aria-label={t('import.bannerDismissAria')}
        >
          <svg
            class="size-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            stroke-width="2.25"
            aria-hidden="true"
          >
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      {/if}
    </div>

    <!-- Mobile progress bar (full width strip below the text row) -->
    {#if notice.status === 'importing'}
      <div class="px-4 pb-2 sm:hidden {progressBarClass}">
        <Progress value={notice.percentage} />
      </div>
    {/if}
  </div>
{/if}
