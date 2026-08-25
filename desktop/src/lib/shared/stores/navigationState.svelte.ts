import type { AppRoute } from '$lib/shared/stores/homeState';
import type { MessageKey } from '$lib/shared/i18n';
import type { IconName } from '$lib/shared/ui/navigation/Icon.svelte';

export type NavItem = {
  id: AppRoute;
  messageKey: MessageKey;
  icon: IconName;
  action: () => void;
};

export type NavCallbacks = {
  onNavigateHome: () => void;
  onNavigateLibrary: () => void;
  onNavigateStats: () => void;
  onNavigateHighlights: () => void;
  onNavigateSettings: () => void;
  onNavigateDictionary?: () => void;
  onNavigateStorage?: () => void;
  onNavigateSync?: () => void;
};

export function getNavItems(callbacks: NavCallbacks): NavItem[] {
  return [
    { id: 'home', messageKey: 'sidebar.home', icon: 'home', action: callbacks.onNavigateHome },
    { id: 'library', messageKey: 'sidebar.library', icon: 'library', action: callbacks.onNavigateLibrary },
    { id: 'stats', messageKey: 'sidebar.stats', icon: 'stats', action: callbacks.onNavigateStats },
    { id: 'highlights', messageKey: 'sidebar.highlights', icon: 'highlights', action: callbacks.onNavigateHighlights },
    { id: 'settings', messageKey: 'sidebar.settings', icon: 'settings', action: callbacks.onNavigateSettings },
  ];
}

export function getDataNavItems(callbacks: NavCallbacks): NavItem[] {
  const items: NavItem[] = [];
  if (callbacks.onNavigateDictionary) {
    items.push({ id: 'dictionary', messageKey: 'sidebar.dictionary', icon: 'book', action: callbacks.onNavigateDictionary });
  }
  return items;
}
