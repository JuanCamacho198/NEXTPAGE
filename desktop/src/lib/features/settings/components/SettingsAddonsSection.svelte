<script lang="ts">
  import Panel from '$lib/shared/ui/layout/Panel.svelte';
  import AddonsInstalledList from '$lib/features/addons/components/AddonsInstalledList.svelte';
  import type { InstallOutcome } from '../useSettingsAddons.svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { InstalledAddonRow } from '$lib/shared/services/addons/AddonRegistry';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    url: string;
    installed: InstalledAddonRow[];
    isBusy: boolean;
    installOutcome: InstallOutcome;
    consentById?: Record<string, boolean>;
    onUrlChange: (value: string) => void;
    onInstall: () => void;
    onToggle: (id: string, enabled: boolean) => void;
    onUninstall: (id: string) => void;
  };

  let {
    t,
    url,
    installed,
    isBusy,
    installOutcome,
    consentById = {},
    onUrlChange,
    onInstall,
    onToggle,
    onUninstall,
  }: Props = $props();
</script>

<Panel title={t('settings.addons.title')} subtitle={t('settings.addons.description')}>
  <AddonsInstalledList
    {t}
    {url}
    {installed}
    {isBusy}
    {installOutcome}
    {consentById}
    {onUrlChange}
    {onInstall}
    {onToggle}
    {onUninstall}
  />
</Panel>
