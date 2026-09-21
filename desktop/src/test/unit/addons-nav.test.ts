import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import LayoutGrid from 'lucide-svelte/icons/layout-grid';
import { NavigationDomainState } from '$lib/shared/stores/NavigationDomainState.svelte';
import { getNavItems, type NavCallbacks } from '$lib/shared/stores/NavigationState.svelte';

const here = dirname(fileURLToPath(import.meta.url));
const readSource = (rel: string): string => readFileSync(resolve(here, rel), 'utf8');

function baseCallbacks(overrides: Partial<NavCallbacks> = {}): NavCallbacks {
  const noop = (): void => {};
  return {
    onNavigateHome: noop,
    onNavigateLibrary: noop,
    onNavigateStats: noop,
    onNavigateHighlights: noop,
    onNavigateSettings: noop,
    ...overrides,
  };
}

describe('addons navigation', () => {
  it('exposes navigateToAddons setting route to addons', () => {
    const nav = new NavigationDomainState();
    nav.shelfDetailsBookId = 'book-1';
    nav.navigateToAddons();
    expect(nav.route).toBe('addons');
    expect(nav.shelfDetailsBookId).toBeNull();
  });

  it('includes the addons entry in nav items', () => {
    const items = getNavItems(baseCallbacks({ onNavigateAddons: () => {} }));
    const addons = items.find((item) => item.id === 'addons');
    expect(addons).toBeDefined();
    expect(addons?.messageKey).toBe('sidebar.addons');
    // `NavItem.icon` is a component reference, not an `IconName` string: the
    // icon shim is deleted in this batch, so the only honest assertion is the
    // resolved component identity.
    expect(addons?.icon).toBe(LayoutGrid);
  });

  it('activates the addons route through the sidebar action', () => {
    const nav = new NavigationDomainState();
    const items = getNavItems(baseCallbacks({ onNavigateAddons: () => nav.navigateToAddons() }));
    items.find((item) => item.id === 'addons')?.action();
    expect(nav.route).toBe('addons');
  });

  it('omits the addons entry when no addons callback is registered', () => {
    const items = getNavItems(baseCallbacks());
    expect(items.find((item) => item.id === 'addons')).toBeUndefined();
    // Existing routes render exactly as before: same ids, same order.
    expect(items.map((item) => item.id)).toEqual([
      'home',
      'library',
      'stats',
      'highlights',
      'settings',
    ]);
  });

  it('router mounts AddonsScreen on the registered addons branch', () => {
    const router = readSource('../../lib/shared/ui/layout/AppRouter.svelte');
    expect(router).toContain("navigationState.route === 'addons'");
    expect(router).toContain('AddonsScreen');
    expect(router).toContain('onNavigateAddons');
    expect(router).toContain('navigateToAddons');
    // Existing branches untouched.
    for (const branch of [
      "navigationState.route === 'home'",
      "navigationState.route === 'library'",
      "navigationState.route === 'discover'",
      "navigationState.route === 'stats'",
      "navigationState.route === 'highlights'",
      "navigationState.route === 'dictionary'",
      "navigationState.route === 'storage'",
      "navigationState.route === 'sync'",
      "navigationState.route === 'settings'",
      "navigationState.route === 'welcome'",
    ]) {
      expect(router).toContain(branch);
    }
    expect(router).toContain('DiscoverScreen');
  });

  it('only registered route literals reach the addons screen (no deep-link map)', () => {
    const homeState = readSource('../../lib/shared/stores/HomeState.ts');
    expect(homeState).toContain("'addons'");
    // The install deep link routes manifest URLs to the install dialog,
    // never to the addons route.
    const deepLink = readSource('../../lib/features/addons/installDeepLink.ts');
    expect(deepLink).not.toContain("'addons'");
    expect(deepLink).not.toContain('navigateToAddons');
    // Exactly one addons branch in the router.
    const router = readSource('../../lib/shared/ui/layout/AppRouter.svelte');
    expect(router.match(/route === 'addons'/g)?.length).toBe(1);
  });
});
