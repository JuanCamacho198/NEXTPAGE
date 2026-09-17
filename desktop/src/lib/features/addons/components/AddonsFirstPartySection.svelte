<script lang="ts">
  import Panel from '$lib/shared/ui/layout/Panel.svelte';
  import { FIRST_PARTY_BUILTINS, FIRST_PARTY_CURATED } from '../firstPartySources';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { t }: Props = $props();
</script>

<!--
  AddonsFirstPartySection — read-only, visually distinct first-party list
  (slice 7). No install/uninstall affordance, no registry access: it renders
  the static first-party model only. Consent (slice 8) and capability badges
  (slice 9) do not belong here.
-->
<Panel
  variant="surface"
  title={t('addons.firstParty.title')}
  subtitle={t('addons.firstParty.readOnly')}
>
  <h3 class="m-0 text-sm font-semibold">{t('addons.firstParty.builtinTitle')}</h3>
  <ul class="flex flex-col gap-1">
    {#each FIRST_PARTY_BUILTINS as source (source.sourceId)}
      <li class="flex items-center justify-between gap-2">
        <p class="m-0 truncate text-sm">{source.name}</p>
        <span class="shrink-0 rounded border px-1.5 py-0.5 text-xs opacity-70">
          {t('addons.firstParty.builtinBadge')}
        </span>
      </li>
    {/each}
  </ul>
  <h3 class="m-0 text-sm font-semibold">{t('addons.firstParty.curatedTitle')}</h3>
  <ul class="flex flex-col gap-1">
    {#each FIRST_PARTY_CURATED as source (source.sourceId)}
      <li class="flex items-center justify-between gap-2">
        <p class="m-0 truncate text-sm">{source.name}</p>
      </li>
    {/each}
  </ul>
</Panel>
