<script lang="ts">
  import Icon from "$lib/components/ui/navigation/Icon.svelte";

  export type SettingsTab = "general" | "appearance" | "data" | "about";

  let {
    activeTab = $bindable<SettingsTab>("general"),
    onTabChange,
    t,
  } = $props<{
    activeTab: SettingsTab;
    onTabChange?: (tab: SettingsTab) => void;
    t: (key: string, params?: Record<string, string | number>) => string;
  }>();

  const tabs: { id: SettingsTab; icon: IconName; labelKey: string }[] = [
    { id: "general", icon: "user", labelKey: "settings.tab.general" },
    { id: "appearance", icon: "sun", labelKey: "settings.tab.appearance" },
    { id: "data", icon: "database", labelKey: "settings.tab.data" },
    { id: "about", icon: "info", labelKey: "settings.tab.about" },
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

  function handleTabClick(tab: SettingsTab) {
    activeTab = tab;
    onTabChange?.(tab);
  }
</script>

<nav class="settings-sidebar" aria-label={t("settings.title")}>
  <div class="sidebar-tabs">
    {#each tabs as tab (tab.id)}
      <button
        type="button"
        class="sidebar-tab"
        class:active={activeTab === tab.id}
        onclick={() => handleTabClick(tab.id)}
        aria-current={activeTab === tab.id ? "page" : undefined}
      >
        <Icon name={tab.icon} size="md" />
        <span class="tab-label">{t(tab.labelKey)}</span>
      </button>
    {/each}
  </div>
</nav>

<style>
  .settings-sidebar {
    width: 180px;
    flex-shrink: 0;
    border-right: 1px solid var(--color-border);
    background: var(--color-surface);
  }

  .sidebar-tabs {
    display: flex;
    flex-direction: column;
    padding: 8px;
    gap: 4px;
  }

  .sidebar-tab {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border: none;
    border-radius: 8px;
    background: transparent;
    cursor: pointer;
    font-size: 14px;
    color: var(--color-text-muted, var(--color-secondary));
    transition: all 0.2s ease;
    text-align: left;
    width: 100%;
  }

  .sidebar-tab:hover {
    background: var(--color-background);
    color: var(--color-text);
  }

  .sidebar-tab.active {
    background: rgba(78, 140, 255, 0.1);
    color: var(--color-primary);
    font-weight: 500;
  }

  .tab-label {
    flex: 1;
  }

  @media (max-width: 767px) {
    .settings-sidebar {
      display: none;
    }
  }
</style>