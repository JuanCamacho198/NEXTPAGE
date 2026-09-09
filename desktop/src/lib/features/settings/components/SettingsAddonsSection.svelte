<script lang="ts">
  import Panel from '$lib/shared/ui/layout/Panel.svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { InstalledAddonRow } from '$lib/shared/services/addons/AddonRegistry';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    url: string;
    installed: InstalledAddonRow[];
    isBusy: boolean;
    onUrlChange: (value: string) => void;
    onInstall: () => void;
    onToggle: (id: string, enabled: boolean) => void;
    onUninstall: (id: string) => void;
  };

  let { t, url, installed, isBusy, onUrlChange, onInstall, onToggle, onUninstall }: Props = $props();
</script>

<Panel title={t('settings.addons.title')} subtitle={t('settings.addons.description')}>
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
      <button
        type="button"
        class="rounded px-3 py-1"
        disabled={isBusy}
        onclick={() => onInstall()}
      >
        {isBusy ? t('settings.addons.installing') : t('settings.addons.install')}
      </button>
    </div>
  </div>

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
        </li>
      {/each}
    </ul>
  {/if}
</Panel>
