<script lang="ts">
  import { errorState } from '$lib/shared/stores/ErrorState';
  import Button from '../forms/Button.svelte';
  import { i18n, type MessageKey } from '$lib/shared/i18n';
  import { recordLastEventId } from '$lib/shared/feedback/feedbackStore';
  import * as Sentry from '@sentry/browser';

  let locale = $state(i18n?.DEFAULT_LOCALE ?? 'es');
  $effect(() => {
    if (!i18n?.locale) return;
    const unsub = i18n.locale.subscribe((l) => {
      locale = l;
    });
    return () => unsub();
  });
  const t = (key: MessageKey): string => i18n?.t?.(locale, key) ?? key;

  /**
   * Spec D3 trigger: when the desktop ErrorFallback shows, persist the
   * crash's eventId so the next-launch prompt knows which crash to attach
   * feedback to. We use Sentry.lastEventId() — by the time the fallback
   * renders, Sentry has already captured the exception and stamped an id.
   *
   * If Sentry is uninitialised (no DSN) the value is undefined; we persist
   * `null` and the dialog will open without an associated eventId.
   */
  $effect(() => {
    if ($errorState.showFallback) {
      let id: string | undefined;
      try {
        id = Sentry.lastEventId();
      } catch {
        id = undefined;
      }
      recordLastEventId(id ?? null);
    }
  });

  function handleOpenFeedback(): void {
    // Fire a custom event so AppModals can pick it up via window listener.
    // We deliberately don't import the dialog directly here to avoid
    // creating a circular dep between AppModals <-> ErrorFallback.
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('np:open-feedback'));
    }
    // Dismiss the fallback so the dialog owns the viewport.
    errorState.clearError();
  }

  const handleReload = (): void => {
    window.location.reload();
  };

  const handleGoHome = (): void => {
    errorState.clearError();
  };
</script>

<svelte:window />

{#if $errorState.showFallback && $errorState.currentError}
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60" role="alert">
    <div class="mx-4 max-w-md rounded-xl border border-red-200 bg-white p-6 shadow-2xl">
      <div class="text-center">
        <div class="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-full bg-red-100">
          <svg class="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>

        <h2 class="mb-2 text-lg font-semibold text-gray-900">{t('error.somethingWrong')}</h2>

        <p class="mb-6 text-sm text-gray-600">
          {$errorState.currentError.message}
        </p>

        <div class="flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Button onclick={handleOpenFeedback}>
            {t('error.sendFeedback')}
          </Button>
          <Button variant="secondary" onclick={handleReload}>{t('error.reload')}</Button>
          <Button variant="ghost" onclick={handleGoHome}>{t('error.tryAgainLater')}</Button>
        </div>
      </div>
    </div>
  </div>
{/if}
