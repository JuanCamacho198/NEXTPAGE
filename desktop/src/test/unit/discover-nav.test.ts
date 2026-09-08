import { describe, expect, it } from 'vitest';
import { NavigationDomainState } from '$lib/shared/stores/NavigationDomainState.svelte';
import { getNavItems } from '$lib/shared/stores/NavigationState.svelte';

describe('discover navigation', () => {
  it('exposes navigateToDiscover setting route to discover', () => {
    const nav = new NavigationDomainState();
    nav.shelfDetailsBookId = 'book-1';
    nav.navigateToDiscover();
    expect(nav.route).toBe('discover');
    expect(nav.shelfDetailsBookId).toBeNull();
  });

  it('includes discover entry in nav items', () => {
    const noop = () => {};
    const items = getNavItems({
      onNavigateHome: noop,
      onNavigateLibrary: noop,
      onNavigateStats: noop,
      onNavigateHighlights: noop,
      onNavigateSettings: noop,
      onNavigateDiscover: noop,
    });
    const discover = items.find((item) => item.id === 'discover');
    expect(discover).toBeDefined();
    expect(discover?.messageKey).toBe('sidebar.discover');
  });
});
