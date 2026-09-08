<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';

  type Translate = (key: MessageKey) => string;

  let { t }: { t: Translate } = $props();
</script>

<section aria-labelledby="discover-heading" class="flex flex-col gap-2">
  <h1 id="discover-heading" class="m-0 text-xl font-semibold">{t('sidebar.discover')}</h1>
  <p class="m-0 text-sm text-(--color-text-muted)">{t('discover.routePlaceholder')}</p>
</section>
