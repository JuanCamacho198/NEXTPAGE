<script lang="ts">
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';

  export type SettingsTab = 'general' | 'appearance' | 'data' | 'about';

  let {
    activeTab = $bindable<SettingsTab>('general'),
    onTabChange,
    t,
  } = $props<{
    activeTab: SettingsTab;
    onTabChange?: (tab: SettingsTab) => void;
    t: (key: string, params?: Record<string, string | number>) => string;
  }>();

  const tabs: { id: SettingsTab; icon: IconName; labelKey: string }[] = [
    { id: 'general', icon: 'user', labelKey: 'settings.tab.general' },
    { id: 'appearance', icon: 'sun', labelKey: 'settings.tab.appearance' },
    { id: 'data', icon: 'database', labelKey: 'settings.tab.data' },
    { id: 'about', icon: 'info', labelKey: 'settings.tab.about' },
  ];

  type IconName =
    | 'home'
    | 'library'
    | 'stats'
    | 'highlights'
    | 'settings'
    | 'book'
    | 'check'
    | 'clock'
    | 'trend-up'
    | 'chart'
    | 'search'
    | 'close'
    | 'menu'
    | 'sun'
    | 'moon'
    | 'chevron-left'
    | 'chevron-right'
    | 'copy'
    | 'edit'
    | 'trash'
    | 'more-vertical'
    | 'grid'
    | 'list'
    | 'more-dot'
    | 'fullscreen-enter'
    | 'fullscreen-exit'
    | 'arrow-right'
    | 'bookmark'
    | 'note'
    | 'add'
    | 'filter'
    | 'user'
    | 'database'
    | 'info';

  function handleTabClick(tab: SettingsTab): void {
    activeTab = tab;
    onTabChange?.(tab);
  }
</script>

<nav
  class="w-[180px] shrink-0 border-r border-(--color-border) bg-(--color-surface) max-md:hidden"
  aria-label={t('settings.title')}
>
  <div class="flex flex-col gap-1 p-2">
    {#each tabs as tab (tab.id)}
      <button
        type="button"
        class="flex items-center gap-3 px-4 py-3 border-none rounded-lg bg-transparent cursor-pointer text-sm w-full text-left text-(--color-text-muted,var(--color-secondary)) transition-all duration-200 hover:bg-(--color-background) hover:text-(--color-primary)"
        class:bg-[rgba(78,140,255,0.1)]={activeTab === tab.id}
        class:text-(--color-primary)={activeTab === tab.id}
        class:font-medium={activeTab === tab.id}
        onclick={() => handleTabClick(tab.id)}
        aria-current={activeTab === tab.id ? 'page' : undefined}
      >
        <Icon name={tab.icon} size="md" />
        <span class="flex-1">{t(tab.labelKey)}</span>
      </button>
    {/each}
  </div>
</nav>
