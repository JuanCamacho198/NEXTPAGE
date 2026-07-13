<script lang="ts">
  import { errorState } from '$lib/shared/stores/errorState';
  import Button from '../forms/Button.svelte';
  import { i18n, type MessageKey } from '$lib/shared/i18n';

  let visible = $state(false);
  let locale = $state(i18n?.DEFAULT_LOCALE ?? 'es');

  $effect(() => {
    if (!i18n?.locale) return;
    const unsub = i18n.locale.subscribe((l) => { locale = l; });
    return () => unsub();
  });

  const t = (key: MessageKey): string => i18n?.t?.(locale, key) ?? key;
  let message = $state('');
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

  const handleDismiss = (): void => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    errorState.dismissToast();
    visible = false;
  };

  const handleRetry = (): void => {
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
        <p class="text-sm font-medium text-amber-800">{t('error.warning')}</p>
        <p class="mt-1 text-sm text-amber-700">{message}</p>
      </div>
      <button
        class="text-amber-600 hover:text-amber-800"
        onclick={handleDismiss}
        aria-label={t('error.dismissAria')}
      >
        ×
      </button>
    </div>
    <div class="mt-3 flex gap-2">
      <Button size="sm" variant="secondary" onclick={handleRetry}>{t('error.retry')}</Button>
      <Button size="sm" variant="ghost" onclick={handleDismiss}>{t('error.dismiss')}</Button>
    </div>
  </div>
{/if}
