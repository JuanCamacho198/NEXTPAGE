<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { InstalledAddonRow } from '$lib/shared/services/addons/AddonRegistry';
  import type { InstallOutcome } from '$lib/features/settings/useSettingsAddons.svelte';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    url: string;
    installed: InstalledAddonRow[];
    isBusy: boolean;
    installOutcome: InstallOutcome;
    /** Slice 8 fills: per-addon consent control. Receives the row + current grant. */
    consentById?: Record<string, boolean>;
    onUrlChange: (value: string) => void;
    onInstall: () => void;
    onToggle: (id: string, enabled: boolean) => void;
    onUninstall: (id: string) => void;
    /** Slice 8 fill point: per-addon consent control. */
    consentControl?: Snippet<[{ addon: InstalledAddonRow; granted: boolean }]>;
    /** Slice 9 fill point: capability badges. */
    capabilityBadges?: Snippet<[InstalledAddonRow]>;
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
    consentControl,
    capabilityBadges,
  }: Props = $props();
</script>

<!--
  AddonsInstalledList — purely presentational addon management (slice 7):
  install-by-URL form, installed rows, enable/disable, uninstall, and the
  inline install error/offline block. Owns no state: every value arrives via
  props and every mutation leaves through an on* callback. The error/offline
  block never unmounts either list. consentControl / capabilityBadges are
  render slots that slices 8–9 fill.
-->
<div class="flex flex-col gap-2">
  <label class="text-sm" for="addon-install-url">{t('settings.addons.urlLabel')}</label>
  <div class="flex gap-2">
    <input
      id="addon-install-url"
      type="url"
      class="flex-1 rounded border px-2 py-1"
      placeholder={t('settings.addons.urlPlaceholder')}
      value={url}
      oninput={(e) => onUrlChange((e.currentTarget as HTMLInputElement).value)}
    />
    <button type="button" class="rounded px-3 py-1" disabled={isBusy} onclick={() => onInstall()}>
      {isBusy ? t('settings.addons.installing') : t('settings.addons.install')}
    </button>
  </div>
</div>

{#if installOutcome.kind === 'error' || installOutcome.kind === 'offline'}
  <div
    role="alert"
    class="flex items-center justify-between gap-2 rounded border px-2 py-1 text-sm"
  >
    <p class="m-0">
      {installOutcome.kind === 'offline'
        ? t('addons.install.offline')
        : t('addons.install.errorInline')}
    </p>
    <button type="button" class="shrink-0 text-sm" disabled={isBusy} onclick={() => onInstall()}>
      {t('addons.install.retry')}
    </button>
  </div>
{/if}

{#if installed.length === 0}
  <p class="text-sm opacity-70">{t('settings.addons.empty')}</p>
{:else}
  <ul class="flex flex-col gap-2">
    {#each installed as addon (addon.id)}
      <li class="flex items-center justify-between gap-2">
        <div class="min-w-0">
          <p class="truncate text-sm font-medium">{addon.manifest.name}</p>
          <p class="truncate text-xs opacity-60">{addon.url}</p>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <button type="button" class="text-sm" onclick={() => onToggle(addon.id, !addon.enabled)}>
            {addon.enabled ? t('settings.addons.disable') : t('settings.addons.enable')}
          </button>
          <button type="button" class="text-sm" onclick={() => onUninstall(addon.id)}>
            {t('settings.addons.uninstall')}
          </button>
        </div>
        {@render consentControl?.({ addon, granted: consentById[addon.id] ?? false })}
        {@render capabilityBadges?.(addon)}
      </li>
    {/each}
  </ul>
{/if}
