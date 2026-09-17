<script lang="ts" module>
  import type { MessageKey } from '$lib/shared/i18n';

  export type CapabilityTranslate = (
    key: MessageKey,
    params?: Record<string, string | number>,
  ) => string;

  /**
   * Display label for a declared capability id. The only known id (`resolve`)
   * is localized; unknown ids (the open set) render raw (Android parity).
   * Shared by the badges and the detail dialog so both label identically.
   */
  export function capabilityLabel(capability: string, t: CapabilityTranslate): string {
    return capability === 'resolve' ? t('addons.capabilities.resolve') : capability;
  }
</script>

<script lang="ts">
  type Props = {
    t: CapabilityTranslate;
    capabilities: readonly string[];
    onClose: () => void;
  };

  let { t, capabilities, onClose }: Props = $props();
</script>

<!--
  AddonCapabilityDetail — capability detail dialog (slice 9): lists the
  addon's declared capabilities with the same known-id/raw labeling as the
  badges. Purely presentational: every value arrives via props and closing
  leaves through onClose.
-->
<div role="dialog" aria-label={t('addons.capabilities.title')} class="flex flex-col gap-1 text-sm">
  <p class="m-0 font-medium">{t('addons.capabilities.title')}</p>
  <p class="m-0 opacity-70">{t('addons.capabilities.detail')}</p>
  <ul class="m-0 flex list-none flex-col gap-0.5 p-0">
    {#each capabilities as capability (capability)}
      <li class="text-sm">{capabilityLabel(capability, t)}</li>
    {/each}
  </ul>
  <div class="flex gap-2">
    <button type="button" class="text-sm" onclick={() => onClose()}>
      {t('discover.dismiss')}
    </button>
  </div>
</div>
