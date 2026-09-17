<script lang="ts">
  import { addonsState, bindAddonsNotifier } from './addonsStore.svelte';
  import AddonsInstalledList from './components/AddonsInstalledList.svelte';
  import AddonsFirstPartySection from './components/AddonsFirstPartySection.svelte';
  import type { MessageKey } from '$lib/shared/i18n/messages.en';

  type Translate = (key: MessageKey, params?: Record<string, string | number>) => string;

  let { t }: { t: Translate } = $props();

  // Same shared state as the Settings panel: managing an addon from either
  // surface updates the other. The deep-link confirm dialog stays reachable
  // here because it is shell-global (AppModals), not route-gated.
  $effect(() => {
    bindAddonsNotifier({ t });
    void addonsState.refresh();
  });
</script>

<!--
  Addons screen content (slice 7): the shared installed list, the read-only
  first-party section, and the inline install error/offline block (rendered by
  AddonsInstalledList without unmounting either list). Consent toggle
  (slice 8) and read sheet (slice 9) fill the list slots.
-->
<section aria-labelledby="addons-heading" class="flex h-auto flex-col gap-6">
  <div class="flex flex-col gap-1">
    <h2 id="addons-heading" class="m-0 text-lg font-semibold text-(--color-primary)">
      {t('addons.title')}
    </h2>
    <p class="m-0 text-sm opacity-70">{t('addons.subtitle')}</p>
  </div>

  <AddonsInstalledList
    {t}
    url={addonsState.url}
    installed={addonsState.installed}
    isBusy={addonsState.isBusy}
    installOutcome={addonsState.installOutcome}
    onUrlChange={(v: string) => (addonsState.url = v)}
    onInstall={() => void addonsState.handleInstall()}
    onToggle={(id: string, enabled: boolean) => void addonsState.handleToggle(id, enabled)}
    onUninstall={(id: string) => void addonsState.handleUninstall(id)}
  />

  <AddonsFirstPartySection {t} />
</section>
