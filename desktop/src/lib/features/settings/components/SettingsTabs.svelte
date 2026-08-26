<script lang="ts">
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { SettingsTab } from '../useSettingsRouter.svelte';

  type Props = {
    activeTab: SettingsTab;
    onTabChange: (tab: SettingsTab) => void;
    onKeydown: (e: KeyboardEvent) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { activeTab, onTabChange, onKeydown, t }: Props = $props();

  type TabMeta = {
    id: SettingsTab;
    icon: string;
    labelKey: MessageKey;
    fallback: string;
  };

  const tabs: TabMeta[] = [
    { id: 'cuenta', icon: 'user', labelKey: 'settings.tab.account', fallback: 'Cuenta' },
    { id: 'apariencia', icon: 'sun', labelKey: 'settings.tab.appearance', fallback: 'Apariencia' },
    { id: 'reader', icon: 'book', labelKey: 'settings.tab.reader', fallback: 'Reader' },
    { id: 'datos', icon: 'database', labelKey: 'settings.tab.data', fallback: 'Datos' },
    { id: 'almacenamiento', icon: 'database', labelKey: 'sidebar.storage', fallback: 'Almacenamiento' },
    { id: 'sincronizacion', icon: 'cloud-sync', labelKey: 'sidebar.sync', fallback: 'Sincronización' },
    { id: 'atajos', icon: 'bookmark', labelKey: 'settings.shortcuts.title', fallback: 'Atajos' },
    { id: 'acerca', icon: 'info', labelKey: 'settings.tab.about', fallback: 'Acerca' },
  ];
</script>

<div
  role="tablist"
  aria-label={t('settings.title')}
  onkeydown={onKeydown}
  tabindex={0}
  class="flex border-b border-(--color-border)"
>
  {#each tabs as tab (tab.id)}
    <button
      type="button"
      role="tab"
      aria-selected={activeTab === tab.id}
      aria-controls="tabpanel-{tab.id}"
      id="tab-{tab.id}"
      tabindex={activeTab === tab.id ? 0 : -1}
      class="flex-1 px-2 py-3 border-none cursor-pointer text-2sm text-(--color-text-muted,var(--color-secondary)) border-b-2 border-transparent hover:text-(--color-primary) transition-all duration-200 flex items-center justify-center gap-1.5"
      class:bg-(--color-accent-soft)={activeTab === tab.id}
      class:text-(--color-accent-start)={activeTab === tab.id}
      class:border-(--color-accent-start)={activeTab === tab.id}
      class:font-semibold={activeTab === tab.id}
      onclick={() => onTabChange(tab.id)}
    >
      <Icon name={tab.icon as never} size="sm" />
      <span>{t(tab.labelKey as MessageKey) || tab.fallback}</span>
    </button>
  {/each}
</div>
