<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n/messages.en';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let { t }: { t: Translate } = $props();
</script>

<!--
  Addons route shell (slice 6). The screen content — installed list,
  first-party section, consent toggle, read sheet — lands in slices 7–9.
-->
<section aria-labelledby="addons-heading" class="flex h-auto flex-col gap-6">
  <h2 id="addons-heading" class="m-0 text-lg font-semibold text-(--color-primary)">
    {t('sidebar.addons')}
  </h2>
</section>
