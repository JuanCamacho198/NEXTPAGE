<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n/messages.en';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  /**
   * Inline per-rail failure: a stable message plus a retry that re-resolves only
   * its own rail. Copy is reused from the screen-level catalog error keys.
   */
  let {
    offline,
    t,
    onRetry,
  }: {
    offline: boolean;
    t: Translate;
    onRetry: () => void;
  } = $props();
</script>

<div class="flex flex-col items-start gap-2">
  <p class="m-0 text-sm text-(--color-text-muted)">
    {offline ? t('discover.offline') : t('discover.errorUpstream')}
  </p>
  <button
    type="button"
    class="rounded-md border border-(--color-primary)/25 bg-(--color-primary)/8 px-3 py-1.5 text-sm font-medium text-(--color-primary) transition-colors hover:bg-(--color-primary)/15"
    onclick={onRetry}
  >
    {t('discover.retry')}
  </button>
</div>
