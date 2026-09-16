<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n/messages.en';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let {
    t,
    onRetry,
  }: {
    t: Translate;
    onRetry: () => void;
  } = $props();
</script>

<div
  role="alert"
  class="flex flex-col items-start gap-2 rounded-lg border border-(--color-border) bg-(--color-surface-subtle) p-4"
>
  <p class="m-0 text-sm text-(--color-text-muted)">{t('discover.offline')}</p>
  <button
    type="button"
    class="rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
    onclick={onRetry}
  >
    {t('discover.retry')}
  </button>
</div>
